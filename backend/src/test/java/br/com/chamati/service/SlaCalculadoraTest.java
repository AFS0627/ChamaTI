package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.enums.SituacaoSla;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlaCalculadoraTest {

    static final Instant CRIADO = Instant.parse("2026-03-10T12:00:00Z");

    @Test
    void limiteEhCriadoEmMaisPrazoEmMinutosCorridos() {
        // tabela do projeto: CRITICA 15 min/4 h, ALTA 1 h/8 h, MEDIA 4 h/24 h, BAIXA 8 h/72 h
        assertThat(SlaCalculadora.limite(CRIADO, 15)).isEqualTo(Instant.parse("2026-03-10T12:15:00Z"));
        assertThat(SlaCalculadora.limite(CRIADO, 240)).isEqualTo(Instant.parse("2026-03-10T16:00:00Z"));
        assertThat(SlaCalculadora.limite(CRIADO, 60)).isEqualTo(Instant.parse("2026-03-10T13:00:00Z"));
        assertThat(SlaCalculadora.limite(CRIADO, 480)).isEqualTo(Instant.parse("2026-03-10T20:00:00Z"));
        assertThat(SlaCalculadora.limite(CRIADO, 1440)).isEqualTo(Instant.parse("2026-03-11T12:00:00Z"));
        assertThat(SlaCalculadora.limite(CRIADO, 4320)).isEqualTo(Instant.parse("2026-03-13T12:00:00Z"));
    }

    @Test
    void prazosSaoCorridosSemExpedienteNemFeriados() {
        // criado numa sexta às 23:00: 4 h corridas atravessam o fim de semana normalmente
        Instant sexta = Instant.parse("2026-03-13T23:00:00Z");
        assertThat(SlaCalculadora.limite(sexta, 24 * 60 * 3)).isEqualTo(Instant.parse("2026-03-16T23:00:00Z"));
    }

    @Test
    void mudancaDePrioridadeRecalculaSempreAPartirDeCriadoEm() {
        Instant limiteBaixa = SlaCalculadora.limite(CRIADO, 4320);
        Instant agora = CRIADO.plus(Duration.ofHours(10)); // momento da mudança
        Instant limiteCritica = SlaCalculadora.limite(CRIADO, 240);

        assertThat(limiteBaixa).isEqualTo(Instant.parse("2026-03-13T12:00:00Z"));
        assertThat(limiteCritica).isEqualTo(Instant.parse("2026-03-10T16:00:00Z"));
        assertThat(limiteCritica).isNotEqualTo(SlaCalculadora.limite(agora, 240)); // não reinicia na mudança
    }

    @Test
    void situacaoQuandoJaRealizado() {
        Instant limite = Instant.parse("2026-03-10T16:00:00Z");
        assertThat(SlaCalculadora.situacao(limite, limite, limite.plusSeconds(999)))
                .isEqualTo(SituacaoSla.DENTRO_DO_PRAZO);                       // igual ao limite = dentro
        assertThat(SlaCalculadora.situacao(limite, limite.minusSeconds(1), limite.plusSeconds(999)))
                .isEqualTo(SituacaoSla.DENTRO_DO_PRAZO);
        assertThat(SlaCalculadora.situacao(limite, limite.plusSeconds(1), limite.plusSeconds(999)))
                .isEqualTo(SituacaoSla.ATRASADO);                              // resolvido depois do limite
    }

    @Test
    void situacaoQuandoAindaNaoRealizado() {
        Instant limite = Instant.parse("2026-03-10T16:00:00Z");
        assertThat(SlaCalculadora.situacao(limite, null, limite)).isEqualTo(SituacaoSla.EM_ANDAMENTO); // agora == limite
        assertThat(SlaCalculadora.situacao(limite, null, limite.minusSeconds(1))).isEqualTo(SituacaoSla.EM_ANDAMENTO);
        assertThat(SlaCalculadora.situacao(limite, null, limite.plusSeconds(1))).isEqualTo(SituacaoSla.ATRASADO);
    }

    @Test
    void semLimiteNaoHaSla() {
        assertThat(SlaCalculadora.situacao(null, null, CRIADO)).isEqualTo(SituacaoSla.SEM_SLA);
    }
}
