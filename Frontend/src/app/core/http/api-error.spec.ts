import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './api-error';

describe('ApiError', () => {
  it('usa a mensagem do ErroResponse da API e resume os campos', () => {
    const erro = ApiError.deHttp(
      new HttpErrorResponse({
        status: 400,
        error: { mensagem: 'Dados inválidos.', campos: [{ campo: 'nome', mensagem: 'obrigatório' }] },
      }),
    );

    expect(erro.status).toBe(400);
    expect(erro.message).toBe('Dados inválidos. nome: obrigatório');
    expect(erro.campos).toEqual([{ campo: 'nome', mensagem: 'obrigatório' }]);
    expect(erro.notificado).toBe(false);
    expect(erro instanceof Error).toBe(true);
  });

  it('usa mensagens padrão quando o corpo não é da API', () => {
    const texto = (status: number): string => ApiError.deHttp(new HttpErrorResponse({ status })).message;

    expect(texto(0)).toBe('Não foi possível conectar ao servidor.');
    expect(texto(401)).toBe('Sessão expirada. Entre novamente.');
    expect(texto(403)).toBe('Você não tem permissão para esta operação.');
    expect(texto(404)).toBe('Registro não encontrado.');
    expect(texto(500)).toBe('Erro no servidor. Tente novamente em instantes.');
    expect(texto(503)).toBe('Erro no servidor. Tente novamente em instantes.');
    expect(texto(418)).toBe('Falha na requisição (418).');
  });

  it('guarda a resposta original e se já foi notificado', () => {
    const original = new HttpErrorResponse({ status: 500 });
    const erro = ApiError.deHttp(original, true);

    expect(erro.original).toBe(original);
    expect(erro.notificado).toBe(true);
  });
});
