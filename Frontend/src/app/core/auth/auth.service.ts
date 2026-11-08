import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, defer, finalize, firstValueFrom, from, map, of, shareReplay, tap, timeout } from 'rxjs';
import { ApiError } from '../http/api-error';
import { SILENCIAR_ERROS } from '../http/contexto';
import { TokenResponse, UsuarioSessao } from '../models/api.models';
import { ArmazenamentoSessao } from './armazenamento-sessao';
import { MARGEM_RENOVACAO_MS, Sessao, accessExpirando, criarSessao } from './sessao';
import { TRAVA_RENOVACAO } from './trava-renovacao';

const TEMPO_MAXIMO_RENOVACAO_MS = 15_000;
const TEMPO_MAXIMO_RESTAURACAO_MS = 8_000;

/** O servidor recusou o refresh token (inválido, expirado, revogado ou usuário inativo)? */
function recusadoPeloServidor(erro: unknown): boolean {
  return (
    (erro instanceof ApiError || erro instanceof HttpErrorResponse) &&
    (erro.status === 400 || erro.status === 401 || erro.status === 403)
  );
}

/**
 * Sessão do usuário, conforme a estratégia do backend:
 * - access token JWT de 15 min, enviado em Authorization: Bearer;
 * - refresh token opaco, de uso único: reutilizar um já revogado é tratado como roubo e derruba TODAS as sessões;
 * - o backend confere o usuário no banco a cada chamada (perfil e situação atuais valem na hora).
 *
 * Por causa do segundo ponto, a renovação é serializada (nesta aba e entre abas) e sempre parte da sessão mais
 * recente gravada no localStorage, nunca de uma cópia antiga na memória.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly armazenamento = inject(ArmazenamentoSessao);
  private readonly trava = inject(TRAVA_RENOVACAO);

  private readonly sessao = signal<Sessao | null>(this.armazenamento.ler());
  private renovando$: Observable<string> | null = null;

  readonly usuario = computed(() => this.sessao()?.usuario ?? null);
  readonly autenticado = computed(() => this.sessao() !== null);

  constructor() {
    this.armazenamento.aoMudarEmOutraAba((nova) => this.sincronizar(nova));
  }

  accessToken(): string | null {
    return this.sessao()?.accessToken ?? null;
  }

  /** O access token já expirou ou está para expirar? */
  accessExpirando(margemMs: number = MARGEM_RENOVACAO_MS): boolean {
    const atual = this.sessao();
    return atual !== null && accessExpirando(atual, Date.now(), margemMs);
  }

  login(email: string, senha: string): Observable<UsuarioSessao> {
    return this.http.post<TokenResponse>('/api/auth/login', { email, senha }).pipe(
      tap((r) => this.guardar(r)),
      map((r) => r.usuario),
    );
  }

  /** Dados atuais do usuário (o backend consulta o banco): perfil e situação podem ter mudado. */
  me(): Observable<UsuarioSessao> {
    return this.http.get<UsuarioSessao>('/api/auth/me').pipe(tap((u) => this.atualizarUsuario(u)));
  }

  /**
   * Executada antes da primeira navegação: se há sessão guardada, confere-a no backend.
   * Recusa do servidor encerra a sessão; falha de conexão mantém (o servidor pode estar só indisponível).
   */
  restaurar(): Promise<void> {
    if (this.sessao() === null) {
      return Promise.resolve();
    }
    return firstValueFrom(
      this.me().pipe(
        timeout(TEMPO_MAXIMO_RESTAURACAO_MS),
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  /**
   * Devolve um access token novo. `tokenUsado` é o token que a chamada tinha em mãos: se outra aba já renovou
   * (o armazenamento tem um token diferente e ainda válido), ele é adotado sem ir ao servidor.
   * Chamadas simultâneas compartilham uma única renovação.
   */
  renovar(tokenUsado: string | null): Observable<string> {
    if (!this.renovando$) {
      this.renovando$ = defer(() => from(this.trava(() => this.renovarComTrava(tokenUsado)))).pipe(
        finalize(() => (this.renovando$ = null)),
        shareReplay(1),
      );
    }
    return this.renovando$;
  }

  /** Revoga o refresh token no servidor (idempotente) e limpa a sessão local, mesmo que a chamada falhe. */
  logout(): Observable<void> {
    const atual = this.armazenamento.ler() ?? this.sessao();
    const requisicao: Observable<unknown> = atual
      ? this.http.post(
          '/api/auth/logout',
          { refreshToken: atual.refreshToken },
          { context: new HttpContext().set(SILENCIAR_ERROS, true) },
        )
      : of(null);
    return requisicao.pipe(
      catchError(() => of(null)),
      map(() => undefined),
      finalize(() => this.encerrarSessaoLocal()),
    );
  }

  /** Revoga os refresh tokens de todos os dispositivos e encerra esta sessão. */
  logoutTodos(): Observable<void> {
    return this.http.post('/api/auth/logout-todos', null).pipe(
      tap(() => this.encerrarSessaoLocal()),
      map(() => undefined),
    );
  }

  /**
   * O backend encerra todas as sessões e devolve um novo par de tokens para manter esta aberta;
   * as outras abas adotam o novo par pelo armazenamento.
   */
  alterarSenha(senhaAtual: string, novaSenha: string): Observable<void> {
    return this.http.put<TokenResponse>('/api/auth/senha', { senhaAtual, novaSenha }).pipe(
      tap((r) => this.guardar(r)),
      map(() => undefined),
    );
  }

  /** Descarta a sessão neste navegador e volta para o login, guardando onde o usuário estava. */
  encerrarSessaoLocal(): void {
    this.sessao.set(null);
    this.armazenamento.limpar();
    this.irParaLogin();
  }

  private async renovarComTrava(tokenUsado: string | null): Promise<string> {
    // Fonte da verdade: o armazenamento (outra aba pode ter renovado e a nossa memória estar velha).
    const armazenada = this.armazenamento.ler() ?? this.sessao();
    if (armazenada === null) {
      throw new Error('Sem sessão.');
    }
    if (tokenUsado !== null && armazenada.accessToken !== tokenUsado && !accessExpirando(armazenada, Date.now(), 0)) {
      this.sessao.set(armazenada);
      return armazenada.accessToken;
    }
    try {
      const resposta = await firstValueFrom(
        this.http
          .post<TokenResponse>('/api/auth/refresh', { refreshToken: armazenada.refreshToken })
          .pipe(timeout(TEMPO_MAXIMO_RENOVACAO_MS)),
      );
      return this.guardar(resposta).accessToken;
    } catch (erro) {
      if (!recusadoPeloServidor(erro)) {
        throw erro; // sem conexão, 5xx, demora: a sessão continua, a próxima chamada tenta de novo
      }
      // Recusado: talvez outra aba tenha trocado o par entre a nossa leitura e a chamada (ex.: troca de senha).
      const maisNova = this.armazenamento.ler();
      if (maisNova !== null && maisNova.refreshToken !== armazenada.refreshToken) {
        this.sessao.set(maisNova);
        return maisNova.accessToken;
      }
      this.encerrarSessaoLocal();
      throw erro;
    }
  }

  private guardar(resposta: TokenResponse): Sessao {
    const sessao = criarSessao(resposta, Date.now());
    this.sessao.set(sessao);
    this.armazenamento.gravar(sessao);
    return sessao;
  }

  private atualizarUsuario(usuario: UsuarioSessao): void {
    const atual = this.armazenamento.ler() ?? this.sessao();
    if (atual === null) {
      return;
    }
    const nova: Sessao = { ...atual, usuario };
    this.sessao.set(nova);
    this.armazenamento.gravar(nova);
  }

  /** Outra aba entrou, saiu ou renovou: acompanha. */
  private sincronizar(nova: Sessao | null): void {
    this.sessao.set(nova);
    if (nova === null) {
      this.irParaLogin();
    } else if (this.router.url.startsWith('/login')) {
      void this.router.navigate(['/dashboard']);
    }
  }

  private irParaLogin(): void {
    // Antes da primeira navegação os guards já redirecionam sozinhos (com o endereço pedido).
    if (!this.router.navigated) {
      return;
    }
    const url = this.router.url;
    if (url.startsWith('/login')) {
      return;
    }
    void this.router.navigate(['/login'], { queryParams: url === '/' ? {} : { retorno: url } });
  }
}
