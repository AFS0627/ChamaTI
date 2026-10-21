package br.com.chamati.service;

import br.com.chamati.enums.StatusChamado;
import java.util.EnumSet;
import java.util.Set;

/**
 * Transições do ciclo de vida do chamado, sem dependências de framework.
 *
 * <pre>
 * ABERTO ──► EM_ATENDIMENTO ◄──► AGUARDANDO_USUARIO ◄──► AGUARDANDO_TERCEIRO   (alterar status)
 *            └── qualquer um dos três em atendimento ──► RESOLVIDO              (resolver)
 * RESOLVIDO ──► FECHADO                                                         (fechar)
 * RESOLVIDO / FECHADO ──► EM_ATENDIMENTO (ou ABERTO, sem técnico)               (reabrir)
 * </pre>
 */
public final class MaquinaEstadosChamado {

    private static final Set<StatusChamado> EM_ANDAMENTO = EnumSet.of(
            StatusChamado.EM_ATENDIMENTO, StatusChamado.AGUARDANDO_USUARIO, StatusChamado.AGUARDANDO_TERCEIRO);

    private MaquinaEstadosChamado() {
    }

    /** Alteração simples de status (RESOLVIDO, FECHADO e reabertura têm operações próprias). */
    public static boolean podeAlterarStatus(StatusChamado de, StatusChamado para) {
        if (de == para) {
            return false;
        }
        if (de == StatusChamado.ABERTO) {
            return para == StatusChamado.EM_ATENDIMENTO;
        }
        return EM_ANDAMENTO.contains(de) && EM_ANDAMENTO.contains(para);
    }

    public static boolean podeResolver(StatusChamado de) {
        return EM_ANDAMENTO.contains(de);
    }

    public static boolean podeFechar(StatusChamado de) {
        return de == StatusChamado.RESOLVIDO;
    }

    public static boolean podeReabrir(StatusChamado de) {
        return de == StatusChamado.RESOLVIDO || de == StatusChamado.FECHADO;
    }

    /** Só chamados ainda não resolvidos podem receber ou trocar o técnico. */
    public static boolean podeAtribuir(StatusChamado de) {
        return de != StatusChamado.RESOLVIDO && de != StatusChamado.FECHADO;
    }

    /** Chamados fechados não recebem comentários: reabra o chamado antes. */
    public static boolean podeComentar(StatusChamado de) {
        return de != StatusChamado.FECHADO;
    }

    public static StatusChamado statusAposReabertura(boolean temTecnico) {
        return temTecnico ? StatusChamado.EM_ATENDIMENTO : StatusChamado.ABERTO;
    }
}
