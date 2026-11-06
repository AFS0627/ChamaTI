import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { apiUrlInterceptor } from './core/http/api-url.interceptor';
import { erroInterceptor } from './core/http/error.interceptor';

// Sem provider de detecção de mudanças: o estado das telas usa signals e funciona com ou sem zone.js.
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    // Ordem importa: erro (vê o resultado final) → URL da API → autenticação (precisa da URL completa).
    provideHttpClient(withInterceptors([erroInterceptor, apiUrlInterceptor, authInterceptor])),
  ],
};
