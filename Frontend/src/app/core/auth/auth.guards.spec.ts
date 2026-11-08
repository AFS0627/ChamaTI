import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NotificacaoService } from '../notificacoes/notificacao.service';
import { autenticadoGuard, perfilGuard, visitanteGuard } from './auth.guards';
import { USUARIO_TESTE, guardarSessaoDeTeste, semTrava } from './auth.testing';

describe('guards de autenticação', () => {
  const rota = {} as ActivatedRouteSnapshot;
  const estado = (url: string): RouterStateSnapshot => ({ url }) as RouterStateSnapshot;

  function executar(guard: CanActivateFn, url = '/chamados?status=ABERTO'): boolean | string {
    const resultado = TestBed.runInInjectionContext(() => guard(rota, estado(url)));
    return resultado instanceof UrlTree ? TestBed.inject(Router).serializeUrl(resultado) : (resultado as boolean);
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), semTrava],
    });
  });

  afterEach(() => localStorage.clear());

  it('autenticadoGuard: sem sessão, vai para o login guardando o endereço pedido', () => {
    expect(executar(autenticadoGuard)).toBe('/login?retorno=%2Fchamados%3Fstatus%3DABERTO');
  });

  it('autenticadoGuard: com sessão, libera', () => {
    guardarSessaoDeTeste('1');

    expect(executar(autenticadoGuard)).toBe(true);
  });

  it('visitanteGuard: quem já tem sessão vai para o dashboard', () => {
    guardarSessaoDeTeste('1');

    expect(executar(visitanteGuard, '/login')).toBe('/dashboard');
  });

  it('visitanteGuard: sem sessão, mostra o login', () => {
    expect(executar(visitanteGuard, '/login')).toBe(true);
  });

  it('perfilGuard: libera o perfil permitido', () => {
    guardarSessaoDeTeste('1');

    expect(executar(perfilGuard('ADMIN'), '/admin')).toBe(true);
    expect(executar(perfilGuard('TECNICO', 'ADMIN'), '/admin')).toBe(true);
  });

  it('perfilGuard: nega outro perfil, volta ao dashboard e avisa', () => {
    guardarSessaoDeTeste('1', { usuario: { ...USUARIO_TESTE, perfil: 'SOLICITANTE' } });

    expect(executar(perfilGuard('ADMIN'), '/admin')).toBe('/dashboard');
    expect(TestBed.inject(NotificacaoService).itens().map((n) => n.texto)).toEqual([
      'Você não tem permissão para acessar esta área.',
    ]);
  });

  it('perfilGuard: sem sessão, nega', () => {
    expect(executar(perfilGuard('ADMIN'), '/admin')).toBe('/dashboard');
  });
});
