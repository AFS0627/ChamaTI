package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.categoria;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.subcategoria;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.ChamadoCriarRequest;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.entity.Categoria;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Subcategoria;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.ChamadoMapper;
import br.com.chamati.repository.CategoriaRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.SubcategoriaRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChamadoServiceTest {

    @Mock ChamadoRepository chamados;
    @Mock CategoriaRepository categorias;
    @Mock SubcategoriaRepository subcategorias;
    @Mock UsuarioRepository usuarios;
    @Mock HistoricoChamadoRepository historico;
    @Mock ProtocoloService protocolos;
    @Mock SlaService sla;

    ChamadoService service;
    Usuario ana;
    Categoria rede;

    @BeforeEach
    void preparar() {
        service = new ChamadoService(chamados, categorias, subcategorias, usuarios, historico, protocolos, sla,
                new ChamadoMapper(), Fixtures.relogio());
        ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
        rede = categoria(3L, true);
    }

    private void solicitanteExiste(Usuario u) {
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
    }

    private void categoriaAtiva() {
        when(categorias.findById(3L)).thenReturn(Optional.of(rede));
    }

    private void gravacaoOk() {
        when(protocolos.gerar(AGORA)).thenReturn("CT-2026-000013");
        when(chamados.saveAndFlush(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ChamadoCriarRequest pedido(Prioridade prioridade, Long subcategoriaId) {
        return new ChamadoCriarRequest("  Sem acesso à VPN  ", "  Não conecto desde ontem.  ", 3L,
                subcategoriaId, prioridade);
    }

    @Test
    void abreChamadoComSolicitanteAutenticadoStatusAbertoProtocoloEHistoricoDeCriacao() {
        solicitanteExiste(ana);
        categoriaAtiva();
        Subcategoria vpn = subcategoria(8L, rede, true);
        when(subcategorias.findByIdAndCategoriaId(8L, 3L)).thenReturn(Optional.of(vpn));
        gravacaoOk();

        ChamadoResponse r = service.abrir(pedido(Prioridade.ALTA, 8L), principal(ana));

        ArgumentCaptor<Chamado> salvo = ArgumentCaptor.forClass(Chamado.class);
        verify(chamados).saveAndFlush(salvo.capture());
        Chamado c = salvo.getValue();
        assertThat(c.getTitulo()).isEqualTo("Sem acesso à VPN");
        assertThat(c.getDescricao()).isEqualTo("Não conecto desde ontem.");
        assertThat(c.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(c.getPrioridade()).isEqualTo(Prioridade.ALTA);
        assertThat(c.getSolicitante()).isSameAs(ana);
        assertThat(c.getTecnico()).isNull();
        assertThat(c.getCategoria()).isSameAs(rede);
        assertThat(c.getSubcategoria()).isSameAs(vpn);
        assertThat(c.getProtocolo()).isEqualTo("CT-2026-000013");
        assertThat(c.getCriadoEm()).isEqualTo(AGORA);

        ArgumentCaptor<HistoricoChamado> evento = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historico).save(evento.capture());
        assertThat(evento.getValue().getTipoEvento()).isEqualTo(TipoEventoHistorico.CRIACAO);
        assertThat(evento.getValue().getAutor()).isSameAs(ana);
        assertThat(evento.getValue().getChamado()).isSameAs(c);

        assertThat(r.protocolo()).isEqualTo("CT-2026-000013");
        assertThat(r.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(r.solicitante().id()).isEqualTo(4L);
        assertThat(r.subcategoria().id()).isEqualTo(8L);
    }

    @Test
    void calculaOsLimitesDeSlaAPartirDoCriadoEmAntesDeReservarOProtocolo() {
        solicitanteExiste(ana);
        categoriaAtiva();
        gravacaoOk();

        service.abrir(pedido(null, null), principal(ana));

        ArgumentCaptor<Chamado> aplicado = ArgumentCaptor.forClass(Chamado.class);
        InOrder ordem = inOrder(sla, protocolos, chamados);
        ordem.verify(sla).aplicarLimites(aplicado.capture());
        ordem.verify(protocolos).gerar(AGORA);
        ordem.verify(chamados).saveAndFlush(aplicado.getValue());
        assertThat(aplicado.getValue().getCriadoEm()).isEqualTo(AGORA);
    }

    @Test
    void prioridadeOmitidaViraMediaESubcategoriaEOpcional() {
        solicitanteExiste(ana);
        categoriaAtiva();
        gravacaoOk();

        ChamadoResponse r = service.abrir(pedido(null, null), principal(ana));

        assertThat(r.prioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(r.subcategoria()).isNull();
        verifyNoInteractions(subcategorias);
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"TECNICO", "ADMIN"})
    void tecnicoEAdminPodemAbrirChamadoCritico(Perfil perfil) {
        Usuario equipe = usuario(2L, "Carlos Mendes", perfil);
        solicitanteExiste(equipe);
        categoriaAtiva();
        gravacaoOk();

        assertThat(service.abrir(pedido(Prioridade.CRITICA, null), principal(equipe)).prioridade())
                .isEqualTo(Prioridade.CRITICA);
    }

    @Test
    void solicitanteNaoPodeAbrirChamadoCritico() {
        solicitanteExiste(ana);

        assertThatThrownBy(() -> service.abrir(pedido(Prioridade.CRITICA, null), principal(ana)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CRITICA");

        verifyNoInteractions(categorias, protocolos, chamados, historico);
    }

    @Test
    void perfilConsideradoEOdoBancoENaoOQueVemNoPrincipal() {
        solicitanteExiste(ana);
        UsuarioPrincipal afirmaSerTecnico = new UsuarioPrincipal(ana.getId(), ana.getEmail(), Perfil.TECNICO);

        assertThatThrownBy(() -> service.abrir(pedido(Prioridade.CRITICA, null), afirmaSerTecnico))
                .isInstanceOf(RegraNegocioException.class);

        verifyNoInteractions(chamados);
    }

    @Test
    void usuarioInativoOuInexistenteNaoAbreChamado() {
        ana.setAtivo(false);
        solicitanteExiste(ana);
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abrir(pedido(null, null), principal(ana)))
                .isInstanceOf(AutenticacaoException.class);
        assertThatThrownBy(() -> service.abrir(pedido(null, null),
                principal(usuario(99L, "Fantasma", Perfil.SOLICITANTE))))
                .isInstanceOf(AutenticacaoException.class);

        verifyNoInteractions(chamados, protocolos);
    }

    @Test
    void categoriaInexistenteOuInativaEhRecusadaSemConsumirProtocolo() {
        solicitanteExiste(ana);
        when(categorias.findById(3L)).thenReturn(Optional.empty(), Optional.of(categoria(3L, false)));

        assertThatThrownBy(() -> service.abrir(pedido(null, null), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Categoria inválida ou inativa.");
        assertThatThrownBy(() -> service.abrir(pedido(null, null), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Categoria inválida ou inativa.");

        verifyNoInteractions(protocolos, chamados);
    }

    @Test
    void subcategoriaDeOutraCategoriaEhRecusada() {
        solicitanteExiste(ana);
        categoriaAtiva();
        when(subcategorias.findByIdAndCategoriaId(20L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abrir(pedido(null, 20L), principal(ana)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Subcategoria inválida, inativa ou de outra categoria.");

        verifyNoInteractions(protocolos, chamados);
    }

    @Test
    void subcategoriaInativaEhRecusada() {
        solicitanteExiste(ana);
        categoriaAtiva();
        when(subcategorias.findByIdAndCategoriaId(8L, 3L)).thenReturn(Optional.of(subcategoria(8L, rede, false)));

        assertThatThrownBy(() -> service.abrir(pedido(null, 8L), principal(ana)))
                .isInstanceOf(RegraNegocioException.class);

        verifyNoInteractions(protocolos, chamados);
    }

    @Test
    void semPoliticaDeSlaOChamadoNaoEhGravadoNemConsomeProtocolo() {
        solicitanteExiste(ana);
        categoriaAtiva();
        doThrow(new RegraNegocioException("Não há política de SLA ativa para a prioridade MEDIA."))
                .when(sla).aplicarLimites(any(Chamado.class));

        assertThatThrownBy(() -> service.abrir(pedido(null, null), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("SLA");

        verifyNoInteractions(protocolos, chamados, historico);
    }
}
