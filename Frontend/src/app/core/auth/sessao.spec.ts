import { TokenResponse } from '../models/api.models';
import { USUARIO_TESTE, tokensDeTeste } from './auth.testing';
import { accessExpirando, criarSessao, lerSessao } from './sessao';

describe('sessao', () => {
  it('conta a validade do access token a partir de expiraEmSegundos', () => {
    const sessao = criarSessao(tokensDeTeste('1', 900), 1_000_000);

    expect(sessao.accessToken).toBe('acc1');
    expect(sessao.refreshToken).toBe('ref1');
    expect(sessao.accessExpiraEm).toBe(1_000_000 + 900_000);
  });

  it('considera "expirando" quando falta menos que a margem', () => {
    const sessao = criarSessao(tokensDeTeste('1', 900), 0);

    expect(accessExpirando(sessao, 0)).toBe(false);
    expect(accessExpirando(sessao, 869_000)).toBe(false);
    expect(accessExpirando(sessao, 870_000)).toBe(true);
    expect(accessExpirando(sessao, 900_000, 0)).toBe(true);
    expect(accessExpirando(sessao, 899_999, 0)).toBe(false);
  });

  it('lê uma sessão gravada', () => {
    const texto = JSON.stringify({ accessToken: 'a', refreshToken: 'r', usuario: USUARIO_TESTE, accessExpiraEm: 123 });

    expect(lerSessao(texto)).toEqual({ accessToken: 'a', refreshToken: 'r', usuario: USUARIO_TESTE, accessExpiraEm: 123 });
  });

  it('sessão antiga, sem validade, vale como já expirada (renova na primeira chamada)', () => {
    const texto = JSON.stringify({ accessToken: 'a', refreshToken: 'r', usuario: USUARIO_TESTE });

    expect(lerSessao(texto)?.accessExpiraEm).toBe(0);
  });

  it('ignora conteúdo inválido ou adulterado', () => {
    expect(lerSessao(null)).toBeNull();
    expect(lerSessao('não é json')).toBeNull();
    expect(lerSessao('42')).toBeNull();
    expect(lerSessao(JSON.stringify({ accessToken: 'a' }))).toBeNull();
    expect(lerSessao(JSON.stringify({ accessToken: 'a', refreshToken: 'r', usuario: { ...USUARIO_TESTE, perfil: 'ROOT' } }))).toBeNull();
    expect(lerSessao(JSON.stringify({ accessToken: 1, refreshToken: 'r', usuario: USUARIO_TESTE }))).toBeNull();
  });

  it('guarda também o usuário devolvido pelo backend', () => {
    const resposta: TokenResponse = tokensDeTeste('1', 900, { ...USUARIO_TESTE, perfil: 'TECNICO' });

    expect(criarSessao(resposta, 0).usuario.perfil).toBe('TECNICO');
  });
});
