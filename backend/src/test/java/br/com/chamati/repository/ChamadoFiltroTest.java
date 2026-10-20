package br.com.chamati.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChamadoFiltroTest {

    static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    static final Instant ATE = Instant.parse("2026-02-01T00:00:00Z");

    private static ChamadoFiltro completo() {
        return new ChamadoFiltro("vpn", StatusChamado.ABERTO, Prioridade.ALTA, 3L, 9L, 2L, true, true, DE, ATE,
                "CT-2026-000001", 7L);
    }

    @Test
    void vazioNaoAplicaNenhumFiltro() {
        ChamadoFiltro f = ChamadoFiltro.vazio();
        assertThat(f.termo()).isNull();
        assertThat(f.status()).isNull();
        assertThat(f.prioridade()).isNull();
        assertThat(f.categoriaId()).isNull();
        assertThat(f.solicitanteId()).isNull();
        assertThat(f.tecnicoId()).isNull();
        assertThat(f.somenteSemTecnico()).isFalse();
        assertThat(f.somenteAtrasados()).isFalse();
        assertThat(f.criadoDe()).isNull();
        assertThat(f.criadoAte()).isNull();
        assertThat(f.protocolo()).isNull();
        assertThat(f.escopoTecnicoId()).isNull();
    }

    @Test
    void construtorAntigoMantemProtocoloEEscopoNulos() {
        ChamadoFiltro f = new ChamadoFiltro("x", StatusChamado.FECHADO, null, null, 4L, null, false, true, DE, ATE);
        assertThat(f.termo()).isEqualTo("x");
        assertThat(f.solicitanteId()).isEqualTo(4L);
        assertThat(f.somenteAtrasados()).isTrue();
        assertThat(f.protocolo()).isNull();
        assertThat(f.escopoTecnicoId()).isNull();
    }

    @Test
    void comSolicitanteSubstituiOValorEnviadoEPreservaOsDemaisCampos() {
        ChamadoFiltro f = completo().comSolicitante(4L);
        assertThat(f.solicitanteId()).isEqualTo(4L);
        assertThat(f).isEqualTo(new ChamadoFiltro("vpn", StatusChamado.ABERTO, Prioridade.ALTA, 3L, 4L, 2L, true,
                true, DE, ATE, "CT-2026-000001", 7L));
    }

    @Test
    void comEscopoTecnicoDefineSomenteOEscopo() {
        ChamadoFiltro f = completo().comEscopoTecnico(2L);
        assertThat(f.escopoTecnicoId()).isEqualTo(2L);
        assertThat(f).isEqualTo(new ChamadoFiltro("vpn", StatusChamado.ABERTO, Prioridade.ALTA, 3L, 9L, 2L, true,
                true, DE, ATE, "CT-2026-000001", 2L));
    }

    @Test
    void filtroOriginalNaoEhAlterado() {
        ChamadoFiltro original = ChamadoFiltro.vazio();
        original.comSolicitante(4L);
        original.comEscopoTecnico(2L);
        assertThat(original).isEqualTo(ChamadoFiltro.vazio());
    }
}
