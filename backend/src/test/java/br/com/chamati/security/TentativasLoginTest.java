package br.com.chamati.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chamati.exception.TentativasExcedidasException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TentativasLoginTest {

    static class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-01-01T00:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    private final RelogioMutavel relogio = new RelogioMutavel();
    private final TentativasLogin tentativas = new TentativasLogin(relogio);

    private void falhar(String chave, int vezes) {
        for (int i = 0; i < vezes; i++) {
            tentativas.verificar(chave);
            tentativas.registrarFalha(chave);
        }
    }

    @Test
    void bloqueiaAposCincoFalhasEInformaTempoRestante() {
        falhar("a|1", 5);
        assertThatThrownBy(() -> tentativas.verificar("a|1"))
                .isInstanceOfSatisfying(TentativasExcedidasException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getSegundos()).isBetween(890L, 901L));
    }

    @Test
    void quatroFalhasNaoBloqueiam() {
        falhar("a|1", 4);
        assertThatCode(() -> tentativas.verificar("a|1")).doesNotThrowAnyException();
    }

    @Test
    void chavesSaoIndependentes() {
        falhar("a|1", 5);
        assertThatCode(() -> tentativas.verificar("b|1")).doesNotThrowAnyException();
    }

    @Test
    void desbloqueiaDepoisDoPeriodo() {
        falhar("a|1", 5);
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(16));
        assertThatCode(() -> tentativas.verificar("a|1")).doesNotThrowAnyException();
        falhar("a|1", 4);
        assertThatCode(() -> tentativas.verificar("a|1")).doesNotThrowAnyException();
    }

    @Test
    void sucessoZeraOContador() {
        falhar("a|1", 4);
        tentativas.limpar("a|1");
        falhar("a|1", 4);
        assertThatCode(() -> tentativas.verificar("a|1")).doesNotThrowAnyException();
    }

    @Test
    void falhasForaDaJanelaNaoSeAcumulam() {
        falhar("a|1", 4);
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(16));
        falhar("a|1", 4);
        assertThatCode(() -> tentativas.verificar("a|1")).doesNotThrowAnyException();
    }
}
