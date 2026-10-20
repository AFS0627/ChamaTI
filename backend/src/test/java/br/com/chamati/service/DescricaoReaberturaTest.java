package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DescricaoReaberturaTest {

    static final Instant RESOLVIDO = Instant.parse("2026-03-10T12:34:56.789Z");
    static final Instant FECHADO = Instant.parse("2026-03-11T08:00:00Z");

    @Test
    void incluiMotivoEDatasAnterioresEmUtc() {
        assertThat(DescricaoReabertura.formatar("Problema voltou a ocorrer", RESOLVIDO, FECHADO))
                .isEqualTo("Problema voltou a ocorrer. resolvido_em anterior: 2026-03-10 12:34:56; "
                        + "fechado_em anterior: 2026-03-11 08:00:00");
    }

    @Test
    void semFechamentoIndicaNenhum() {
        assertThat(DescricaoReabertura.formatar("Voltou", RESOLVIDO, null))
                .isEqualTo("Voltou. resolvido_em anterior: 2026-03-10 12:34:56; fechado_em anterior: nenhum");
    }

    @Test
    void naoDuplicaPontoFinalNemEspacosDoMotivo() {
        assertThat(DescricaoReabertura.formatar("  Voltou a falhar.  ", RESOLVIDO, null))
                .startsWith("Voltou a falhar. resolvido_em anterior:");
    }

    @Test
    void nuncaUltrapassaOLimiteDaColuna() {
        String descricao = DescricaoReabertura.formatar("x".repeat(2000), RESOLVIDO, FECHADO);
        assertThat(descricao).hasSize(500).endsWith("; fechado_em anterior: 2026-03-11 08:00:00");
    }
}
