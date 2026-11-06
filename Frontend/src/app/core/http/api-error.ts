import { HttpErrorResponse } from '@angular/common/http';
import { ErroApi } from '../models/api.models';

export interface CampoComErro {
  campo: string;
  mensagem: string;
}

function mensagemPorStatus(status: number): string {
  if (status === 0) {
    return 'Não foi possível conectar ao servidor.';
  }
  if (status === 401) {
    return 'Sessão expirada. Entre novamente.';
  }
  if (status === 403) {
    return 'Você não tem permissão para esta operação.';
  }
  if (status === 404) {
    return 'Registro não encontrado.';
  }
  if (status >= 500) {
    return 'Erro no servidor. Tente novamente em instantes.';
  }
  return `Falha na requisição (${status}).`;
}

/** Erro de HTTP já interpretado: é o que chega aos componentes depois do interceptor de erros. */
export class ApiError extends Error {
  /** Código HTTP (0 = sem conexão). */
  readonly status: number;
  /** Erros de validação por campo, quando a API os informa (também resumidos em `message`). */
  readonly campos: CampoComErro[];
  /** Verdadeiro quando o erro já foi mostrado como aviso global; a tela não precisa repeti-lo. */
  readonly notificado: boolean;
  readonly original: HttpErrorResponse;

  private constructor(original: HttpErrorResponse, mensagem: string, campos: CampoComErro[], notificado: boolean) {
    super(mensagem);
    this.name = 'ApiError';
    this.status = original.status;
    this.campos = campos;
    this.notificado = notificado;
    this.original = original;
  }

  /** Usa o ErroResponse da API quando existir; senão, uma mensagem padrão para o código. */
  static deHttp(erro: HttpErrorResponse, notificado = false): ApiError {
    const corpo: unknown = erro.error;
    if (corpo !== null && typeof corpo === 'object' && 'mensagem' in corpo) {
      const api = corpo as Partial<ErroApi>;
      const campos = api.campos ?? [];
      const detalhes = campos.map((c) => `${c.campo}: ${c.mensagem}`).join('; ');
      const base = api.mensagem ?? 'Requisição recusada.';
      return new ApiError(erro, detalhes ? `${base} ${detalhes}` : base, campos, notificado);
    }
    return new ApiError(erro, mensagemPorStatus(erro.status), [], notificado);
  }
}
