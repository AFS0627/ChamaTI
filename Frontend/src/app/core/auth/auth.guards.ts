import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Perfil } from '../models/api.models';
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

/** Restringe uma área a certos perfis (a API continua sendo a autoridade). */
export const perfilGuard =
  (...perfis: Perfil[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const usuario = auth.usuario();
    return usuario !== null && perfis.includes(usuario.perfil) ? true : router.createUrlTree(['/dashboard']);
  };
