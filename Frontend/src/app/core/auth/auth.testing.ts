import { Provider } from '@angular/core';
import { TokenResponse, UsuarioSessao } from '../models/api.models';
import { Sessao } from './sessao';
import { TRAVA_RENOVACAO } from './trava-renovacao';

/** Apoio aos testes de autenticação (não é usado pela aplicação). */

export const USUARIO_TESTE: UsuarioSessao = { id: 1, nome: 'Admin', email: 'admin@chamati.local', perfil: 'ADMIN' };

export const CHAVE_SESSAO = 'chamati.sessao';

export function tokensDeTeste(sufixo: string, expiraEmSegundos = 900, usuario: UsuarioSessao = USUARIO_TESTE): TokenResponse {
  return {
    tipo: 'Bearer',
    accessToken: `acc${sufixo}`,
    expiraEmSegundos,
    refreshToken: `ref${sufixo}`,
    refreshExpiraEm: '2030-01-01T00:00:00Z',
    usuario,
  };
}

/** Grava uma sessão no localStorage (por padrão com o access token válido por mais 15 minutos). */
export function guardarSessaoDeTeste(sufixo = '1', alteracoes: Partial<Sessao> = {}): Sessao {
  const sessao: Sessao = {
    accessToken: `acc${sufixo}`,
    refreshToken: `ref${sufixo}`,
    usuario: USUARIO_TESTE,
    accessExpiraEm: Date.now() + 15 * 60 * 1000,
    ...alteracoes,
  };
  localStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao));
  return sessao;
}

/** Trava que executa na hora: os testes controlam o fluxo sem depender do Web Locks do navegador. */
export const semTrava: Provider = {
  provide: TRAVA_RENOVACAO,
  useValue: <T>(tarefa: () => Promise<T>): Promise<T> => tarefa(),
};

/** Deixa as promessas pendentes (renovação com trava) concluírem. */
export const aguardar = (): Promise<void> => new Promise<void>((resolve) => setTimeout(resolve, 0));
