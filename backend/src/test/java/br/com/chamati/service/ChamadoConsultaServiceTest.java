package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.chamado;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.SituacaoSla;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.ChamadoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.repository.consulta.ChamadoResumo;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ChamadoConsultaServiceTest {

    @Mock ChamadoRepository chamados;
    ChamadoConsultaService service;

    Usuario admin = usuario(1L, "Admin", Perfil.ADMIN);
    Usuario carlos = usuario(2L, "Carlos Mendes", Perfil.TECNICO);
    Usuario fernanda = usuario(3L, "Fernanda Lima", Perfil.TECNICO);
    Usuario ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
    Usuario bruno = usuario(5L, "Bruno Costa", Perfil.SOLICITANTE);

    @BeforeEach
    void preparar() {
        service = new ChamadoConsultaService(chamados, new ChamadoMapper(), Fixtures.relogio());
    }

    private ChamadoFiltro filtroCapturado(UsuarioPrincipal ator, ChamadoFiltro entrada) {
        when(chamados.buscar(any(ChamadoFiltro.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.listar(entrada, null, null, 0, 20, ator);
        ArgumentCaptor<ChamadoFiltro> captor = ArgumentCaptor.forClass(ChamadoFiltro.class);
        verify(chamados).buscar(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    // ---------- visibilidade ----------

    @Test
    void adminVeTodosOsChamados() {
        assertThat(ChamadoConsultaService.visivel(chamado(1L, ana, carlos, StatusChamado.ABERTO), principal(admin))).isTrue();
    }

    @Test
    void solicitanteVeSomenteOsQueAbriu() {
        Chamado daAna = chamado(1L, ana, carlos, StatusChamado.ABERTO);

        assertThat(ChamadoConsultaService.visivel(daAna, principal(ana))).isTrue();
        assertThat(ChamadoConsultaService.visivel(daAna, principal(bruno))).isFalse();
    }

    @Test
    void tecnicoVeOsAtribuidosAEleAFilaSemTecnicoEOsQueAbriu() {
        UsuarioPrincipal pCarlos = principal(carlos);

        assertThat(ChamadoConsultaService.visivel(chamado(1L, ana, carlos, StatusChamado.EM_ATENDIMENTO), pCarlos)).isTrue();
        assertThat(ChamadoConsultaService.visivel(chamado(2L, ana, null, StatusChamado.ABERTO), pCarlos)).isTrue();
        assertThat(ChamadoConsultaService.visivel(chamado(3L, carlos, fernanda, StatusChamado.ABERTO), pCarlos)).isTrue();
    }

    @Test
    void tecnicoNaoVeChamadoAtribuidoAOutroTecnicoDeOutroSolicitante() {
        assertThat(ChamadoConsultaService.visivel(chamado(1L, ana, fernanda, StatusChamado.EM_ATENDIMENTO),
                principal(carlos))).isFalse();
    }

    // ---------- listagem ----------

    @Test
    void adminListaSemRestricaoAdicional() {
        ChamadoFiltro entrada = ChamadoFiltro.vazio();

        ChamadoFiltro aplicado = filtroCapturado(principal(admin), entrada);

        assertThat(aplicado).isEqualTo(entrada);
    }

    @Test
    void solicitanteSempreRestritoAoProprioIdMesmoQueOFiltroTenteOutro() {
        ChamadoFiltro tentativa = ChamadoFiltro.vazio().comSolicitante(5L);

        ChamadoFiltro aplicado = filtroCapturado(principal(ana), tentativa);

        assertThat(aplicado.solicitanteId()).isEqualTo(4L);
    }

    @Test
    void tecnicoRecebeOEscopoDeFilaAtribuidosEProprios() {
        ChamadoFiltro aplicado = filtroCapturado(principal(carlos), ChamadoFiltro.vazio());

        assertThat(aplicado.escopoTecnicoId()).isEqualTo(2L);
    }

    @Test
    void ordenacaoPadraoEPorCriadoEmDecrescenteComDesempatePorId() {
        when(chamados.buscar(any(ChamadoFiltro.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listar(ChamadoFiltro.vazio(), null, null, 2, 50, principal(admin));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(chamados).buscar(any(ChamadoFiltro.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("criadoEm: DESC,id: DESC");
    }

    @Test
    void ordenacaoEDirecaoInformadasSaoRespeitadas() {
        when(chamados.buscar(any(ChamadoFiltro.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listar(ChamadoFiltro.vazio(), "titulo", "asc", 0, 20, principal(admin));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(chamados).buscar(any(ChamadoFiltro.class), pageable.capture());
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("titulo: ASC,id: DESC");
    }

    @Test
    void campoDeOrdenacaoForaDaListaPermitidaEhRecusado() {
        assertThatThrownBy(() -> service.listar(ChamadoFiltro.vazio(), "senhaHash", "asc", 0, 20, principal(admin)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Campo de ordenação inválido.");

        verifyNoInteractions(chamados);
    }

    @Test
    void periodoComInicioPosteriorAoFimEhRecusado() {
        Instant inicio = AGORA;
        ChamadoFiltro invertido = new ChamadoFiltro(null, null, null, null, null, null, false, false,
                inicio, inicio.minusSeconds(1));

        assertThatThrownBy(() -> service.listar(invertido, null, null, 0, 20, principal(admin)))
                .isInstanceOf(RegraNegocioException.class);

        verifyNoInteractions(chamados);
    }

    @Test
    void periodoComInicioIgualAoFimEhAceito() {
        when(chamados.buscar(any(ChamadoFiltro.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        ChamadoFiltro igual = new ChamadoFiltro(null, null, null, null, null, null, false, false, AGORA, AGORA);

        PaginaResponse<ChamadoResumo> pagina = service.listar(igual, null, null, 0, 20, principal(admin));

        assertThat(pagina.conteudo()).isEmpty();
    }

    // ---------- detalhe ----------

    @Test
    void buscarDevolveOChamadoVisivelComASituacaoDoSla() {
        Chamado c = chamado(7L, ana, carlos, StatusChamado.EM_ATENDIMENTO);
        c.setSlaResolucaoLimite(AGORA.minusSeconds(60));
        when(chamados.findDetalheById(7L)).thenReturn(Optional.of(c));

        ChamadoResponse r = service.buscar(7L, principal(ana));

        assertThat(r.id()).isEqualTo(7L);
        assertThat(r.protocolo()).isEqualTo("CT-2026-000007");
        assertThat(r.situacaoSlaResolucao()).isEqualTo(SituacaoSla.ATRASADO);
    }

    @Test
    void buscarChamadoForaDaVisibilidadeResponde404() {
        when(chamados.findDetalheById(7L)).thenReturn(Optional.of(chamado(7L, ana, carlos, StatusChamado.ABERTO)));

        assertThatThrownBy(() -> service.buscar(7L, principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Chamado não encontrado.");
    }

    @Test
    void buscarChamadoInexistenteResponde404() {
        when(chamados.findDetalheById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(404L, principal(admin)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
