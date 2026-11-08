import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Perfil } from '../models/api.models';
import { NotificacaoService } from '../notificacoes/notificacao.service';
import { AuthService } from './auth.service';

/** Exige sessão; sem ela, envia para o login guardando o endereço pedido. */
export const autenticadoGuard: CanActivateFn = (_rota, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.autenticado() ? true : router.createUrlTree(['/login'], { queryParams: { retorno: estado.url } });
};

/** Quem já tem sessão não precisa ver o login. */
export const visitanteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.autenticado() ? router.createUrlTree(['/dashboard']) : true;
};

/**
 * Restringe uma área a certos perfis. Serve para não mostrar telas inúteis: quem decide é sempre a API,
 * que confere o perfil atual no banco a cada chamada.
 */
export const perfilGuard =
  (...perfis: Perfil[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const usuario = auth.usuario();
    if (usuario !== null && perfis.includes(usuario.perfil)) {
      return true;
    }
    inject(NotificacaoService).erro('Você não tem permissão para acessar esta área.');
    return router.createUrlTree(['/dashboard']);
  };
