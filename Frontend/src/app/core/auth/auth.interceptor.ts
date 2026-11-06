import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { API_CONFIG, ehRequisicaoDaApi } from '../http/api-config';
import { AuthService } from './auth.service';

const ROTAS_PUBLICAS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

function comToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/** Envia o access token e, ao receber 401, renova a sessão uma vez e repete a chamada. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const api = inject(API_CONFIG);
  const publica = ROTAS_PUBLICAS.some((rota) => req.url === `${api.baseUrl}${rota}`);
  if (!ehRequisicaoDaApi(api, req.url) || publica) {
    return next(req);
  }

  const token = auth.accessToken();
  return next(token ? comToken(req, token) : req).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse) || erro.status !== 401 || !token) {
        return throwError(() => erro);
      }
      const atual = auth.accessToken();
      if (atual && atual !== token) {
        return next(comToken(req, atual));
      }
      return auth.renovar().pipe(
        catchError((falha: unknown) => {
          auth.encerrarSessaoLocal();
          return throwError(() => falha);
        }),
        switchMap((novo) => next(comToken(req, novo))),
      );
    }),
  );
};
