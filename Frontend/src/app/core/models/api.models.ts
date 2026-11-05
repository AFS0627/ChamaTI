// Tipos da API do ChamaTI (espelham os DTOs do backend). Datas (Instant) chegam como texto ISO-8601.

export type Perfil = 'ADMIN' | 'TECNICO' | 'SOLICITANTE';
export type Prioridade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
export type StatusChamado =
  | 'ABERTO'
  | 'EM_ATENDIMENTO'
  | 'AGUARDANDO_USUARIO'
  | 'AGUARDANDO_TERCEIRO'
  | 'RESOLVIDO'
  | 'FECHADO';
export type SituacaoSla = 'EM_ANDAMENTO' | 'DENTRO_DO_PRAZO' | 'ATRASADO' | 'SEM_SLA';
export type TipoComentario = 'PUBLICO' | 'NOTA_INTERNA';
export type TipoEventoHistorico =
  | 'CRIACAO'
  | 'EDICAO'
  | 'ALTERACAO_STATUS'
  | 'ALTERACAO_PRIORIDADE'
  | 'ATRIBUICAO'
  | 'COMENTARIO'
  | 'NOTA_INTERNA'
  | 'RESOLUCAO'
  | 'ENCERRAMENTO'
  | 'REABERTURA';

export interface Pagina<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface ErroApi {
  timestamp: string;
  status: number;
  erro: string;
  mensagem?: string;
  campos?: { campo: string; mensagem: string }[];
}

// ---- Autenticação ----
export interface UsuarioSessao {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
}

export interface TokenResponse {
  tipo: string;
  accessToken: string;
  expiraEmSegundos: number;
  refreshToken: string;
  refreshExpiraEm: string;
  usuario: UsuarioSessao;
}

// ---- Chamados ----
export interface Referencia {
  id: number;
  nome: string;
}

export interface Autor {
  id: number;
  nome: string;
  perfil: Perfil;
}

export interface ChamadoResumo {
  id: number;
  protocolo: string;
  titulo: string;
  prioridade: Prioridade;
  status: StatusChamado;
  categoriaId: number;
  categoriaNome: string;
  solicitanteId: number;
  solicitanteNome: string;
  tecnicoId: number | null;
  tecnicoNome: string | null;
  criadoEm: string;
  atualizadoEm: string;
  slaResolucaoLimite: string | null;
  resolvidoEm: string | null;
}

export interface Chamado {
  id: number;
  protocolo: string;
  titulo: string;
  descricao: string;
  prioridade: Prioridade;
  status: StatusChamado;
  categoria: Referencia;
  subcategoria: Referencia | null;
  solicitante: Referencia;
  tecnico: Referencia | null;
  criadoEm: string;
  atualizadoEm: string;
  atribuidoEm: string | null;
  primeiraRespostaEm: string | null;
  resolvidoEm: string | null;
  fechadoEm: string | null;
  reabertoEm: string | null;
  totalReaberturas: number;
  solucao: string | null;
  slaPrimeiraRespostaLimite: string | null;
  slaResolucaoLimite: string | null;
  situacaoSlaPrimeiraResposta: SituacaoSla | null;
  situacaoSlaResolucao: SituacaoSla | null;
  versao: number;
}

export interface ChamadoCriar {
  titulo: string;
  descricao: string;
  categoriaId: number;
  subcategoriaId: number | null;
  prioridade: Prioridade;
}

export interface FiltrosChamados {
  q?: string;
  status?: StatusChamado | '';
  prioridade?: Prioridade | '';
  semTecnico?: boolean;
  atrasados?: boolean;
  pagina?: number;
  tamanho?: number;
}

export interface Comentario {
  id: number;
  tipo: TipoComentario;
  conteudo: string;
  autor: Autor;
  criadoEm: string;
}

export interface Historico {
  id: number;
  tipoEvento: TipoEventoHistorico;
  campo: string | null;
  valorAnterior: string | null;
  valorNovo: string | null;
  descricao: string | null;
  autor: Autor | null;
  criadoEm: string;
}

export interface Anexo {
  id: number;
  nome: string;
  tipoConteudo: string;
  tamanho: number;
  autor: Autor;
  criadoEm: string;
}

export interface Avaliacao {
  id: number;
  chamadoId: number;
  protocolo: string;
  nota: number;
  comentario: string | null;
  solicitante: Autor;
  tecnico: Autor;
  criadoEm: string;
}

export interface MediaTecnico {
  tecnicoId: number;
  tecnicoNome: string;
  media: number;
  quantidade: number;
}

// ---- Dashboard ----
export interface Dashboard {
  geradoEm: string;
  periodoDias: number;
  totais: {
    total: number;
    emAberto: number;
    semTecnico: number;
    atribuidosAMim: number;
    emAtrasoResolucao: number;
    emAtrasoPrimeiraResposta: number;
  };
  porStatus: Record<StatusChamado, number>;
  emAbertoPorPrioridade: Record<Prioridade, number>;
  emAbertoPorCategoria: { categoriaId: number; nome: string; total: number }[];
  periodo: { desde: string; abertos: number; resolvidos: number };
}

// ---- Administração ----
export interface UsuarioResumo {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  departamento: string | null;
  ativo: boolean;
  ultimoLoginEm: string | null;
}

export interface UsuarioCriar {
  nome: string;
  email: string;
  senha: string;
  perfil: Perfil;
  departamento: string | null;
  telefone: string | null;
}

export interface Categoria {
  id: number;
  nome: string;
  descricao: string | null;
  ativo: boolean;
}

export interface Subcategoria {
  id: number;
  categoriaId: number;
  nome: string;
  descricao: string | null;
  ativo: boolean;
}
