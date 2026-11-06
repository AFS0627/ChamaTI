import { HttpContextToken } from '@angular/common/http';

/**
 * Marque uma requisição com `new HttpContext().set(SILENCIAR_ERROS, true)` quando a falha dela
 * não deve gerar aviso global (ex.: logout, que encerra a sessão local de qualquer forma).
 */
export const SILENCIAR_ERROS = new HttpContextToken<boolean>(() => false);
