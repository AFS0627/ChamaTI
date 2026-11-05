import { HttpParams } from '@angular/common/http';

type Valor = string | number | boolean | null | undefined;

/** Monta a query string ignorando valores ausentes ou vazios (false é enviado). */
export function montarParams(valores: Record<string, Valor>): HttpParams {
  let params = new HttpParams();
  for (const [chave, valor] of Object.entries(valores)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      params = params.set(chave, String(valor));
    }
  }
  return params;
}
