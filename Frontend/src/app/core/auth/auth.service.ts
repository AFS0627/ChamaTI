import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';
import { SILENCIAR_ERROS } from '../http/contexto';
import { TokenResponse, UsuarioSessao } from '../models/api.models';

const CHAVE = 'chamati.sessao';

interface Sessao {
  accessToken: string;
  refreshToken: string;
  usuario: UsuarioSessao;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sessao = signal<Sessao | null>(this.carregar());
  private renovando$: Observable<string> | null = null;

  readonly usuario = computed(() => this.sessao()?.usuario ?? null);
  readonly autenticado = computed(() => this.sessao() !== null);

  accessToken(): string | null {
    return this.sessao()?.accessToken ?? null;
  }

  login(email: string, senha: string): Observable<UsuarioSessao> {
    return this.http.post<TokenResponse>('/api/auth/login', { email, senha }).pipe(
      tap((r) => this.guardar(r)),
      map((r) => r.usuario),
    );
  }

  /**
   * Renova o par de tokens. O refresh token é rotativo e de uso único (a reutilização revoga a sessão),
   * por isso chamadas simultâneas compartilham uma única requisição.
   */
  renovar(): Observable<string> {
    const atual = this.sessao();
    if (!atual) {
      return throwError(() => new Error('Sem sessão.'));
    }
    if (!this.renovando$) {
      this.renovando$ = this.http
        .post<TokenResponse>('/api/auth/refresh', { refreshToken: atual.refreshToken })
        .pipe(
          tap((r) => this.guardar(r)),
          map((r) => r.accessToken),
          finalize(() => (this.renovando$ = null)),
          shareReplay(1),
        );
    }
    return this.renovando$;
  }

  logout(): Observable<void> {
    const atual = this.sessao();
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

  /** Descarta a sessão neste navegador e volta para o login. */
  encerrarSessaoLocal(): void {
    this.sessao.set(null);
    this.persistir(null);
    void this.router.navigate(['/login']);
  }

  private guardar(r: TokenResponse): void {
    const sessao: Sessao = { accessToken: r.accessToken, refreshToken: r.refreshToken, usuario: r.usuario };
    this.sessao.set(sessao);
    this.persistir(sessao);
  }

  private carregar(): Sessao | null {
    try {
      const texto = localStorage.getItem(CHAVE);
      return texto ? (JSON.parse(texto) as Sessao) : null;
    } catch {
      return null;
    }
  }

  private persistir(sessao: Sessao | null): void {
    try {
      if (sessao) {
        localStorage.setItem(CHAVE, JSON.stringify(sessao));
      } else {
        localStorage.removeItem(CHAVE);
      }
    } catch {
      // armazenamento indisponível: a sessão vale só enquanto a página estiver aberta
    }
  }
}
