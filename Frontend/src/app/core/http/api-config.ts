import { InjectionToken, Provider } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface ApiConfig {
  /** Origem do backend, sem barra final. Vazio significa a mesma origem do Angular. */
  readonly baseUrl: string;
}

function normalizar(config: ApiConfig): ApiConfig {
  return { baseUrl: config.baseUrl.trim().replace(/\/+$/, '') };
}

/** Único ponto que conhece o endereço do backend. O padrão vem de src/environments. */
export const API_CONFIG = new InjectionToken<ApiConfig>('API_CONFIG', {
  providedIn: 'root',
  factory: () => normalizar({ baseUrl: environment.apiUrl }),
});

/** Sobrescreve o endereço (testes, ou outro ambiente configurado em tempo de execução). */
export function provideApiConfig(config: ApiConfig): Provider {
  return { provide: API_CONFIG, useValue: normalizar(config) };
}

/** A URL já é de uma chamada à API (começa com a origem configurada e /api/)? */
export function ehRequisicaoDaApi(config: ApiConfig, url: string): boolean {
  return url.startsWith(`${config.baseUrl}/api/`);
}
