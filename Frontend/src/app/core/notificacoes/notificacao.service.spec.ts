import { TestBed } from '@angular/core/testing';
import { NotificacaoService } from './notificacao.service';

describe('NotificacaoService', () => {
  let avisos: NotificacaoService;

  beforeEach(() => {
    avisos = TestBed.inject(NotificacaoService);
  });

  it('adiciona avisos de cada tipo, em ordem', () => {
    avisos.erro('falhou', 0);
    avisos.sucesso('salvou', 0);
    avisos.info('fique sabendo', 0);

    expect(avisos.itens().map((n) => n.tipo)).toEqual(['erro', 'sucesso', 'info']);
    expect(avisos.itens().map((n) => n.texto)).toEqual(['falhou', 'salvou', 'fique sabendo']);
  });

  it('não repete uma mensagem que já está na tela', () => {
    avisos.erro('sem conexão', 0);
    avisos.erro('sem conexão', 0);

    expect(avisos.itens().length).toBe(1);
  });

  it('remove um aviso pelo id', () => {
    avisos.erro('a', 0);
    avisos.erro('b', 0);

    avisos.remover(avisos.itens()[0].id);

    expect(avisos.itens().map((n) => n.texto)).toEqual(['b']);
  });

  it('mantém no máximo quatro avisos, descartando o mais antigo', () => {
    for (const texto of ['1', '2', '3', '4', '5']) {
      avisos.erro(texto, 0);
    }

    expect(avisos.itens().map((n) => n.texto)).toEqual(['2', '3', '4', '5']);
  });

  it('remove o aviso sozinho depois do tempo', async () => {
    avisos.erro('passageiro', 20);
    expect(avisos.itens().length).toBe(1);

    await new Promise<void>((resolve) => setTimeout(resolve, 60));

    expect(avisos.itens().length).toBe(0);
  });
});
