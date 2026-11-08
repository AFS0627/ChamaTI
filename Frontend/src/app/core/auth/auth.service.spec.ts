import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsuarioSessao } from '../models/api.models';
import { AuthService } from './auth.service';
import { CHAVE_SESSAO, USUARIO_TESTE, guardarSessaoDeTeste, semTrava, tokensDeTeste } from './auth.testing';
import { Sessao } from './sessao';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  function iniciar(): void {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'login', children: [] }]), semTrava],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  }

  const gravadaNoNavegador = (): Sessao | null => {
    const texto = localStorage.getItem(CHAVE_SESSAO);
    return texto ? (JSON.parse(texto) as Sessao) : null;
  };

  beforeEach(() => localStorage.clear());

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('começa sem sessão', () => {
    iniciar();

    expect(auth.autenticado()).toBe(false);
    expect(auth.accessToken()).toBeNull();
    expect(auth.accessExpirando()).toBe(false);
  });

  it('guarda a sessão e a validade do access token no login', () => {
    iniciar();
    const recebidos: UsuarioSessao[] = [];
    auth.login('admin@chamati.local', 'Senha@123').subscribe((u) => recebidos.push(u));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'admin@chamati.local', senha: 'Senha@123' });
    req.flush(tokensDeTeste('1'));

    expect(recebidos[0].nome).toBe('Admin');
    expect(auth.autenticado()).toBe(true);
    expect(auth.accessToken()).toBe('acc1');
    expect(auth.accessExpirando()).toBe(false);
    expect(typeof gravadaNoNavegador()?.accessExpiraEm).toBe('number');
  });

  it('retoma a sessão guardada e percebe que o access token está vencendo', () => {
    guardarSessaoDeTeste('1', { accessExpiraEm: Date.now() + 5_000 });
    iniciar();

    expect(auth.autenticado()).toBe(true);
    expect(auth.accessExpirando()).toBe(true);
  });

  describe('renovar', () => {
    it('chamadas simultâneas compartilham uma única renovação', async () => {
      guardarSessaoDeTeste('1');
      iniciar();
      const novos: string[] = [];
      const a = firstValueFrom(auth.renovar('acc1')).then((t) => novos.push(t));
      const b = firstValueFrom(auth.renovar('acc1')).then((t) => novos.push(t));

      const req = http.expectOne('/api/auth/refresh');
      expect(req.request.body).toEqual({ refreshToken: 'ref1' });
      req.flush(tokensDeTeste('2'));
      await Promise.all([a, b]);

      expect(novos).toEqual(['acc2', 'acc2']);
      expect(auth.accessToken()).toBe('acc2');
      expect(gravadaNoNavegador()?.refreshToken).toBe('ref2');
    });

    it('adota a sessão que outra aba já renovou, sem ir ao servidor', async () => {
      guardarSessaoDeTeste('1');
      iniciar();
      guardarSessaoDeTeste('9'); // outra aba renovou

      const token = await firstValueFrom(auth.renovar('acc1'));

      expect(token).toBe('acc9');
      expect(auth.accessToken()).toBe('acc9');
      http.expectNone('/api/auth/refresh');
    });

    it('renova com o refresh token mais recente do armazenamento, não o da memória', async () => {
      guardarSessaoDeTeste('1');
      iniciar();
      guardarSessaoDeTeste('2', { accessExpiraEm: Date.now() - 1000 }); // outra aba renovou, mas o novo token já venceu

      const promessa = firstValueFrom(auth.renovar('acc1'));

      const req = http.expectOne('/api/auth/refresh');
      expect(req.request.body).toEqual({ refreshToken: 'ref2' });
      req.flush(tokensDeTeste('3'));
      expect(await promessa).toBe('acc3');
    });

    it('encerra a sessão quando o servidor recusa o refresh token', async () => {
      guardarSessaoDeTeste('1');
      iniciar();
      const resultado = firstValueFrom(auth.renovar('acc1')).then(
        () => 'ok',
        () => 'falhou',
      );

      http.expectOne('/api/auth/refresh').flush({ mensagem: 'inválido' }, { status: 401, statusText: 'Unauthorized' });

      expect(await resultado).toBe('falhou');
      expect(auth.autenticado()).toBe(false);
      expect(gravadaNoNavegador()).toBeNull();
    });

    it('se outra aba trocou o par durante a chamada, adota o novo em vez de encerrar', async () => {
      guardarSessaoDeTeste('1');
      iniciar();
      const promessa = firstValueFrom(auth.renovar('acc1'));

      const req = http.expectOne('/api/auth/refresh');
      guardarSessaoDeTeste('7'); // ex.: outra aba trocou a senha e recebeu um par novo
      req.flush({ mensagem: 'revogado' }, { status: 401, statusText: 'Unauthorized' });

      expect(await promessa).toBe('acc7');
      expect(auth.autenticado()).toBe(true);
    });

    it('mantém a sessão quando a falha é de conexão', async () => {
      guardarSessaoDeTeste('1');
      iniciar();
      const resultado = firstValueFrom(auth.renovar('acc1')).then(
        () => 'ok',
        () => 'falhou',
      );

      http.expectOne('/api/auth/refresh').error(new ProgressEvent('error'));

      expect(await resultado).toBe('falhou');
      expect(auth.autenticado()).toBe(true);
      expect(gravadaNoNavegador()?.refreshToken).toBe('ref1');
    });
  });

  describe('restaurar', () => {
    it('sem sessão, não chama o servidor', async () => {
      iniciar();

      await auth.restaurar();

      http.expectNone('/api/auth/me');
    });

    it('confere o usuário no backend e atualiza o perfil', async () => {
      guardarSessaoDeTeste('1', { usuario: { ...USUARIO_TESTE, perfil: 'SOLICITANTE' } });
      iniciar();
      const pronto = auth.restaurar();

      http.expectOne('/api/auth/me').flush({ ...USUARIO_TESTE, perfil: 'ADMIN' });
      await pronto;

      expect(auth.usuario()?.perfil).toBe('ADMIN');
      expect(gravadaNoNavegador()?.usuario.perfil).toBe('ADMIN');
      expect(gravadaNoNavegador()?.accessToken).toBe('acc1');
    });

    it('com o servidor fora do ar, mantém a sessão e deixa o app abrir', async () => {
      guardarSessaoDeTeste('1');
      iniciar();
      const pronto = auth.restaurar();

      http.expectOne('/api/auth/me').error(new ProgressEvent('error'));
      await pronto;

      expect(auth.autenticado()).toBe(true);
    });
  });

  it('logout revoga o refresh token mais recente e limpa a sessão mesmo se a API falhar', () => {
    guardarSessaoDeTeste('1');
    iniciar();
    auth.logout().subscribe();

    const req = http.expectOne('/api/auth/logout');
    expect(req.request.body).toEqual({ refreshToken: 'ref1' });
    req.flush(null, { status: 500, statusText: 'Erro' });

    expect(auth.autenticado()).toBe(false);
    expect(gravadaNoNavegador()).toBeNull();
  });

  it('logout-todos revoga as sessões e encerra a local', () => {
    guardarSessaoDeTeste('1');
    iniciar();
    auth.logoutTodos().subscribe();

    const req = http.expectOne('/api/auth/logout-todos');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(auth.autenticado()).toBe(false);
  });

  it('troca de senha substitui a sessão pelo novo par de tokens', () => {
    guardarSessaoDeTeste('1');
    iniciar();
    auth.alterarSenha('Antiga@1', 'Nova@1234').subscribe();

    const req = http.expectOne('/api/auth/senha');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ senhaAtual: 'Antiga@1', novaSenha: 'Nova@1234' });
    req.flush(tokensDeTeste('5'));

    expect(auth.accessToken()).toBe('acc5');
    expect(gravadaNoNavegador()?.refreshToken).toBe('ref5');
  });

  describe('várias abas', () => {
    it('acompanha o logout feito em outra aba', () => {
      guardarSessaoDeTeste('1');
      iniciar();

      localStorage.removeItem(CHAVE_SESSAO);
      window.dispatchEvent(new StorageEvent('storage', { key: CHAVE_SESSAO, newValue: null }));

      expect(auth.autenticado()).toBe(false);
    });

    it('acompanha a renovação feita em outra aba', () => {
      guardarSessaoDeTeste('1');
      iniciar();

      const nova = guardarSessaoDeTeste('4');
      window.dispatchEvent(new StorageEvent('storage', { key: CHAVE_SESSAO, newValue: JSON.stringify(nova) }));

      expect(auth.accessToken()).toBe('acc4');
    });

    it('ignora alterações de outras chaves do armazenamento', () => {
      guardarSessaoDeTeste('1');
      iniciar();

      window.dispatchEvent(new StorageEvent('storage', { key: 'outra-coisa', newValue: null }));

      expect(auth.autenticado()).toBe(true);
    });
  });
});

