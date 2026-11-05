import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_URL } from '../config';
import { TokenResponse } from '../models/api.models';
import { authInterceptor } from './auth.interceptor';

const USUARIO = { id: 1, nome: 'Admin', email: 'admin@chamati.local', perfil: 'ADMIN' as const };

function tokens(sufixo: string): TokenResponse {
  return {
    tipo: 'Bearer',
    accessToken: `acc${sufixo}`,
    expiraEmSegundos: 900,
    refreshToken: `ref${sufixo}`,
    refreshExpiraEm: '2030-01-01T00:00:00Z',
    usuario: USUARIO,
  };
}

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(
      'chamati.sessao',
      JSON.stringify({ accessToken: 'acc1', refreshToken: 'ref1', usuario: USUARIO }),
    );
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), provideRouter([{ path: 'login', children: [] }])],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('envia o access token nas chamadas à API', () => {
    client.get(`${API_URL}/api/chamados`).subscribe();

    const req = http.expectOne(`${API_URL}/api/chamados`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer acc1');
    req.flush({});
  });

  it('não envia o token no login', () => {
    client.post(`${API_URL}/api/auth/login`, {}).subscribe();

    const req = http.expectOne(`${API_URL}/api/auth/login`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('renova o token ao receber 401 e repete a chamada', () => {
    const respostas: unknown[] = [];
    client.get(`${API_URL}/api/chamados`).subscribe((r) => respostas.push(r));

    http.expectOne(`${API_URL}/api/chamados`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API_URL}/api/auth/refresh`).flush(tokens('2'));

    const repetida = http.expectOne(`${API_URL}/api/chamados`);
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer acc2');
    repetida.flush({ ok: true });

    expect(respostas).toEqual([{ ok: true }]);
  });
});
