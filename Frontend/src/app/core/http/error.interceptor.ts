import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificacaoService } from '../notificacoes/notificacao.service';
import { ApiError } from './api-error';
import { SILENCIAR_ERROS } from './contexto';

/**
 * Aviso global só para o que a tela não consegue resolver sozinha:
 * sem conexão, erro do servidor (5xx) e sessão expirada. Erros de regra (400, 403, 404, 409, 422, 429)
 * seguem para o componente, que os mostra junto ao formulário.
 */
function avisoGlobal(erro: HttpErrorResponse, req: HttpRequest<unknown>): string | null {
  if (erro.status === 401) {
    // 401 do login é "senha incorreta": a tela mostra a mensagem da API.
    return req.url.endsWith('/api/auth/login') ? null : 'Sua sessão expirou. Entre novamente.';
  }
  if (erro.status === 0 || erro.status >= 500) {
    return ApiError.deHttp(erro).message;
  }
  return null;
}

/**
 * Deve ser o PRIMEIRO da cadeia: assim vê o erro final, depois da renovação de sessão feita pelo
 * interceptor de autenticação, e converte HttpErrorResponse em ApiError para o resto da aplicação.
 */
export const erroInterceptor: HttpInterceptorFn = (req, next) => {
  const notificacoes = inject(NotificacaoService);
  return next(req).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse)) {
        return throwError(() => erro);
      }
      const aviso = req.context.get(SILENCIAR_ERROS) ? null : avisoGlobal(erro, req);
      if (aviso !== null) {
        notificacoes.erro(aviso);
      }
      return throwError(() => ApiError.deHttp(erro, aviso !== null));
    }),
  );
};
