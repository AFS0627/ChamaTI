import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_URL } from '../config';
import { TokenResponse, UsuarioSessao } from '../models/api.models';
import { AuthService } from './auth.service';

const USUARIO: UsuarioSessao = { id: 1, nome: 'Admin', email: 'admin@chamati.local', perfil: 'ADMIN' };

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

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'login', children: [] }])],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('começa sem sessão', () => {
    expect(auth.autenticado()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });

  it('guarda a sessão no login', () => {
    const recebidos: UsuarioSessao[] = [];
    auth.login('admin@chamati.local', 'Senha@123').subscribe((u) => recebidos.push(u));

    const req = http.expectOne(`${API_URL}/api/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'admin@chamati.local', senha: 'Senha@123' });
    req.flush(tokens('1'));

    expect(recebidos[0].nome).toBe('Admin');
    expect(auth.autenticado()).toBe(true);
    expect(auth.accessToken()).toBe('acc1');
    expect(auth.usuario()?.perfil).toBe('ADMIN');
  });

  it('renova uma única vez para chamadas simultâneas', () => {
    auth.login('a@a.com', 'x').subscribe();
    http.expectOne(`${API_URL}/api/auth/login`).flush(tokens('1'));

    const novos: string[] = [];
    auth.renovar().subscribe((t) => novos.push(t));
    auth.renovar().subscribe((t) => novos.push(t));

    const req = http.expectOne(`${API_URL}/api/auth/refresh`);
    expect(req.request.body).toEqual({ refreshToken: 'ref1' });
    req.flush(tokens('2'));

    expect(novos).toEqual(['acc2', 'acc2']);
    expect(auth.accessToken()).toBe('acc2');
  });

  it('limpa a sessão no logout mesmo se a API falhar', () => {
    auth.login('a@a.com', 'x').subscribe();
    http.expectOne(`${API_URL}/api/auth/login`).flush(tokens('1'));

    auth.logout().subscribe();
    http.expectOne(`${API_URL}/api/auth/logout`).flush(null, { status: 500, statusText: 'Erro' });

    expect(auth.autenticado()).toBe(false);
    expect(localStorage.getItem('chamati.sessao')).toBeNull();
  });
});
