import { formatarTamanho, rotuloEvento, rotuloValorHistorico } from './rotulos';

describe('rotulos', () => {
  it('traduz status e prioridades do histórico e preserva o resto', () => {
    expect(rotuloValorHistorico('EM_ATENDIMENTO')).toBe('Em atendimento');
    expect(rotuloValorHistorico('CRITICA')).toBe('Crítica');
    expect(rotuloValorHistorico('Carlos Mendes')).toBe('Carlos Mendes');
  });

  it('mostra um traço quando não há valor', () => {
    expect(rotuloValorHistorico(null)).toBe('—');
    expect(rotuloValorHistorico('')).toBe('—');
  });

  it('dá nome legível aos eventos', () => {
    expect(rotuloEvento('ALTERACAO_STATUS')).toBe('Alteração de status');
    expect(rotuloEvento('CRIACAO')).toBe('Criação');
  });

  it('formata tamanhos de arquivo', () => {
    expect(formatarTamanho(512)).toBe('512 B');
    expect(formatarTamanho(2048)).toBe('2.0 KB');
    expect(formatarTamanho(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});
