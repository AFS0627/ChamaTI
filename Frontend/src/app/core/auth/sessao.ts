import { Perfil, TokenResponse, UsuarioSessao } from '../models/api.models';

/** Quanto antes de expirar o access token passa a ser renovado (o backend o emite com 15 min de vida). */
export const MARGEM_RENOVACAO_MS = 30_000;

const PERFIS: readonly Perfil[] = ['ADMIN', 'TECNICO', 'SOLICITANTE'];

export interface Sessao {
  accessToken: string;
  /** Opaco, de uso único: a cada renovação o backend o troca por outro e revoga o anterior. */
  refreshToken: string;
  usuario: UsuarioSessao;
  /** Instante (ms) em que o access token expira, contado pelo relógio local a partir de expiraEmSegundos. */
  accessExpiraEm: number;
}

export function criarSessao(resposta: TokenResponse, agora: number): Sessao {
  return {
    accessToken: resposta.accessToken,
    refreshToken: resposta.refreshToken,
    usuario: resposta.usuario,
    accessExpiraEm: agora + resposta.expiraEmSegundos * 1000,
  };
}

/** O access token já expirou ou expira dentro da margem? */
export function accessExpirando(sessao: Sessao, agora: number, margemMs: number = MARGEM_RENOVACAO_MS): boolean {
  return agora >= sessao.accessExpiraEm - margemMs;
}

/** Interpreta o que está guardado; qualquer coisa fora do formato esperado vale como "sem sessão". */
export function lerSessao(texto: string | null): Sessao | null {
  if (texto === null) {
    return null;
  }
  try {
    const bruto: unknown = JSON.parse(texto);
    if (typeof bruto !== 'object' || bruto === null) {
      return null;
    }
    const s = bruto as Partial<Sessao>;
    const u = s.usuario;
    if (
      typeof s.accessToken !== 'string' ||
      typeof s.refreshToken !== 'string' ||
      !u ||
      typeof u.id !== 'number' ||
      typeof u.nome !== 'string' ||
      typeof u.email !== 'string' ||
      !PERFIS.includes(u.perfil)
    ) {
      return null;
    }
    // Sessões gravadas antes de existir a validade são renovadas na primeira chamada.
    return {
      accessToken: s.accessToken,
      refreshToken: s.refreshToken,
      usuario: u,
      accessExpiraEm: typeof s.accessExpiraEm === 'number' ? s.accessExpiraEm : 0,
    };
  } catch {
    return null;
  }
}
