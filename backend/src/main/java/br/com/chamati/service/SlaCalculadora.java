package br.com.chamati.service;

import br.com.chamati.enums.SituacaoSla;
import java.time.Duration;
import java.time.Instant;

/** Regras de SLA do projeto (prazos corridos em minutos), sem dependências de framework. */
public final class SlaCalculadora {

    private SlaCalculadora() {
    }

    /** limite = criado_em + prazo (minutos corridos). */
    public static Instant limite(Instant criadoEm, int prazoMinutos) {
        return criadoEm.plus(Duration.ofMinutes(prazoMinutos));
    }

    /**
     * Dentro do prazo: realizado_em &lt;= limite. Atrasado: realizado após o limite, ou ainda não
     * realizado e agora &gt; limite.
     */
    public static SituacaoSla situacao(Instant limite, Instant realizadoEm, Instant agora) {
        if (limite == null) {
            return SituacaoSla.SEM_SLA;
        }
        if (realizadoEm != null) {
            return realizadoEm.isAfter(limite) ? SituacaoSla.ATRASADO : SituacaoSla.DENTRO_DO_PRAZO;
        }
        return agora.isAfter(limite) ? SituacaoSla.ATRASADO : SituacaoSla.EM_ANDAMENTO;
    }
}
