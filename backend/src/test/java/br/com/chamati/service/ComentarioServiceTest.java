package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.chamado;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.ComentarioCriarRequest;
import br.com.chamati.dto.ComentarioResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Comentario;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.AcessoNegadoException;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.ComentarioRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ComentarioServiceTest {

    static final Long ID = 100L;

    @Mock ChamadoRepository chamados;
    @Mock UsuarioRepository usuarios;
    @Mock ComentarioRepository comentarios;
    @Mock HistoricoChamadoRepository historico;

    ComentarioService service;

    Usuario admin = usuario(1L, "Admin", Perfil.ADMIN);
    Usuario carlos = usuario(2L, "Carlos Mendes", Perfil.TECNICO);
    Usuario ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
    Usuario bruno = usuario(5L, "Bruno Costa", Perfil.SOLICITANTE);

    Chamado chamado;

    @BeforeEach
    void preparar() {
        service = new ComentarioService(chamados, usuarios, comentarios, historico, new AcompanhamentoMapper(),
                Fixtures.relogio());
        chamado = chamado(ID, ana, carlos, StatusChamado.EM_ATENDIMENTO);
        lenient().when(chamados.findDetalheById(ID)).thenAnswer(inv -> Optional.ofNullable(chamado));
        for (Usuario u : new Usuario[]{admin, carlos, ana, bruno}) {
            lenient().when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    private static ComentarioCriarRequest publico(String texto) {
        return new ComentarioCriarRequest(TipoComentario.PUBLICO, texto);
    }

    private static ComentarioCriarRequest nota(String texto) {
        return new ComentarioCriarRequest(TipoComentario.NOTA_INTERNA, texto);
    }

    private HistoricoChamado eventoGravado() {
        ArgumentCaptor<HistoricoChamado> captor = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historico).save(captor.capture());
        return captor.getValue();
    }

    private void nadaFoiGravado() {
        verify(chamados, never()).saveAndFlush(any());
        verifyNoInteractions(comentarios, historico);
    }

    // =====================================================================
    // criar
    // =====================================================================

    @Test
    void primeiroComentarioPublicoDoTecnicoRegistraAPrimeiraRespostaAntesDoComentario() {
        ComentarioResponse r = service.criar(ID, publico("  Estamos verificando.  "), principal(carlos));

        assertThat(chamado.getPrimeiraRespostaEm()).isEqualTo(AGORA);
        InOrder ordem = inOrder(chamados, comentarios);
        ordem.verify(chamados).saveAndFlush(chamado);
        ordem.verify(comentarios).save(any(Comentario.class));

        ArgumentCaptor<Comentario> salvo = ArgumentCaptor.forClass(Comentario.class);
        verify(comentarios).save(salvo.capture());
        assertThat(salvo.getValue().getConteudo()).isEqualTo("Estamos verificando.");
        assertThat(salvo.getValue().getTipo()).isEqualTo(TipoComentario.PUBLICO);
        assertThat(salvo.getValue().getAutor()).isSameAs(carlos);
        assertThat(salvo.getValue().getChamado()).isSameAs(chamado);
        assertThat(salvo.getValue().getCriadoEm()).isEqualTo(AGORA);

        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.COMENTARIO);
        assertThat(e.getCampo()).isEqualTo("primeira_resposta_em");
        assertThat(e.getValorNovo()).isEqualTo(DescricaoReabertura.data(AGORA));
        assertThat(e.getDescricao()).isEqualTo("Comentário adicionado (primeira resposta)");

        assertThat(r.conteudo()).isEqualTo("Estamos verificando.");
        assertThat(r.autor().id()).isEqualTo(2L);
        assertThat(r.autor().perfil()).isEqualTo(Perfil.TECNICO);
    }

    @Test
    void adminTambemRegistraAPrimeiraResposta() {
        service.criar(ID, publico("Olá"), principal(admin));

        assertThat(chamado.getPrimeiraRespostaEm()).isEqualTo(AGORA);
    }

    @Test
    void segundaRespostaNaoAlteraAPrimeiraRespostaJaRegistrada() {
        java.time.Instant anterior = AGORA.minusSeconds(900);
        chamado.setPrimeiraRespostaEm(anterior);

        service.criar(ID, publico("Mais uma atualização"), principal(carlos));

        assertThat(chamado.getPrimeiraRespostaEm()).isEqualTo(anterior);
        verify(chamados, never()).saveAndFlush(any());
        HistoricoChamado e = eventoGravado();
        assertThat(e.getCampo()).isNull();
        assertThat(e.getDescricao()).isEqualTo("Comentário adicionado");
    }

    @Test
    void comentarioDoSolicitanteNaoContaComoPrimeiraResposta() {
        service.criar(ID, publico("Ainda sem acesso"), principal(ana));

        assertThat(chamado.getPrimeiraRespostaEm()).isNull();
        verify(chamados, never()).saveAndFlush(any());
        assertThat(eventoGravado().getTipoEvento()).isEqualTo(TipoEventoHistorico.COMENTARIO);
    }

    @Test
    void tecnicoQueEOProprioSolicitanteNaoRespondeASiMesmo() {
        chamado = chamado(ID, carlos, null, StatusChamado.ABERTO);

        service.criar(ID, publico("Abri e comento"), principal(carlos));

        assertThat(chamado.getPrimeiraRespostaEm()).isNull();
        verify(chamados, never()).saveAndFlush(any());
    }

    @Test
    void notaInternaNaoContaComoPrimeiraRespostaEGeraEventoProprio() {
        ComentarioResponse r = service.criar(ID, nota("Verificar o switch do 3º andar"), principal(carlos));

        assertThat(chamado.getPrimeiraRespostaEm()).isNull();
        verify(chamados, never()).saveAndFlush(any());
        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.NOTA_INTERNA);
        assertThat(e.getDescricao()).isEqualTo("Nota interna adicionada");
        assertThat(r.tipo()).isEqualTo(TipoComentario.NOTA_INTERNA);
    }

    @Test
    void solicitanteNaoCriaNotaInterna() {
        assertThatThrownBy(() -> service.criar(ID, nota("segredo"), principal(ana)))
                .isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    @Test
    void chamadoFechadoNaoRecebeComentario() {
        chamado.setStatus(StatusChamado.FECHADO);

        assertThatThrownBy(() -> service.criar(ID, publico("Oi"), principal(carlos)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechados");

        nadaFoiGravado();
    }

    @Test
    void chamadoResolvidoAindaRecebeComentario() {
        chamado.setStatus(StatusChamado.RESOLVIDO);

        service.criar(ID, publico("Obrigada!"), principal(ana));

        verify(comentarios).save(any(Comentario.class));
    }

    @Test
    void recusaDePermissaoDeNotaInternaVemAntesDaRegraDeChamadoFechado() {
        chamado.setStatus(StatusChamado.FECHADO);

        assertThatThrownBy(() -> service.criar(ID, nota("x"), principal(ana)))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void chamadoForaDaVisibilidadeOuInexistenteResponde404() {
        assertThatThrownBy(() -> service.criar(ID, publico("Oi"), principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        chamado = null;
        assertThatThrownBy(() -> service.criar(ID, publico("Oi"), principal(admin)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        nadaFoiGravado();
    }

    @Test
    void autorInativoOuInexistenteEhRecusado() {
        carlos.setAtivo(false);
        Usuario fantasma = usuario(77L, "Fantasma", Perfil.ADMIN);
        when(usuarios.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(ID, publico("Oi"), principal(carlos)))
                .isInstanceOf(AutenticacaoException.class);
        assertThatThrownBy(() -> service.criar(ID, publico("Oi"), principal(fantasma)))
                .isInstanceOf(AutenticacaoException.class);

        nadaFoiGravado();
    }

    @Test
    void corridaNaPrimeiraRespostaPropagaOConflitoSemGravarOComentario() {
        when(chamados.saveAndFlush(any(Chamado.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Chamado.class, ID));

        assertThatThrownBy(() -> service.criar(ID, publico("Oi"), principal(carlos)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verifyNoInteractions(comentarios, historico);
    }

    // =====================================================================
    // listar
    // =====================================================================

    private Comentario comentario(TipoComentario tipo, Usuario autor) {
        Comentario c = new Comentario();
        c.setChamado(chamado);
        c.setAutor(autor);
        c.setTipo(tipo);
        c.setConteudo("texto");
        c.setCriadoEm(AGORA);
        return c;
    }

    @Test
    void equipeSemFiltroDeTipoRecebeTodosOsComentarios() {
        Pageable[] usado = new Pageable[1];
        when(comentarios.findByChamadoId(eqId(), any(Pageable.class))).thenAnswer(inv -> {
            usado[0] = inv.getArgument(1);
            return new PageImpl<>(List.of(comentario(TipoComentario.PUBLICO, ana),
                    comentario(TipoComentario.NOTA_INTERNA, carlos)));
        });

        PaginaResponse<ComentarioResponse> pagina = service.listar(ID, null, null, 0, 20, principal(carlos));

        assertThat(pagina.conteudo()).extracting(ComentarioResponse::tipo)
                .containsExactly(TipoComentario.PUBLICO, TipoComentario.NOTA_INTERNA);
        assertThat(usado[0].getSort().toString()).isEqualTo("criadoEm: ASC,id: ASC");
        verify(comentarios, never()).findByChamadoIdAndTipo(any(), any(), any());
    }

    @Test
    void equipePodeFiltrarPorNotaInterna() {
        when(comentarios.findByChamadoIdAndTipo(eqId(), org.mockito.ArgumentMatchers.eq(TipoComentario.NOTA_INTERNA),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(comentario(TipoComentario.NOTA_INTERNA, carlos))));

        PaginaResponse<ComentarioResponse> pagina = service.listar(ID, TipoComentario.NOTA_INTERNA, "desc", 0, 20,
                principal(admin));

        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    void solicitanteSempreRecebeSomenteOsPublicosMesmoSemFiltro() {
        when(comentarios.findByChamadoIdAndTipo(eqId(), org.mockito.ArgumentMatchers.eq(TipoComentario.PUBLICO),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(comentario(TipoComentario.PUBLICO, carlos))));

        service.listar(ID, null, null, 0, 20, principal(ana));

        verify(comentarios, never()).findByChamadoId(any(), any());
    }

    @Test
    void solicitanteNaoPodeFiltrarPorNotaInterna() {
        assertThatThrownBy(() -> service.listar(ID, TipoComentario.NOTA_INTERNA, null, 0, 20, principal(ana)))
                .isInstanceOf(AcessoNegadoException.class);

        verifyNoInteractions(comentarios);
    }

    @Test
    void listarChamadoForaDaVisibilidadeResponde404() {
        assertThatThrownBy(() -> service.listar(ID, null, null, 0, 20, principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(comentarios);
    }

    private static Long eqId() {
        return org.mockito.ArgumentMatchers.eq(ID);
    }
}
