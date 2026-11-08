import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from '../auth/auth.interceptor';
import { AuthService } from '../auth/auth.service';
import { aguardar, guardarSessaoDeTeste, semTrava } from '../auth/auth.testing';
import { NotificacaoService } from '../notificacoes/notificacao.service';
import { provideApiConfig } from './api-config';
import { ApiError } from './api-error';
import { apiUrlInterceptor } from './api-url.interceptor';
import { SILENCIAR_ERROS } from './contexto';
import { erroInterceptor } from './error.interceptor';

const BASE = 'http://api.teste';
describe('erroInterceptor (cadeia completa)', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let avisos: NotificacaoService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideApiConfig({ baseUrl: BASE }),
        provideHttpClient(withInterceptors([erroInterceptor, apiUrlInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', children: [] }]),
        semTrava,
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    avisos = TestBed.inject(NotificacaoService);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function falhas(observable: ReturnType<HttpClient['get']>): unknown[] {
    const erros: unknown[] = [];
    observable.subscribe({ error: (e: unknown) => erros.push(e) });
    return erros;
  }

  it('avisa quando não há conexão com o servidor', () => {
    const erros = falhas(client.get('/api/chamados'));

    http.expectOne(`${BASE}/api/chamados`).error(new ProgressEvent('error'));

    const erro = erros[0] as ApiError;
    expect(erro instanceof ApiError).toBe(true);
    expect(erro.status).toBe(0);
    expect(erro.notificado).toBe(true);
    expect(avisos.itens().map((n) => n.texto)).toEqual(['Não foi possível conectar ao servidor.']);
    expect(avisos.itens()[0].tipo).toBe('erro');
  });

  it('avisa erros do servidor (5xx)', () => {
    const erros = falhas(client.get('/api/chamados'));

    http.expectOne(`${BASE}/api/chamados`).flush({}, { status: 500, statusText: 'Server Error' });

    expect((erros[0] as ApiError).notificado).toBe(true);
    expect(avisos.itens().map((n) => n.texto)).toEqual(['Erro no servidor. Tente novamente em instantes.']);
  });

  it('não avisa erros de regra: entrega a mensagem da API ao componente', () => {
    const erros = falhas(client.post('/api/chamados', {}));

    http
      .expectOne(`${BASE}/api/chamados`)
      .flush({ mensagem: 'Categoria inativa.' }, { status: 422, statusText: 'Unprocessable Entity' });

    const erro = erros[0] as ApiError;
    expect(erro.status).toBe(422);
    expect(erro.message).toBe('Categoria inativa.');
    expect(erro.notificado).toBe(false);
    expect(avisos.itens().length).toBe(0);
  });

  it('senha incorreta no login não vira aviso de sessão expirada', () => {
    const erros = falhas(client.post('/api/auth/login', {}));

    http
      .expectOne(`${BASE}/api/auth/login`)
      .flush({ mensagem: 'E-mail ou senha inválidos.' }, { status: 401, statusText: 'Unauthorized' });

    const erro = erros[0] as ApiError;
    expect(erro.message).toBe('E-mail ou senha inválidos.');
    expect(erro.notificado).toBe(false);
    expect(avisos.itens().length).toBe(0);
  });

  it('respeita SILENCIAR_ERROS', () => {
    const contexto = new HttpContext().set(SILENCIAR_ERROS, true);
    const erros = falhas(client.get('/api/chamados', { context: contexto }));

    http.expectOne(`${BASE}/api/chamados`).error(new ProgressEvent('error'));

    expect((erros[0] as ApiError).notificado).toBe(false);
    expect(avisos.itens().length).toBe(0);
  });

  it('sessão que não renova: um único aviso, sessão encerrada e erro já notificado', async () => {
    guardarSessaoDeTeste('1');
    const erros = falhas(client.get('/api/chamados'));

    http.expectOne(`${BASE}/api/chamados`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne(`${BASE}/api/auth/refresh`)
      .flush({ mensagem: 'Refresh token inválido.' }, { status: 401, statusText: 'Unauthorized' });

    await aguardar();

    expect(erros.length).toBe(1);
    expect((erros[0] as ApiError).notificado).toBe(true);
    expect(avisos.itens().map((n) => n.texto)).toEqual(['Sua sessão expirou. Entre novamente.']);
    expect(TestBed.inject(AuthService).autenticado()).toBe(false);
  });
});
