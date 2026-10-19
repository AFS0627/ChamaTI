package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.entity.RefreshToken;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.repository.*;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.repository.consulta.ChamadoResumo;
import br.com.chamati.repository.consulta.ContagemStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RepositoriosIntegrationTest extends IntegrationTestBase {

    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired SubcategoriaRepository subcategorias;
    @Autowired PoliticaSlaRepository politicas;
    @Autowired SequenciaProtocoloRepository sequencias;
    @Autowired ChamadoRepository chamados;
    @Autowired ComentarioRepository comentarios;
    @Autowired HistoricoChamadoRepository historico;
    @Autowired RefreshTokenRepository tokens;

    static final PageRequest PAGINA = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "criadoEm"));

    private static ChamadoFiltro filtro(StatusChamado status, String termo, Long solicitante, boolean atrasados) {
        return new ChamadoFiltro(termo, status, null, null, solicitante, null, false, atrasados, null, null);
    }

    @Test
    void usuarios() {
        assertThat(this.usuarios.findByEmail("ADMIN@chamati.local")).isPresent(); // collation ci
        assertThat(this.usuarios.findByIdAndPerfilAndAtivoTrue(2L, Perfil.TECNICO)).isPresent();
        assertThat(this.usuarios.findByIdAndPerfilAndAtivoTrue(4L, Perfil.TECNICO)).isEmpty();
        assertThat(this.usuarios.findByPerfilAndAtivoTrueOrderByNomeAsc(Perfil.TECNICO)).hasSize(2);
        assertThat(this.usuarios.buscar(Perfil.SOLICITANTE, true, null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
        assertThat(this.usuarios.buscar(null, null, "souza", PageRequest.of(0, 10)).getContent())
                .extracting("email").containsExactly("ana.souza@chamati.local");
    }

    @Test
    void categoriasSubcategoriasEPoliticas() {
        assertThat(categorias.findByAtivoTrueOrderByNomeAsc()).hasSize(8);
        assertThat(categorias.existsByNome("hardware")).isTrue();
        assertThat(categorias.buscar(true, "rede", PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(subcategorias.findByCategoriaIdAndAtivoTrueOrderByNomeAsc(3L)).hasSize(3);
        assertThat(subcategorias.findByIdAndCategoriaId(8L, 3L)).isPresent();
        assertThat(subcategorias.findByIdAndCategoriaId(8L, 1L)).isEmpty();
        assertThat(politicas.findByPrioridadeAndAtivoTrue(Prioridade.ALTA).orElseThrow().getPrazoResolucaoMin())
                .isEqualTo(480);
    }

    @Test
    void chamadosFiltrosEPaginacao() {
        assertThat(chamados.buscar(ChamadoFiltro.vazio(), PAGINA).getTotalElements()).isEqualTo(12);
        assertThat(chamados.buscar(filtro(StatusChamado.EM_ATENDIMENTO, null, null, false), PAGINA)
                .getTotalElements()).isEqualTo(3);
        assertThat(chamados.buscar(filtro(null, null, 4L, false), PAGINA).getTotalElements()).isEqualTo(5);
        assertThat(chamados.buscar(filtro(null, "VPN", null, false), PAGINA).getContent())
                .extracting(ChamadoResumo::protocolo).containsExactly("CT-2026-000003");
        assertThat(chamados.buscar(filtro(null, "CT-2026-000005", null, false), PAGINA).getTotalElements()).isEqualTo(1);
        assertThat(chamados.buscar(filtro(null, null, null, true), PAGINA).getContent())
                .extracting(ChamadoResumo::protocolo)
                .containsExactlyInAnyOrder("CT-2026-000003", "CT-2026-000004", "CT-2026-000008");

        var semTecnico = new ChamadoFiltro(null, null, null, null, null, null, true, false, null, null);
        assertThat(chamados.buscar(semTecnico, PAGINA).getTotalElements()).isEqualTo(2);

        var recentes = new ChamadoFiltro(null, null, null, null, null, null, false, false,
                Instant.now().minus(2, ChronoUnit.HOURS), null);
        assertThat(chamados.buscar(recentes, PAGINA).getTotalElements()).isEqualTo(2); // chamados 5 e 9

        assertThat(chamados.buscar(ChamadoFiltro.vazio(), PageRequest.of(1, 5)).getContent()).hasSize(5);
    }

    @Test
    void chamadoDetalheEContagens() {
        var c = chamados.findDetalheById(3L).orElseThrow();
        assertThat(c.getCategoria().getNome()).isEqualTo("Rede e conectividade");
        assertThat(chamados.findByProtocolo("CT-2026-000001")).isPresent();
        assertThat(chamados.contarPorStatus(null, null))
                .contains(new ContagemStatus(StatusChamado.EM_ATENDIMENTO, 3));
        assertThat(chamados.contarPorStatus(null, 2L)).isNotEmpty();
    }

    @Test
    void comentariosEHistoricoComFiltroDeNotaInterna() {
        assertThat(comentarios.findByChamadoId(3L, PageRequest.of(0, 10, Sort.by("criadoEm"))).getTotalElements()).isEqualTo(3);
        assertThat(comentarios.findByChamadoIdAndTipo(3L, TipoComentario.PUBLICO, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(historico.findByChamadoId(3L, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(7);
        assertThat(historico.findByChamadoIdAndTipoEventoNot(3L, TipoEventoHistorico.NOTA_INTERNA, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(6);
    }

    @Test
    void protocoloAtomicoETokens() {
        sequencias.proximoNumero(2099);
        assertThat(sequencias.ultimoIdGerado()).isEqualTo(1L);
        sequencias.proximoNumero(2099);
        assertThat(sequencias.ultimoIdGerado()).isEqualTo(2L);

        RefreshToken t = new RefreshToken();
        t.setUsuario(usuarios.getReferenceById(4L));
        t.setTokenHash("a".repeat(64));
        t.setExpiraEm(Instant.now().plus(7, ChronoUnit.DAYS));
        tokens.saveAndFlush(t);

        assertThat(tokens.findByTokenHash("a".repeat(64))).isPresent();
        assertThat(tokens.revogarTodosDoUsuario(4L, Instant.now())).isEqualTo(1);
        assertThat(tokens.findByTokenHash("a".repeat(64)).orElseThrow().estaRevogado()).isTrue();
        assertThat(tokens.apagarExpiradosAte(Instant.now().plus(30, ChronoUnit.DAYS))).isEqualTo(1);
    }
}
