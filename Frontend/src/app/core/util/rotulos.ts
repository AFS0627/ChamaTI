import { Perfil, Prioridade, SituacaoSla, StatusChamado, TipoEventoHistorico } from '../models/api.models';

const STATUS: Record<StatusChamado, string> = {
  ABERTO: 'Aberto',
  EM_ATENDIMENTO: 'Em atendimento',
  AGUARDANDO_USUARIO: 'Aguardando usuário',
  AGUARDANDO_TERCEIRO: 'Aguardando terceiro',
  RESOLVIDO: 'Resolvido',
  FECHADO: 'Fechado',
};

const PRIORIDADE: Record<Prioridade, string> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
  CRITICA: 'Crítica',
};

const PERFIL: Record<Perfil, string> = {
  ADMIN: 'Administrador',
  TECNICO: 'Técnico',
  SOLICITANTE: 'Solicitante',
};

const SLA: Record<SituacaoSla, string> = {
  EM_ANDAMENTO: 'Em andamento',
  DENTRO_DO_PRAZO: 'Dentro do prazo',
  ATRASADO: 'Atrasado',
  SEM_SLA: 'Sem SLA',
};

const EVENTO: Record<TipoEventoHistorico, string> = {
  CRIACAO: 'Criação',
  EDICAO: 'Edição',
  ALTERACAO_STATUS: 'Alteração de status',
  ALTERACAO_PRIORIDADE: 'Alteração de prioridade',
  ATRIBUICAO: 'Atribuição',
  COMENTARIO: 'Comentário',
  NOTA_INTERNA: 'Nota interna',
  RESOLUCAO: 'Resolução',
  ENCERRAMENTO: 'Encerramento',
  REABERTURA: 'Reabertura',
};

export const STATUS_LISTA = Object.keys(STATUS) as StatusChamado[];
export const PRIORIDADES = Object.keys(PRIORIDADE) as Prioridade[];
export const PERFIS = Object.keys(PERFIL) as Perfil[];

export const rotuloStatus = (s: StatusChamado): string => STATUS[s];
export const rotuloPrioridade = (p: Prioridade): string => PRIORIDADE[p];
export const rotuloPerfil = (p: Perfil): string => PERFIL[p];
export const rotuloEvento = (t: TipoEventoHistorico): string => EVENTO[t];

/** Valores do histórico: status e prioridades ganham rótulo; nomes e textos seguem como estão. */
export function rotuloValorHistorico(valor: string | null): string {
  if (valor === null || valor === '') {
    return '—';
  }
  const status = STATUS_LISTA.find((s) => s === valor);
  if (status) {
    return STATUS[status];
  }
  const prioridade = PRIORIDADES.find((p) => p === valor);
  return prioridade ? PRIORIDADE[prioridade] : valor;
}

export const rotuloSla = (s: SituacaoSla | null): string => (s ? SLA[s] : '—');

export function formatarTamanho(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
