package br.com.chamati.service;

import static br.com.chamati.Fixtures.usuario;
import static br.com.chamati.enums.Perfil.ADMIN;
import static br.com.chamati.enums.Perfil.SOLICITANTE;
import static br.com.chamati.enums.Perfil.TECNICO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.dto.UsuarioAtualizarRequest;
import br.com.chamati.dto.UsuarioCriarRequest;
import br.com.chamati.dto.UsuarioResponse;
import br.com.chamati.entity.Usuario;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.UsuarioMapper;
import br.com.chamati.repository.RefreshTokenRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.repository.consulta.UsuarioResumo;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock UsuarioRepository usuarios;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder encoder;

    UsuarioService service;

    @BeforeEach
    void preparar() {
        service = new UsuarioService(usuarios, refreshTokens, encoder, new UsuarioMapper());
    }

    private void salvarDevolvendoOArgumento() {
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- criar ----------

    @Test
    void criarNormalizaDadosCodificaASenhaEAtivaOUsuario() {
        when(usuarios.existsByEmail("nova.pessoa@chamati.local")).thenReturn(false);
        when(encoder.encode("Senha123")).thenReturn("$2a$hash");
        salvarDevolvendoOArgumento();

        UsuarioResponse resposta = service.criar(new UsuarioCriarRequest("  Nova Pessoa  ",
                "  Nova.Pessoa@Chamati.local ", "Senha123", TECNICO, "   ", " 47 99999-0000 "));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(salvo.capture());
        Usuario u = salvo.getValue();
        assertThat(u.getNome()).isEqualTo("Nova Pessoa");
        assertThat(u.getEmail()).isEqualTo("nova.pessoa@chamati.local");
        assertThat(u.getSenhaHash()).isEqualTo("$2a$hash").isNotEqualTo("Senha123");
        assertThat(u.getPerfil()).isEqualTo(TECNICO);
        assertThat(u.getDepartamento()).isNull();
        assertThat(u.getTelefone()).isEqualTo("47 99999-0000");
        assertThat(u.isAtivo()).isTrue();

        assertThat(resposta.email()).isEqualTo("nova.pessoa@chamati.local");
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.toString()).doesNotContain("$2a$hash");
    }

    @Test
    void criarComEmailJaCadastradoLancaConflitoSemGravarNemCodificarSenha() {
        when(usuarios.existsByEmail("ana@chamati.local")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new UsuarioCriarRequest("Ana", "ANA@chamati.local", "Senha123",
                SOLICITANTE, null, null)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Já existe um usuário com este e-mail.");

        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    // ---------- consultar ----------

    @Test
    void buscarPorIdDevolveOUsuario() {
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario(3L, "Fernanda Lima", TECNICO)));

        UsuarioResponse r = service.buscarPorId(3L);

        assertThat(r.id()).isEqualTo(3L);
        assertThat(r.perfil()).isEqualTo(TECNICO);
    }

    @Test
    void buscarPorIdInexistenteLanca404() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listarAplicaFiltrosPaginacaoEOrdenacaoPorNome() {
        UsuarioResumo resumo = new UsuarioResumo(3L, "Fernanda Lima", "fernanda@chamati.local", TECNICO, null,
                true, null);
        when(usuarios.buscar(eq(TECNICO), eq(true), eq("fer"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resumo), org.springframework.data.domain.PageRequest.of(1, 10), 11));

        PaginaResponse<UsuarioResumo> pagina = service.listar(TECNICO, true, "  fer ", 1, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(usuarios).buscar(eq(TECNICO), eq(true), eq("fer"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("nome: ASC,id: ASC");
        assertThat(pagina.conteudo()).containsExactly(resumo);
        assertThat(pagina.totalElementos()).isEqualTo(11);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    void listarTrataTermoEmBrancoComoAusente() {
        when(usuarios.buscar(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listar(null, null, "   ", 0, 20);

        verify(usuarios).buscar(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    // ---------- atualizar ----------

    @Test
    void atualizarAlteraOsCamposPermitidosDeOutroUsuario() {
        Usuario alvo = usuario(5L, "Bruno Costa", SOLICITANTE);
        String senhaAnterior = alvo.getSenhaHash();
        when(usuarios.findById(5L)).thenReturn(Optional.of(alvo));
        when(usuarios.existsByEmailAndIdNot("bruno.novo@chamati.local", 5L)).thenReturn(false);
        salvarDevolvendoOArgumento();

        UsuarioResponse r = service.atualizar(5L, new UsuarioAtualizarRequest(" Bruno C. ",
                "Bruno.Novo@chamati.local", TECNICO, " Suporte ", null), 1L);

        assertThat(r.nome()).isEqualTo("Bruno C.");
        assertThat(r.email()).isEqualTo("bruno.novo@chamati.local");
        assertThat(r.perfil()).isEqualTo(TECNICO);
        assertThat(r.departamento()).isEqualTo("Suporte");
        assertThat(alvo.getSenhaHash()).isEqualTo(senhaAnterior);
        assertThat(alvo.isAtivo()).isTrue();
    }

    @Test
    void atualizarPermiteAoProprioUsuarioMantendoOMesmoPerfil() {
        Usuario admin = usuario(1L, "Admin", ADMIN);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
        salvarDevolvendoOArgumento();

        UsuarioResponse r = service.atualizar(1L,
                new UsuarioAtualizarRequest("Administrador", admin.getEmail(), ADMIN, null, null), 1L);

        assertThat(r.nome()).isEqualTo("Administrador");
        assertThat(r.perfil()).isEqualTo(ADMIN);
    }

    @Test
    void atualizarOProprioPerfilEhRecusado() {
        Usuario admin = usuario(1L, "Admin", ADMIN);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.atualizar(1L,
                new UsuarioAtualizarRequest("Admin", admin.getEmail(), SOLICITANTE, null, null), 1L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Você não pode alterar o seu próprio perfil.");

        assertThat(admin.getPerfil()).isEqualTo(ADMIN);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void atualizarComEmailDeOutroUsuarioLancaConflito() {
        when(usuarios.findById(5L)).thenReturn(Optional.of(usuario(5L, "Bruno Costa", SOLICITANTE)));
        when(usuarios.existsByEmailAndIdNot("ana@chamati.local", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(5L,
                new UsuarioAtualizarRequest("Bruno", "ana@chamati.local", SOLICITANTE, null, null), 1L))
                .isInstanceOf(ConflitoException.class);

        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void atualizarUsuarioInexistenteLanca404() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(99L,
                new UsuarioAtualizarRequest("X", "x@chamati.local", TECNICO, null, null), 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(usuarios, never()).existsByEmailAndIdNot(anyString(), anyLong());
    }

    // ---------- ativar / desativar ----------

    @Test
    void ativarReativaOUsuario() {
        Usuario inativo = usuario(5L, "Bruno Costa", SOLICITANTE);
        inativo.setAtivo(false);
        when(usuarios.findById(5L)).thenReturn(Optional.of(inativo));
        salvarDevolvendoOArgumento();

        assertThat(service.ativar(5L).ativo()).isTrue();
    }

    @Test
    void desativarMarcaInativoERevogaTodasAsSessoes() {
        Usuario alvo = usuario(5L, "Bruno Costa", SOLICITANTE);
        when(usuarios.findById(5L)).thenReturn(Optional.of(alvo));
        salvarDevolvendoOArgumento();

        UsuarioResponse r = service.desativar(5L, 1L);

        assertThat(r.ativo()).isFalse();
        assertThat(alvo.isAtivo()).isFalse();
        verify(refreshTokens).revogarTodosDoUsuario(eq(5L), any(java.time.Instant.class));
    }

    @Test
    void ninguemDesativaASiMesmo() {
        assertThatThrownBy(() -> service.desativar(1L, 1L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Você não pode desativar a sua própria conta.");

        verifyNoInteractions(usuarios, refreshTokens);
    }

    @Test
    void desativarUsuarioInexistenteLanca404SemRevogarSessoes() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desativar(99L, 1L)).isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(refreshTokens);
    }
}
