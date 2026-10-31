package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.chamado;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.dto.HistoricoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
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
class HistoricoConsultaServiceTest {

    @Mock ChamadoRepository chamados;
    @Mock HistoricoChamadoRepository historico;
    HistoricoConsultaService service;

    Usuario admin = usuario(1L, "Admin", Perfil.ADMIN);
    Usuario carlos = usuario(2L, "Carlos Mendes", Perfil.TECNICO);
    Usuario ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
    Usuario bruno = usuario(5L, "Bruno Costa", Perfil.SOLICITANTE);
    Chamado chamado;

    @BeforeEach
    void preparar() {
        service = new HistoricoConsultaService(chamados, historico, new AcompanhamentoMapper());
        chamado = chamado(100L, ana, carlos, StatusChamado.EM_ATENDIMENTO);
    }

    private HistoricoChamado evento(TipoEventoHistorico tipo) {
        return new HistoricoChamado(chamado, carlos, tipo, null, null, null, "descrição");
    }

    @Test
    void equipeRecebeTodosOsEventosInclusiveNotasInternas() {
        when(chamados.findDetalheById(100L)).thenReturn(Optional.of(chamado));
        when(historico.findByChamadoId(eq(100L), any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(evento(TipoEventoHistorico.CRIACAO), evento(TipoEventoHistorico.NOTA_INTERNA))));

        PaginaResponse<HistoricoResponse> pagina = service.listar(100L, null, 0, 20, principal(carlos));

        assertThat(pagina.conteudo()).extracting(HistoricoResponse::tipoEvento)
                .containsExactly(TipoEventoHistorico.CRIACAO, TipoEventoHistorico.NOTA_INTERNA);
        verify(historico, never()).findByChamadoIdAndTipoEventoNot(any(), any(), any());
    }

    @Test
    void solicitanteNuncaRecebeEventosDeNotaInterna() {
        when(chamados.findDetalheById(100L)).thenReturn(Optional.of(chamado));
        when(historico.findByChamadoIdAndTipoEventoNot(eq(100L), eq(TipoEventoHistorico.NOTA_INTERNA),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(evento(TipoEventoHistorico.CRIACAO))));

        PaginaResponse<HistoricoResponse> pagina = service.listar(100L, null, 0, 20, principal(ana));

        assertThat(pagina.conteudo()).hasSize(1);
        verify(historico, never()).findByChamadoId(any(), any());
    }

    @Test
    void ordenaPorDataEIdNaDirecaoPedida() {
        when(chamados.findDetalheById(100L)).thenReturn(Optional.of(chamado));
        when(historico.findByChamadoId(eq(100L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listar(100L, "desc", 1, 10, principal(admin));
        service.listar(100L, null, 0, 20, principal(admin));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(historico, org.mockito.Mockito.times(2)).findByChamadoId(eq(100L), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getSort().toString()).isEqualTo("criadoEm: DESC,id: DESC");
        assertThat(pageable.getAllValues().get(0).getPageNumber()).isEqualTo(1);
        assertThat(pageable.getAllValues().get(1).getSort().toString()).isEqualTo("criadoEm: ASC,id: ASC");
    }

    @Test
    void chamadoForaDaVisibilidadeOuInexistenteResponde404() {
        when(chamados.findDetalheById(100L)).thenReturn(Optional.of(chamado));
        when(chamados.findDetalheById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(100L, null, 0, 20, principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.listar(404L, null, 0, 20, principal(admin)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(historico);
    }
}
