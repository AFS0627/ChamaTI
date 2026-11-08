import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideApiConfig } from '../http/api-config';
import { apiUrlInterceptor } from '../http/api-url.interceptor';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { CHAVE_SESSAO, aguardar, guardarSessaoDeTeste, semTrava, tokensDeTeste } from './auth.testing';

const BASE = 'http://api.teste';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  function iniciar(): void {
    TestBed.configureTestingModule({
      providers: [
        provideApiConfig({ baseUrl: BASE }),
        provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', children: [] }]),
        semTrava,
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.clear());

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('envia o access token nas chamadas à API', () => {
    guardarSessaoDeTeste('1');
    iniciar();
    client.get('/api/chamados').subscribe();

    const req = http.expectOne(`${BASE}/api/chamados`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer acc1');
    req.flush({});
  });

  it('não envia o token nos endpoints públicos (login, refresh, logout)', () => {
    guardarSessaoDeTeste('1');
    iniciar();
    client.post('/api/auth/login', {}).subscribe();
    client.post('/api/auth/logout', {}).subscribe();

    for (const caminho of ['/api/auth/login', '/api/auth/logout']) {
      const req = http.expectOne(`${BASE}${caminho}`);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    }
  });

  it('não envia o token para endereços fora da API', () => {
    guardarSessaoDeTeste('1');
    iniciar();
    client.get('https://outro.site/dados').subscribe();

    const req = http.expectOne('https://outro.site/dados');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('renova ANTES de expirar, sem passar pelo 401', async () => {
    guardarSessaoDeTeste('1', { accessExpiraEm: Date.now() + 5_000 });
    iniciar();
    const respostas: unknown[] = [];
    client.get('/api/chamados').subscribe((r) => respostas.push(r));

    const refresh = http.expectOne(`${BASE}/api/auth/refresh`);
    expect(refresh.request.body).toEqual({ refreshToken: 'ref1' });
    refresh.flush(tokensDeTeste('2'));
    await aguardar();

    const req = http.expectOne(`${BASE}/api/chamados`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer acc2');
    req.flush({ ok: true });
    expect(respostas).toEqual([{ ok: true }]);
  });

  it('se a renovação antecipada falhar por conexão, ainda tenta com o token atual', async () => {
    guardarSessaoDeTeste('1', { accessExpiraEm: Date.now() + 5_000 });
    iniciar();
    client.get('/api/chamados').subscribe();

    http.expectOne(`${BASE}/api/auth/refresh`).error(new ProgressEvent('error'));
    await aguardar();

    const req = http.expectOne(`${BASE}/api/chamados`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer acc1');
    req.flush({});
    expect(auth.autenticado()).toBe(true);
  });

  it('se a renovação antecipada for recusada, não envia a chamada e encerra a sessão', async () => {
    guardarSessaoDeTeste('1', { accessExpiraEm: Date.now() + 5_000 });
    iniciar();
    const erros: unknown[] = [];
    client.get('/api/chamados').subscribe({ error: (e: unknown) => erros.push(e) });

    http.expectOne(`${BASE}/api/auth/refresh`).flush({ mensagem: 'revogado' }, { status: 401, statusText: 'Unauthorized' });
    await aguardar();

    http.expectNone(`${BASE}/api/chamados`);
    expect(erros.length).toBe(1);
    expect(auth.autenticado()).toBe(false);
  });

  it('ao receber 401, renova uma vez e repete a chamada', async () => {
    guardarSessaoDeTeste('1');
    iniciar();
    const respostas: unknown[] = [];
    client.get('/api/chamados').subscribe((r) => respostas.push(r));

    http.expectOne(`${BASE}/api/chamados`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${BASE}/api/auth/refresh`).flush(tokensDeTeste('2'));
    await aguardar();

    const repetida = http.expectOne(`${BASE}/api/chamados`);
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer acc2');
    repetida.flush({ ok: true });
    expect(respostas).toEqual([{ ok: true }]);
  });

  it('com várias chamadas recebendo 401 ao mesmo tempo, renova uma única vez', async () => {
    guardarSessaoDeTeste('1');
    iniciar();
    const respostas: unknown[] = [];
    client.get('/api/a').subscribe((r) => respostas.push(r));
    client.get('/api/b').subscribe((r) => respostas.push(r));

    http.expectOne(`${BASE}/api/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${BASE}/api/b`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${BASE}/api/auth/refresh`).flush(tokensDeTeste('2'));
    await aguardar();

    http.expectOne(`${BASE}/api/a`).flush('a');
    http.expectOne(`${BASE}/api/b`).flush('b');
    expect(respostas).toEqual(['a', 'b']);
  });

  it('usa o token que outra aba já renovou, sem pedir outra renovação (o refresh token é de uso único)', async () => {
    guardarSessaoDeTeste('1');
    iniciar();
    const respostas: unknown[] = [];
    client.get('/api/chamados').subscribe((r) => respostas.push(r));
    const primeira = http.expectOne(`${BASE}/api/chamados`);
    guardarSessaoDeTeste('9'); // outra aba renovou enquanto a chamada estava em andamento

    primeira.flush({}, { status: 401, statusText: 'Unauthorized' });
    await aguardar();

    http.expectNone(`${BASE}/api/auth/refresh`);
    const repetida = http.expectOne(`${BASE}/api/chamados`);
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer acc9');
    repetida.flush({ ok: true });
  });

  it('se o token recém-renovado também for recusado, encerra a sessão (usuário desativado)', async () => {
    guardarSessaoDeTeste('1');
    iniciar();
    const erros: unknown[] = [];
    client.get('/api/chamados').subscribe({ error: (e: unknown) => erros.push(e) });

    http.expectOne(`${BASE}/api/chamados`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${BASE}/api/auth/refresh`).flush(tokensDeTeste('2'));
    await aguardar();
    http.expectOne(`${BASE}/api/chamados`).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(erros.length).toBe(1);
    expect(auth.autenticado()).toBe(false);
    expect(localStorage.getItem(CHAVE_SESSAO)).toBeNull();
  });

  it('401 de quem não tem token não tenta renovar', () => {
    iniciar();
    const erros: unknown[] = [];
    client.get('/api/chamados').subscribe({ error: (e: unknown) => erros.push(e) });

    http.expectOne(`${BASE}/api/chamados`).flush({}, { status: 401, statusText: 'Unauthorized' });

    http.expectNone(`${BASE}/api/auth/refresh`);
    expect(erros.length).toBe(1);
  });
});
