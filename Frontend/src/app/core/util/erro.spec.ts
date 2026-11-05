import { HttpErrorResponse } from '@angular/common/http';
import { mensagemDeErro } from './erro';

describe('mensagemDeErro', () => {
  it('avisa quando o servidor não responde', () => {
    const erro = new HttpErrorResponse({ status: 0 });
    expect(mensagemDeErro(erro)).toBe('Não foi possível conectar ao servidor.');
  });

  it('usa a mensagem e os campos do ErroResponse da API', () => {
    const erro = new HttpErrorResponse({
      status: 400,
      error: { mensagem: 'Dados inválidos.', campos: [{ campo: 'nome', mensagem: 'obrigatório' }] },
    });
    const texto = mensagemDeErro(erro);
    expect(texto).toContain('Dados inválidos.');
    expect(texto).toContain('nome: obrigatório');
  });

  it('cai em uma mensagem por código quando o corpo não é da API', () => {
    expect(mensagemDeErro(new HttpErrorResponse({ status: 403 }))).toBe('Você não tem permissão para esta operação.');
    expect(mensagemDeErro(new HttpErrorResponse({ status: 404 }))).toBe('Registro não encontrado.');
    expect(mensagemDeErro(new HttpErrorResponse({ status: 500 }))).toBe('Falha na requisição (500).');
  });

  it('trata erros que não são de HTTP', () => {
    expect(mensagemDeErro(new Error('x'))).toBe('Ocorreu um erro inesperado.');
  });
});
