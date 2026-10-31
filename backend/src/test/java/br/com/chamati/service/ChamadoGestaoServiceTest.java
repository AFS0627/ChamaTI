package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.chamado;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.AlterarStatusRequest;
import br.com.chamati.dto.AtribuirTecnicoRequest;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.ReabrirChamadoRequest;
import br.com.chamati.dto.ResolverChamadoRequest;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.SituacaoSla;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.AcessoNegadoException;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.ChamadoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChamadoGestaoServiceTest {

    static final Long ID = 100L;

    @Mock ChamadoRepository chamados;
    @Mock UsuarioRepository usuarios;
    @Mock HistoricoChamadoRepository historico;

    ChamadoGestaoService service;

    Usuario admin = usuario(1L, "Admin", Perfil.ADMIN);
    Usuario carlos = usuario(2L, "Carlos Mendes", Perfil.TECNICO);
    Usuario fernanda = usuario(3L, "Fernanda Lima", Perfil.TECNICO);
    Usuario ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
    Usuario bruno = usuario(5L, "Bruno Costa", Perfil.SOLICITANTE);

    UsuarioPrincipal pAdmin = principal(admin);
    UsuarioPrincipal pCarlos = principal(carlos);
    UsuarioPrincipal pFernanda = principal(fernanda);
    UsuarioPrincipal pAna = principal(ana);
    UsuarioPrincipal pBruno = principal(bruno);

    Chamado chamado;

    @BeforeEach
    void preparar() {
        service = new ChamadoGestaoService(chamados, usuarios, historico, new ChamadoMapper(), Fixtures.relogio());
        lenient().when(chamados.findDetalheById(ID)).thenAnswer(inv -> Optional.ofNullable(chamado));
        for (Usuario u : new Usuario[]{admin, carlos, fernanda, ana, bruno}) {
            lenient().when(usuarios.getReferenceById(u.getId())).thenReturn(u);
        }
    }

    /** Chamado da Ana, com o técnico e o status informados. */
    private Chamado dado(Usuario tecnico, StatusChamado status) {
        chamado = chamado(ID, ana, tecnico, status);
        return chamado;
    }

    private HistoricoChamado eventoGravado() {
        ArgumentCaptor<HistoricoChamado> captor = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historico).save(captor.capture());
        return captor.getValue();
    }

    private void nadaFoiGravado() {
        verify(chamados, never()).saveAndFlush(any());
        verifyNoInteractions(historico);
    }

    // =====================================================================
    // atribuir técnico
    // =====================================================================

    @Test
    void adminAtribuiTecnicoAChamadoSemResponsavel() {
        dado(null, StatusChamado.ABERTO);
        when(usuarios.findByIdAndPerfilAndAtivoTrue(2L, Perfil.TECNICO)).thenReturn(Optional.of(carlos));

        ChamadoResponse r = service.atribuirTecnico(ID, new AtribuirTecnicoRequest(2L, null), pAdmin);

        assertThat(chamado.getTecnico()).isSameAs(carlos);
        assertThat(chamado.getAtribuidoEm()).isEqualTo(AGORA);
        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(r.tecnico().id()).isEqualTo(2L);
        verify(chamados).saveAndFlush(chamado);
        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.ATRIBUICAO);
        assertThat(e.getCampo()).isEqualTo("tecnico");
        assertThat(e.getValorAnterior()).isNull();
        assertThat(e.getValorNovo()).isEqualTo("Carlos Mendes");
        assertThat(e.getAutor()).isSameAs(admin);
    }

    @Test
    void adminTrocaOTecnicoEOHistoricoGuardaOAnterior() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        when(usuarios.findByIdAndPerfilAndAtivoTrue(3L, Perfil.TECNICO)).thenReturn(Optional.of(fernanda));

        service.atribuirTecnico(ID, new AtribuirTecnicoRequest(3L, null), pAdmin);

        assertThat(chamado.getTecnico()).isSameAs(fernanda);
        HistoricoChamado e = eventoGravado();
        assertThat(e.getValorAnterior()).isEqualTo("Carlos Mendes");
        assertThat(e.getValorNovo()).isEqualTo("Fernanda Lima");
        assertThat(e.getDescricao()).isEqualTo("Técnico responsável alterado");
    }

    @Test
    void tecnicoPodeAssumirParaSiUmChamadoDaFila() {
        dado(null, StatusChamado.ABERTO);
        when(usuarios.findByIdAndPerfilAndAtivoTrue(3L, Perfil.TECNICO)).thenReturn(Optional.of(fernanda));

        ChamadoResponse r = service.atribuirTecnico(ID, new AtribuirTecnicoRequest(3L, null), pFernanda);

        assertThat(r.tecnico().id()).isEqualTo(3L);
        assertThat(eventoGravado().getAutor()).isSameAs(fernanda);
    }

    @Test
    void tecnicoNaoPodeAtribuirOChamadoAOutroTecnico() {
        dado(null, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.atribuirTecnico(ID, new AtribuirTecnicoRequest(2L, null), pFernanda))
                .isInstanceOf(AcessoNegadoException.class);

        verify(usuarios, never()).findByIdAndPerfilAndAtivoTrue(anyLong(), any());
        nadaFoiGravado();
    }

    @Test
    void tecnicoNaoPodeTrocarOResponsavelDeChamadoJaAtribuido() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.atribuirTecnico(ID, new AtribuirTecnicoRequest(2L, null), pCarlos))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("administradores");

        nadaFoiGravado();
    }

    @Test
    void solicitanteNaoAtribuiTecnico() {
        dado(null, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.atribuirTecnico(ID, new AtribuirTecnicoRequest(2L, null), pAna))
                .isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    @Test
    void tecnicoInexistenteInativoOuSemPerfilDeTecnicoEhRecusado() {
        dado(null, StatusChamado.ABERTO);
        when(usuarios.findByIdAndPerfilAndAtivoTrue(4L, Perfil.TECNICO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atribuirTecnico(ID, new AtribuirTecnicoRequest(4L, null), pAdmin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não existe, está inativo ou não tem perfil de técnico");

        nadaFoiGravado();
    }

    @Test
    void atribuirAoMesmoTecnicoJaResponsavelEhRecusado() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        when(usuarios.findByIdAndPerfilAndAtivoTrue(2L, Perfil.TECNICO)).thenReturn(Optional.of(carlos));

        assertThatThrownBy(() -> service.atribuirTecnico(ID, new AtribuirTecnicoRequest(2L, null), pAdmin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já está atribuído");

        nadaFoiGravado();
    }

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = {"RESOLVIDO", "FECHADO"})
    void naoSeAtribuiTecnicoAChamadoResolvidoOuFechado(StatusChamado status) {
        dado(carlos, status);

        assertThatThrownBy(() -> service.atribuirTecnico(ID, new AtribuirTecnicoRequest(3L, null), pAdmin))
                .isInstanceOf(RegraNegocioException.class);

        nadaFoiGravado();
    }

    // =====================================================================
    // alterar status
    // =====================================================================

    @Test
    void tecnicoResponsavelAlteraOStatusERegistraAObservacaoNoHistorico() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        ChamadoResponse r = service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.AGUARDANDO_USUARIO, "  aguardando retorno  ", null), pCarlos);

        assertThat(r.status()).isEqualTo(StatusChamado.AGUARDANDO_USUARIO);
        verify(chamados).saveAndFlush(chamado);
        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.ALTERACAO_STATUS);
        assertThat(e.getCampo()).isEqualTo("status");
        assertThat(e.getValorAnterior()).isEqualTo("EM_ATENDIMENTO");
        assertThat(e.getValorNovo()).isEqualTo("AGUARDANDO_USUARIO");
        assertThat(e.getDescricao()).isEqualTo("aguardando retorno");
    }

    @Test
    void observacaoEmBrancoNaoViraDescricao() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        service.alterarStatus(ID, new AlterarStatusRequest(StatusChamado.AGUARDANDO_TERCEIRO, "   ", null), pCarlos);

        assertThat(eventoGravado().getDescricao()).isNull();
    }

    @Test
    void adminIniciaOAtendimentoDeChamadoAbertoComTecnico() {
        dado(carlos, StatusChamado.ABERTO);

        ChamadoResponse r = service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.EM_ATENDIMENTO, null, null), pAdmin);

        assertThat(r.status()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
    }

    @Test
    void naoSeIniciaOAtendimentoSemTecnicoAtribuido() {
        dado(null, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.EM_ATENDIMENTO, null, null), pAdmin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Atribua um técnico");

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.ABERTO);
        nadaFoiGravado();
    }

    @Test
    void tecnicoQueNaoEOResponsavelNaoAlteraOStatus() {
        dado(null, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.EM_ATENDIMENTO, null, null), pFernanda))
                .isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    @Test
    void solicitanteNaoAlteraOStatus() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.AGUARDANDO_USUARIO, null, null), pAna))
                .isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    @Test
    void resolverEFecharTemOperacoesProprias() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.RESOLVIDO, null, null), pCarlos))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("resolução");
        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.FECHADO, null, null), pCarlos))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("fechamento");

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        nadaFoiGravado();
    }

    @Test
    void transicaoInvalidaEhRecusadaComOParDeStatusNaMensagem() {
        dado(carlos, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.AGUARDANDO_USUARIO, null, null), pCarlos))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Transição de status inválida: ABERTO → AGUARDANDO_USUARIO.");
        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.ABERTO, null, null), pCarlos))
                .isInstanceOf(RegraNegocioException.class);

        nadaFoiGravado();
    }

    // =====================================================================
    // resolver
    // =====================================================================

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = {"EM_ATENDIMENTO", "AGUARDANDO_USUARIO", "AGUARDANDO_TERCEIRO"})
    void tecnicoResponsavelResolveAPartirDeQualquerEstadoEmAndamento(StatusChamado de) {
        dado(carlos, de);

        ChamadoResponse r = service.resolver(ID, new ResolverChamadoRequest("  Reiniciado o serviço.  ", null), pCarlos);

        assertThat(r.status()).isEqualTo(StatusChamado.RESOLVIDO);
        assertThat(chamado.getResolvidoEm()).isEqualTo(AGORA);
        assertThat(chamado.getSolucao()).isEqualTo("Reiniciado o serviço.");
        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.RESOLUCAO);
        assertThat(e.getValorAnterior()).isEqualTo(de.name());
        assertThat(e.getValorNovo()).isEqualTo("RESOLVIDO");
    }

    @Test
    void resolverDentroDoPrazoMarcaOSlaDeResolucaoComoDentroDoPrazo() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        Instant limite = AGORA.plusSeconds(600);
        chamado.setSlaResolucaoLimite(limite);

        ChamadoResponse r = service.resolver(ID, new ResolverChamadoRequest("ok", null), pAdmin);

        assertThat(r.situacaoSlaResolucao()).isEqualTo(SituacaoSla.DENTRO_DO_PRAZO);
        assertThat(chamado.getSlaResolucaoLimite()).isEqualTo(limite);
    }

    @Test
    void resolverDepoisDoLimiteMarcaOSlaComoAtrasado() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        chamado.setSlaResolucaoLimite(AGORA.minusSeconds(600));

        ChamadoResponse r = service.resolver(ID, new ResolverChamadoRequest("ok", null), pAdmin);

        assertThat(r.situacaoSlaResolucao()).isEqualTo(SituacaoSla.ATRASADO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = {"ABERTO", "RESOLVIDO", "FECHADO"})
    void naoSeResolveChamadoAbertoResolvidoOuFechado(StatusChamado de) {
        dado(carlos, de);

        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", null), pCarlos))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(chamado.getResolvidoEm()).isNull();
        nadaFoiGravado();
    }

    @Test
    void naoSeResolveChamadoSemTecnico() {
        dado(null, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", null), pAdmin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Atribua um técnico");

        nadaFoiGravado();
    }

    @Test
    void soAdminOuOResponsavelResolvem() {
        dado(null, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", null), pFernanda))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", null), pAna))
                .isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    // =====================================================================
    // fechar
    // =====================================================================

    @Test
    void solicitanteFechaChamadoResolvidoPreservandoADataDeResolucao() {
        dado(carlos, StatusChamado.RESOLVIDO);
        Instant resolvidoEm = AGORA.minusSeconds(1800);
        chamado.setResolvidoEm(resolvidoEm);

        ChamadoResponse r = service.fechar(ID, null, pAna);

        assertThat(r.status()).isEqualTo(StatusChamado.FECHADO);
        assertThat(chamado.getFechadoEm()).isEqualTo(AGORA);
        assertThat(chamado.getResolvidoEm()).isEqualTo(resolvidoEm);
        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.ENCERRAMENTO);
        assertThat(e.getValorAnterior()).isEqualTo("RESOLVIDO");
        assertThat(e.getValorNovo()).isEqualTo("FECHADO");
    }

    @Test
    void adminTambemPodeFechar() {
        dado(carlos, StatusChamado.RESOLVIDO);

        assertThat(service.fechar(ID, 0L, pAdmin).status()).isEqualTo(StatusChamado.FECHADO);
    }

    @Test
    void tecnicoResponsavelNaoFechaOChamado() {
        dado(carlos, StatusChamado.RESOLVIDO);

        assertThatThrownBy(() -> service.fechar(ID, null, pCarlos)).isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = "RESOLVIDO", mode = EnumSource.Mode.EXCLUDE)
    void soSeFechaChamadoResolvido(StatusChamado de) {
        dado(carlos, de);

        assertThatThrownBy(() -> service.fechar(ID, null, pAna)).isInstanceOf(RegraNegocioException.class);

        assertThat(chamado.getFechadoEm()).isNull();
        nadaFoiGravado();
    }

    // =====================================================================
    // reabrir
    // =====================================================================

    @Test
    void reaberturaDeResolvidoComTecnicoVoltaParaEmAtendimentoSemMexerNoSla() {
        dado(carlos, StatusChamado.RESOLVIDO);
        Instant resolvidoEm = AGORA.minusSeconds(7200);
        Instant limitePrimeira = AGORA.minusSeconds(9000);
        Instant limiteResolucao = AGORA.plusSeconds(9000);
        chamado.setResolvidoEm(resolvidoEm);
        chamado.setSolucao("Solução anterior");
        chamado.setTotalReaberturas(1);
        chamado.setSlaPrimeiraRespostaLimite(limitePrimeira);
        chamado.setSlaResolucaoLimite(limiteResolucao);

        ChamadoResponse r = service.reabrir(ID, new ReabrirChamadoRequest("O problema voltou.", null), pAna);

        assertThat(r.status()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        assertThat(chamado.getResolvidoEm()).isNull();
        assertThat(chamado.getFechadoEm()).isNull();
        assertThat(chamado.getReabertoEm()).isEqualTo(AGORA);
        assertThat(chamado.getTotalReaberturas()).isEqualTo(2);
        assertThat(chamado.getSolucao()).isEqualTo("Solução anterior");
        assertThat(chamado.getSlaPrimeiraRespostaLimite()).isEqualTo(limitePrimeira);
        assertThat(chamado.getSlaResolucaoLimite()).isEqualTo(limiteResolucao);

        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.REABERTURA);
        assertThat(e.getValorAnterior()).isEqualTo("RESOLVIDO");
        assertThat(e.getValorNovo()).isEqualTo("EM_ATENDIMENTO");
        assertThat(e.getDescricao()).isEqualTo(DescricaoReabertura.formatar("O problema voltou.", resolvidoEm, null));
    }

    @Test
    void reaberturaDeFechadoSemTecnicoVoltaParaAbertoEGuardaAsDuasDatasAnteriores() {
        dado(null, StatusChamado.FECHADO);
        Instant resolvidoEm = AGORA.minusSeconds(7200);
        Instant fechadoEm = AGORA.minusSeconds(3600);
        chamado.setResolvidoEm(resolvidoEm);
        chamado.setFechadoEm(fechadoEm);

        ChamadoResponse r = service.reabrir(ID, new ReabrirChamadoRequest("Voltou", null), pAdmin);

        assertThat(r.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(chamado.getTotalReaberturas()).isEqualTo(1);
        assertThat(eventoGravado().getDescricao())
                .isEqualTo(DescricaoReabertura.formatar("Voltou", resolvidoEm, fechadoEm));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "responsavel", "solicitante"})
    void adminResponsavelESolicitantePodemReabrir(String quem) {
        dado(carlos, StatusChamado.RESOLVIDO);
        UsuarioPrincipal ator = switch (quem) {
            case "admin" -> pAdmin;
            case "responsavel" -> pCarlos;
            default -> pAna;
        };

        assertThat(service.reabrir(ID, new ReabrirChamadoRequest("Motivo", null), ator).status())
                .isEqualTo(StatusChamado.EM_ATENDIMENTO);
    }

    @Test
    void tecnicoQueNaoEOResponsavelNaoReabre() {
        dado(null, StatusChamado.RESOLVIDO);

        assertThatThrownBy(() -> service.reabrir(ID, new ReabrirChamadoRequest("Motivo", null), pFernanda))
                .isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = {"RESOLVIDO", "FECHADO"}, mode = EnumSource.Mode.EXCLUDE)
    void soSeReabreChamadoResolvidoOuFechado(StatusChamado de) {
        dado(carlos, de);

        assertThatThrownBy(() -> service.reabrir(ID, new ReabrirChamadoRequest("Motivo", null), pAdmin))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(chamado.getTotalReaberturas()).isZero();
        nadaFoiGravado();
    }

    // =====================================================================
    // regras comuns: visibilidade, versão e ordem das verificações
    // =====================================================================

    @Test
    void chamadoInexistenteResponde404() {
        chamado = null;

        assertThatThrownBy(() -> service.fechar(ID, null, pAdmin)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void chamadoForaDaVisibilidadeResponde404EmVezDe403() {
        dado(carlos, StatusChamado.RESOLVIDO);

        assertThatThrownBy(() -> service.fechar(ID, null, pBruno)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.reabrir(ID, new ReabrirChamadoRequest("x", null), pBruno))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atribuirTecnico(ID, new AtribuirTecnicoRequest(2L, null), pBruno))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        nadaFoiGravado();
    }

    @Test
    void versaoDesatualizadaGeraConflito() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", 5L), pCarlos))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("alterado por outra pessoa");
        assertThatThrownBy(() -> service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.AGUARDANDO_USUARIO, null, 5L), pCarlos))
                .isInstanceOf(ConflitoException.class);

        nadaFoiGravado();
    }

    @Test
    void versaoIgualAAtualEAceitaEVersaoOmitidaNaoEVerificada() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThat(service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.AGUARDANDO_USUARIO, null, 0L), pCarlos).status())
                .isEqualTo(StatusChamado.AGUARDANDO_USUARIO);
        assertThat(service.alterarStatus(ID,
                new AlterarStatusRequest(StatusChamado.AGUARDANDO_TERCEIRO, null, null), pCarlos).status())
                .isEqualTo(StatusChamado.AGUARDANDO_TERCEIRO);
    }

    @Test
    void permissaoVemAntesDaVersaoEAVersaoAntesDasRegrasDeNegocio() {
        dado(carlos, StatusChamado.FECHADO);

        // solicitante sem permissão de resolver, mesmo com versão errada e status inválido → 403
        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", 9L), pAna))
                .isInstanceOf(AcessoNegadoException.class);
        // com permissão e versão errada, o conflito vem antes do status inválido → 409
        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", 9L), pCarlos))
                .isInstanceOf(ConflitoException.class);
        // com permissão e versão correta, aparece a regra de negócio → 422
        assertThatThrownBy(() -> service.resolver(ID, new ResolverChamadoRequest("x", 0L), pCarlos))
                .isInstanceOf(RegraNegocioException.class);
    }
}
