package br.com.chamati.enums;

/** chamados.status — ck_chamados_status. As transições válidas ficam no service. */
public enum StatusChamado {
    ABERTO,
    EM_ATENDIMENTO,
    AGUARDANDO_USUARIO,
    AGUARDANDO_TERCEIRO,
    RESOLVIDO,
    FECHADO
}
