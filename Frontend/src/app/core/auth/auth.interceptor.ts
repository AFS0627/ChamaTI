import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, of, switchMap, throwError } from 'rxjs';
import { API_CONFIG, ehRequisicaoDaApi } from '../http/api-config';
import { ApiError } from '../http/api-error';
import { AuthService } from './auth.service';

/** Endpoints que o backend atende sem token. */
const ROTAS_PUBLICAS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

function comToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/** Falha que não diz nada sobre a validade da sessão (sem conexão, 5xx, demora). */
function ehTransitoria(erro: unknown): boolean {
  if (erro instanceof ApiError || erro instanceof HttpErrorResponse) {
    return erro.status === 0 || erro.status >= 500;
  }
  return true;
}

function enviar(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
  token: string | null,
): Observable<HttpEvent<unknown>> {
  return next(token ? comToken(req, token) : req).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse) || erro.status !== 401 || token === null) {
        return throwError(() => erro);
      }
      // Access token rejeitado (expirou ou foi trocado): renova uma vez e repete.
      return auth.renovar(token).pipe(
        switchMap((novo) =>
          next(comToken(req, novo)).pipe(
            catchError((falha: unknown) => {
              // Token recém-renovado também recusado: o usuário foi desativado ou a sessão não vale mais.
              if (falha instanceof HttpErrorResponse && falha.status === 401) {
                auth.encerrarSessaoLocal();
              }
              return throwError(() => falha);
            }),
          ),
        ),
      );
    }),
  );
}

/**
 * Envia o access token; renova ANTES de expirar (evita o 401 de ida e volta) e, se ainda assim receber 401,
 * renova uma vez e repete a chamada. A renovação em si é serializada pelo AuthService.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const api = inject(API_CONFIG);
  const publica = ROTAS_PUBLICAS.some((rota) => req.url === `${api.baseUrl}${rota}`);
  if (!ehRequisicaoDaApi(api, req.url) || publica) {
    return next(req);
  }

  const token = auth.accessToken();
  const pronto: Observable<string | null> =
    token !== null && auth.accessExpirando()
      ? auth.renovar(token).pipe(
          // Renovação antecipada que falha por instabilidade: tenta com o token que ainda há.
          catchError((erro: unknown) => (ehTransitoria(erro) ? of(token) : throwError(() => erro))),
        )
      : of(token);

  return pronto.pipe(switchMap((t) => enviar(req, next, auth, t)));
};
