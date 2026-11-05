package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.chamado;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.AvaliacaoCriarRequest;
import br.com.chamati.dto.AvaliacaoResponse;
import br.com.chamati.dto.MediaTecnicoResponse;
import br.com.chamati.entity.Avaliacao;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.exception.AcessoNegadoException;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.AvaliacaoRepository;
import br.com.chamati.repository.ChamadoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    static final Long ID = 100L;

    @Mock ChamadoRepository chamados;
    @Mock AvaliacaoRepository avaliacoes;

    AvaliacaoService service;

    Usuario admin = usuario(1L, "Admin", Perfil.ADMIN);
    Usuario carlos = usuario(2L, "Carlos Mendes", Perfil.TECNICO);
    Usuario ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
    Usuario bruno = usuario(5L, "Bruno Costa", Perfil.SOLICITANTE);

    Chamado chamado;

    @BeforeEach
    void preparar() {
        service = new AvaliacaoService(chamados, avaliacoes, new AcompanhamentoMapper(), Fixtures.relogio());
        chamado = chamado(ID, ana, carlos, StatusChamado.RESOLVIDO);
        lenient().when(chamados.findDetalheById(ID)).thenAnswer(inv -> Optional.of(chamado));
    }

    private void nadaFoiSalvo() {
        verify(avaliacoes, never()).saveAndFlush(any());
    }

    @Test
    void solicitanteAvaliaChamadoResolvidoVinculandoOTecnicoResponsavel() {
        AvaliacaoResponse r = service.avaliar(ID, new AvaliacaoCriarRequest(5, "  Ótimo atendimento  "), principal(ana));

        ArgumentCaptor<Avaliacao> salva = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacoes).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getChamado()).isSameAs(chamado);
        assertThat(salva.getValue().getTecnico()).isSameAs(carlos);
        assertThat(salva.getValue().getNota()).isEqualTo(5);
        assertThat(salva.getValue().getComentario()).isEqualTo("Ótimo atendimento");
        assertThat(salva.getValue().getCriadoEm()).isEqualTo(AGORA);

        assertThat(r.chamadoId()).isEqualTo(ID);
        assertThat(r.protocolo()).isEqualTo("CT-2026-000100");
        assertThat(r.nota()).isEqualTo(5);
        assertThat(r.solicitante().id()).isEqualTo(4L);
        assertThat(r.tecnico().id()).isEqualTo(2L);
    }

    @Test
    void chamadoFechadoTambemPodeSerAvaliado() {
        chamado.setStatus(StatusChamado.FECHADO);

        service.avaliar(ID, new AvaliacaoCriarRequest(3, null), principal(ana));

        verify(avaliacoes).saveAndFlush(any());
    }

    @Test
    void comentarioEmBrancoOuAusenteFicaNulo() {
        service.avaliar(ID, new AvaliacaoCriarRequest(4, "   "), principal(ana));
        service.avaliar(ID, new AvaliacaoCriarRequest(4, null), principal(ana));

        ArgumentCaptor<Avaliacao> salva = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacoes, org.mockito.Mockito.times(2)).saveAndFlush(salva.capture());
        assertThat(salva.getAllValues()).extracting(Avaliacao::getComentario).containsOnlyNulls();
    }

    @Test
    void chamadoForaDaVisibilidadeOuInexistenteResponde404() {
        when(chamados.findDetalheById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.avaliar(999L, new AvaliacaoCriarRequest(5, null), principal(ana)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        nadaFoiSalvo();
    }

    @Test
    void quemEnxergaMasNaoESolicitanteRecebe403() {
        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(carlos)))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(admin)))
                .isInstanceOf(AcessoNegadoException.class);

        nadaFoiSalvo();
    }

    @Test
    void chamadoJaAvaliadoRecebe409() {
        when(avaliacoes.existsByChamadoId(ID)).thenReturn(true);

        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(ana)))
                .isInstanceOf(ConflitoException.class).hasMessage("Este chamado já foi avaliado.");

        nadaFoiSalvo();
    }

    @Test
    void conflitoDeUnicidadeSimultaneoVira409() {
        when(avaliacoes.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_avaliacoes_chamado"));

        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(ana)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void ordemDasVerificacoes403Antes409Antes422() {
        chamado.setStatus(StatusChamado.EM_ATENDIMENTO);
        when(avaliacoes.existsByChamadoId(ID)).thenReturn(true);

        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(carlos)))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(ana)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void chamadoNaoResolvidoRecebe422() {
        for (StatusChamado s : new StatusChamado[]{StatusChamado.ABERTO, StatusChamado.EM_ATENDIMENTO,
                StatusChamado.AGUARDANDO_USUARIO, StatusChamado.AGUARDANDO_TERCEIRO}) {
            chamado.setStatus(s);
            assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(ana)))
                    .as(s.name()).isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Só é possível avaliar chamados resolvidos.");
        }

        nadaFoiSalvo();
    }

    @Test
    void chamadoSemTecnicoRecebe422() {
        chamado.setTecnico(null);

        assertThatThrownBy(() -> service.avaliar(ID, new AvaliacaoCriarRequest(5, null), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("técnico");

        nadaFoiSalvo();
    }

    @Test
    void listaTodasOuFiltraPorTecnicoDaMaisRecenteParaAMaisAntiga() {
        when(avaliacoes.findAllBy(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(avaliacoes.findByTecnicoId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listar(null, 2, 10);
        service.listar(7L, 0, 20);

        ArgumentCaptor<Pageable> todas = ArgumentCaptor.forClass(Pageable.class);
        verify(avaliacoes).findAllBy(todas.capture());
        assertThat(todas.getValue().getPageNumber()).isEqualTo(2);
        assertThat(todas.getValue().getPageSize()).isEqualTo(10);
        assertThat(todas.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "criadoEm", "id"));
        verify(avaliacoes).findByTecnicoId(org.mockito.ArgumentMatchers.eq(7L), any(Pageable.class));
    }

    @Test
    void mediaPorTecnicoTemDuasCasasDecimais() {
        AvaliacaoRepository.MediaTecnico m = mock(AvaliacaoRepository.MediaTecnico.class);
        when(m.getTecnicoId()).thenReturn(2L);
        when(m.getTecnicoNome()).thenReturn("Carlos Mendes");
        when(m.getMedia()).thenReturn(13.0 / 3);
        when(m.getQuantidade()).thenReturn(3L);
        when(avaliacoes.mediasPorTecnico()).thenReturn(List.of(m));

        List<MediaTecnicoResponse> r = service.mediasPorTecnico();

        assertThat(r).containsExactly(new MediaTecnicoResponse(2L, "Carlos Mendes", 4.33, 3));
    }
}
