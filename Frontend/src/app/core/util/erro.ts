import { HttpErrorResponse } from '@angular/common/http';
import { ErroApi } from '../models/api.models';

/** Texto amigável para um erro de HTTP, usando o ErroResponse da API quando existir. */
export function mensagemDeErro(erro: unknown): string {
  if (!(erro instanceof HttpErrorResponse)) {
    return 'Ocorreu um erro inesperado.';
  }
  if (erro.status === 0) {
    return 'Não foi possível conectar ao servidor.';
  }
  const corpo: unknown = erro.error;
  if (corpo !== null && typeof corpo === 'object' && 'mensagem' in corpo) {
    const api = corpo as Partial<ErroApi>;
    const campos = (api.campos ?? []).map((c) => `${c.campo}: ${c.mensagem}`).join('; ');
    const base = api.mensagem ?? 'Requisição recusada.';
    return campos ? `${base} ${campos}` : base;
  }
  if (erro.status === 401) {
    return 'Sessão expirada. Entre novamente.';
  }
  if (erro.status === 403) {
    return 'Você não tem permissão para esta operação.';
  }
  if (erro.status === 404) {
    return 'Registro não encontrado.';
  }
  return `Falha na requisição (${erro.status}).`;
}
