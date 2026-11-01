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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.AlterarPrioridadeRequest;
import br.com.chamati.dto.AlterarStatusRequest;
import br.com.chamati.dto.AtribuirTecnicoRequest;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.DesatribuirTecnicoRequest;
import br.com.chamati.dto.ReabrirChamadoRequest;
import br.com.chamati.dto.ResolverChamadoRequest;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    @Mock SlaService sla;

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
        service = new ChamadoGestaoService(chamados, usuarios, historico, sla, new ChamadoMapper(), Fixtures.relogio());
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

    // =====================================================================
    // alterar prioridade
    // =====================================================================

    private SlaService.Limites limitesApos(long primeiraRespostaMin, long resolucaoMin) {
        Instant criadoEm = chamado.getCriadoEm();
        return new SlaService.Limites(criadoEm.plus(Duration.ofMinutes(primeiraRespostaMin)),
                criadoEm.plus(Duration.ofMinutes(resolucaoMin)));
    }

    @Test
    void tecnicoResponsavelAlteraAPrioridadeERecalculaOSlaAPartirDaAbertura() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        Instant criadoEm = chamado.getCriadoEm();
        SlaService.Limites limites = limitesApos(60, 480);
        when(sla.calcularLimites(Prioridade.ALTA, criadoEm)).thenReturn(limites);

        ChamadoResponse r = service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, "  Impacto maior  ", null), pCarlos);

        assertThat(r.prioridade()).isEqualTo(Prioridade.ALTA);
        assertThat(chamado.getPrioridade()).isEqualTo(Prioridade.ALTA);
        assertThat(chamado.getSlaPrimeiraRespostaLimite()).isEqualTo(limites.primeiraResposta());
        assertThat(chamado.getSlaResolucaoLimite()).isEqualTo(limites.resolucao());
        assertThat(chamado.getCriadoEm()).isEqualTo(criadoEm);
        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        verify(chamados).saveAndFlush(chamado);
        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.ALTERACAO_PRIORIDADE);
        assertThat(e.getCampo()).isEqualTo("prioridade");
        assertThat(e.getValorAnterior()).isEqualTo("MEDIA");
        assertThat(e.getValorNovo()).isEqualTo("ALTA");
        assertThat(e.getDescricao()).isEqualTo("Impacto maior");
        assertThat(e.getAutor()).isSameAs(carlos);
    }

    @Test
    void adminAlteraAPrioridadeDeChamadoSemTecnicoESemMotivo() {
        dado(null, StatusChamado.ABERTO);
        when(sla.calcularLimites(Prioridade.CRITICA, chamado.getCriadoEm())).thenReturn(limitesApos(15, 240));

        ChamadoResponse r = service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.CRITICA, "   ", null), pAdmin);

        assertThat(r.prioridade()).isEqualTo(Prioridade.CRITICA);
        assertThat(eventoGravado().getDescricao()).isNull();
    }

    @Test
    void situacaoDoSlaAcompanhaOLimiteRecalculado() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        chamado.setSlaResolucaoLimite(chamado.getCriadoEm().plus(Duration.ofMinutes(1440)));
        // aberto há 60 minutos; o novo prazo de resolução (10 min) já passou
        when(sla.calcularLimites(Prioridade.CRITICA, chamado.getCriadoEm())).thenReturn(limitesApos(5, 10));

        ChamadoResponse r = service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.CRITICA, null, null), pAdmin);

        assertThat(r.situacaoSlaResolucao()).isEqualTo(SituacaoSla.ATRASADO);
    }

    @Test
    void prioridadeIgualAAtualEhRecusada() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.MEDIA, null, null), pCarlos))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O chamado já tem a prioridade MEDIA.");

        verifyNoInteractions(sla);
        nadaFoiGravado();
    }

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = {"RESOLVIDO", "FECHADO"})
    void naoSeAlteraAPrioridadeDeChamadoResolvidoOuFechado(StatusChamado status) {
        dado(carlos, status);

        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, null), pAdmin))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(chamado.getPrioridade()).isEqualTo(Prioridade.MEDIA);
        verifyNoInteractions(sla);
        nadaFoiGravado();
    }

    @Test
    void semPoliticaDeSlaParaANovaPrioridadeNadaEhAlterado() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        Instant limitePrimeira = chamado.getCriadoEm().plusSeconds(100);
        Instant limiteResolucao = chamado.getCriadoEm().plusSeconds(200);
        chamado.setSlaPrimeiraRespostaLimite(limitePrimeira);
        chamado.setSlaResolucaoLimite(limiteResolucao);
        when(sla.calcularLimites(Prioridade.ALTA, chamado.getCriadoEm()))
                .thenThrow(new RegraNegocioException("Não há política de SLA ativa para a prioridade ALTA."));

        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, null), pCarlos))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("política de SLA");

        assertThat(chamado.getPrioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(chamado.getSlaPrimeiraRespostaLimite()).isEqualTo(limitePrimeira);
        assertThat(chamado.getSlaResolucaoLimite()).isEqualTo(limiteResolucao);
        nadaFoiGravado();
    }

    @Test
    void soAdminOuOResponsavelAlteramAPrioridade() {
        dado(null, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, null), pAna))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, null), pFernanda))
                .isInstanceOf(AcessoNegadoException.class);

        verifyNoInteractions(sla);
        nadaFoiGravado();
    }

    @Test
    void chamadoForaDaVisibilidadeResponde404NaAlteracaoDePrioridade() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, null), pBruno))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        nadaFoiGravado();
    }

    @Test
    void ordemDasVerificacoesNaAlteracaoDePrioridade() {
        dado(carlos, StatusChamado.FECHADO);

        // 403 antes de 409 e 422
        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, 9L), pAna))
                .isInstanceOf(AcessoNegadoException.class);
        // 409 antes de 422
        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, 9L), pCarlos))
                .isInstanceOf(ConflitoException.class);
        // versão correta: aparece a regra de negócio
        assertThatThrownBy(() -> service.alterarPrioridade(ID,
                new AlterarPrioridadeRequest(Prioridade.ALTA, null, 0L), pCarlos))
                .isInstanceOf(RegraNegocioException.class);

        nadaFoiGravado();
    }

    // =====================================================================
    // desatribuir técnico
    // =====================================================================

    private List<HistoricoChamado> eventosGravados(int quantidade) {
        ArgumentCaptor<HistoricoChamado> captor = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historico, times(quantidade)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void adminRemoveOTecnicoEODevolveAFilaPreservandoPrimeiraRespostaESla() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);
        Instant atribuidoEm = AGORA.minusSeconds(3000);
        Instant primeiraResposta = AGORA.minusSeconds(2800);
        Instant limitePrimeira = AGORA.plusSeconds(100);
        Instant limiteResolucao = AGORA.plusSeconds(200);
        chamado.setAtribuidoEm(atribuidoEm);
        chamado.setPrimeiraRespostaEm(primeiraResposta);
        chamado.setSlaPrimeiraRespostaLimite(limitePrimeira);
        chamado.setSlaResolucaoLimite(limiteResolucao);

        ChamadoResponse r = service.desatribuirTecnico(ID, new DesatribuirTecnicoRequest("  Técnico em férias  ", null), pAdmin);

        assertThat(chamado.getTecnico()).isNull();
        assertThat(chamado.getAtribuidoEm()).isNull();
        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(chamado.getSolicitante()).isSameAs(ana);
        assertThat(chamado.getPrimeiraRespostaEm()).isEqualTo(primeiraResposta);
        assertThat(chamado.getSlaPrimeiraRespostaLimite()).isEqualTo(limitePrimeira);
        assertThat(chamado.getSlaResolucaoLimite()).isEqualTo(limiteResolucao);
        assertThat(r.tecnico()).isNull();
        assertThat(r.status()).isEqualTo(StatusChamado.ABERTO);
        verify(chamados).saveAndFlush(chamado);

        List<HistoricoChamado> eventos = eventosGravados(2);
        HistoricoChamado remocao = eventos.get(0);
        assertThat(remocao.getTipoEvento()).isEqualTo(TipoEventoHistorico.ATRIBUICAO);
        assertThat(remocao.getCampo()).isEqualTo("tecnico");
        assertThat(remocao.getValorAnterior()).isEqualTo("Carlos Mendes");
        assertThat(remocao.getValorNovo()).isNull();
        assertThat(remocao.getDescricao()).isEqualTo("Técnico responsável removido: Técnico em férias");
        assertThat(remocao.getAutor()).isSameAs(admin);
        HistoricoChamado status = eventos.get(1);
        assertThat(status.getTipoEvento()).isEqualTo(TipoEventoHistorico.ALTERACAO_STATUS);
        assertThat(status.getValorAnterior()).isEqualTo("EM_ATENDIMENTO");
        assertThat(status.getValorNovo()).isEqualTo("ABERTO");
        assertThat(status.getDescricao()).isEqualTo("Chamado devolvido à fila");
    }

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = {"AGUARDANDO_USUARIO", "AGUARDANDO_TERCEIRO"})
    void chamadoAguardandoTambemVoltaParaAFila(StatusChamado de) {
        dado(carlos, de);

        service.desatribuirTecnico(ID, new DesatribuirTecnicoRequest(null, null), pAdmin);

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(chamado.getTecnico()).isNull();
        assertThat(eventosGravados(2).get(1).getValorAnterior()).isEqualTo(de.name());
    }

    @Test
    void chamadoAbertoComTecnicoPerdeSoOTecnicoEGravaApenasOEventoDeAtribuicao() {
        dado(carlos, StatusChamado.ABERTO);
        chamado.setAtribuidoEm(AGORA.minusSeconds(60));

        service.desatribuirTecnico(ID, null, pAdmin);

        assertThat(chamado.getTecnico()).isNull();
        assertThat(chamado.getAtribuidoEm()).isNull();
        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.ABERTO);
        HistoricoChamado e = eventoGravado();
        assertThat(e.getTipoEvento()).isEqualTo(TipoEventoHistorico.ATRIBUICAO);
        assertThat(e.getDescricao()).isEqualTo("Técnico responsável removido");
    }

    @Test
    void motivoEmBrancoNaoEntraNaDescricao() {
        dado(carlos, StatusChamado.ABERTO);

        service.desatribuirTecnico(ID, new DesatribuirTecnicoRequest("   ", null), pAdmin);

        assertThat(eventoGravado().getDescricao()).isEqualTo("Técnico responsável removido");
    }

    @Test
    void somenteAdminRemoveOTecnico() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.desatribuirTecnico(ID, null, pCarlos)).isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("administradores");
        assertThatThrownBy(() -> service.desatribuirTecnico(ID, null, pAna)).isInstanceOf(AcessoNegadoException.class);

        assertThat(chamado.getTecnico()).isSameAs(carlos);
        nadaFoiGravado();
    }

    @Test
    void tecnicoDaFilaTambemNaoRemoveTecnico() {
        dado(null, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.desatribuirTecnico(ID, null, pFernanda)).isInstanceOf(AcessoNegadoException.class);

        nadaFoiGravado();
    }

    @Test
    void chamadoSemTecnicoNaoPodeSerDesatribuido() {
        dado(null, StatusChamado.ABERTO);

        assertThatThrownBy(() -> service.desatribuirTecnico(ID, null, pAdmin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O chamado não tem técnico atribuído.");

        nadaFoiGravado();
    }

    @ParameterizedTest
    @EnumSource(value = StatusChamado.class, names = {"RESOLVIDO", "FECHADO"})
    void chamadoResolvidoOuFechadoMantemOResponsavel(StatusChamado status) {
        dado(carlos, status);

        assertThatThrownBy(() -> service.desatribuirTecnico(ID, null, pAdmin)).isInstanceOf(RegraNegocioException.class);

        assertThat(chamado.getTecnico()).isSameAs(carlos);
        assertThat(chamado.getStatus()).isEqualTo(status);
        nadaFoiGravado();
    }

    @Test
    void chamadoForaDaVisibilidadeResponde404NaDesatribuicao() {
        dado(carlos, StatusChamado.EM_ATENDIMENTO);

        assertThatThrownBy(() -> service.desatribuirTecnico(ID, null, pBruno))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        nadaFoiGravado();
    }

    @Test
    void ordemDasVerificacoesNaDesatribuicao() {
        dado(null, StatusChamado.ABERTO);

        // 403 antes de 409 e 422
        assertThatThrownBy(() -> service.desatribuirTecnico(ID, new DesatribuirTecnicoRequest(null, 9L), pCarlos))
                .isInstanceOf(AcessoNegadoException.class);
        // 409 antes de 422
        assertThatThrownBy(() -> service.desatribuirTecnico(ID, new DesatribuirTecnicoRequest(null, 9L), pAdmin))
                .isInstanceOf(ConflitoException.class);
        // versão correta: aparece a regra de negócio
        assertThatThrownBy(() -> service.desatribuirTecnico(ID, new DesatribuirTecnicoRequest(null, 0L), pAdmin))
                .isInstanceOf(RegraNegocioException.class);

        nadaFoiGravado();
    }
}
