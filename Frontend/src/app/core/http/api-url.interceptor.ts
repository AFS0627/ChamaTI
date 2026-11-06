import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_CONFIG } from './api-config';

/**
 * Os serviços chamam caminhos relativos ('/api/chamados'); aqui eles ganham a origem do backend.
 * URLs absolutas (ou fora de /api/) passam sem alteração.
 */
export const apiUrlInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const { baseUrl } = inject(API_CONFIG);
  return next(req.clone({ url: `${baseUrl}${req.url}` }));
};
