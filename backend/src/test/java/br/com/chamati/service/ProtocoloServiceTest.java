package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import br.com.chamati.repository.SequenciaProtocoloRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProtocoloServiceTest {

    @Mock SequenciaProtocoloRepository sequencias;
    ProtocoloService service;

    @BeforeEach
    void preparar() {
        service = new ProtocoloService(sequencias);
    }

    @Test
    void geraProtocoloComAnoEmUtcENumeroComSeisDigitos() {
        when(sequencias.ultimoIdGerado()).thenReturn(13L);

        String protocolo = service.gerar(Instant.parse("2026-10-29T12:00:00Z"));

        assertThat(protocolo).isEqualTo("CT-2026-000013");
        InOrder ordem = inOrder(sequencias);
        ordem.verify(sequencias).proximoNumero(2026);
        ordem.verify(sequencias).ultimoIdGerado();
    }

    @Test
    void usaOAnoUtcNaVirada() {
        when(sequencias.ultimoIdGerado()).thenReturn(1L);

        assertThat(service.gerar(Instant.parse("2026-12-31T23:59:59Z"))).isEqualTo("CT-2026-000001");
        assertThat(service.gerar(Instant.parse("2027-01-01T00:00:00Z"))).isEqualTo("CT-2027-000001");
    }

    @Test
    void falhaQuandoOBancoNaoDevolveUmNumeroValido() {
        when(sequencias.ultimoIdGerado()).thenReturn(null, 0L);

        assertThatThrownBy(() -> service.gerar(Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.gerar(Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
    }
}
