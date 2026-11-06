import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../http/api-error';

/**
 * Texto de erro para mostrar na tela.
 * Devolve '' quando o erro já foi exibido como aviso global (a tela não deve repeti-lo).
 */
export function mensagemDeErro(erro: unknown): string {
  if (erro instanceof ApiError) {
    return erro.notificado ? '' : erro.message;
  }
  if (erro instanceof HttpErrorResponse) {
    return ApiError.deHttp(erro).message;
  }
  return 'Ocorreu um erro inesperado.';
}
