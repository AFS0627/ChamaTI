package br.com.chamati.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.dto.UsuarioCriarRequest;
import br.com.chamati.enums.Perfil;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.service.UsuarioService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AdminInicialRunnerTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    static final String SENHA_BOA = "Provisoria2026";

    @Mock UsuarioRepository usuarios;
    @Mock UsuarioService usuarioService;

    private AdminInicialRunner runner(String nome, String email, String senha) {
        return new AdminInicialRunner(usuarios, usuarioService, VALIDATOR, nome, email, senha);
    }

    @Test
    void semVariaveisNaoFazNadaENemConsultaOBanco() {
        runner("Administrador", "", "").executar();
        runner("Administrador", "   ", "  ").executar();

        verifyNoInteractions(usuarios, usuarioService);
    }

    @Test
    void emailOuSenhaIsoladosInterrompemASubida() {
        assertThatThrownBy(() -> runner("Administrador", "admin@empresa.com", "").executar())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("juntos");
        assertThatThrownBy(() -> runner("Administrador", "", SENHA_BOA).executar())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("juntos");

        verifyNoInteractions(usuarios, usuarioService);
    }

    @Test
    void comAdministradorAtivoExistenteNadaEhCriadoNemAlterado() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(true);

        runner("Administrador", "admin@empresa.com", SENHA_BOA).executar();

        verify(usuarioService, never()).criar(any());
    }

    @Test
    void semAdministradorAtivoCriaOAdminComOsDadosInformados() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        runner("  Ana Admin  ", "  Ana.Admin@Empresa.com ", SENHA_BOA).executar();

        ArgumentCaptor<UsuarioCriarRequest> pedido = ArgumentCaptor.forClass(UsuarioCriarRequest.class);
        verify(usuarioService).criar(pedido.capture());
        assertThat(pedido.getValue().perfil()).isEqualTo(Perfil.ADMIN);
        assertThat(pedido.getValue().nome()).isEqualTo("Ana Admin");
        assertThat(pedido.getValue().email()).isEqualTo("Ana.Admin@Empresa.com");
        assertThat(pedido.getValue().senha()).isEqualTo(SENHA_BOA);
    }

    @Test
    void nomeEmBrancoUsaOPadraoAdministrador() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        runner("   ", "admin@empresa.com", SENHA_BOA).executar();

        ArgumentCaptor<UsuarioCriarRequest> pedido = ArgumentCaptor.forClass(UsuarioCriarRequest.class);
        verify(usuarioService).criar(pedido.capture());
        assertThat(pedido.getValue().nome()).isEqualTo("Administrador");
    }

    @Test
    void senhaForaDaPoliticaInterrompeASubidaSemRepetirOValor() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner("Administrador", "admin@empresa.com", "curta1").executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("senha")
                .hasMessageNotContaining("curta1");
        assertThatThrownBy(() -> runner("Administrador", "admin@empresa.com", "somenteletras").executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("somenteletras");

        verify(usuarioService, never()).criar(any());
    }

    @Test
    void senhaAcimaDeSetentaEDoisBytesInterrompeASubida() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner("Administrador", "admin@empresa.com", "é".repeat(40) + "1").executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void emailInvalidoInterrompeASubida() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner("Administrador", "isto-nao-e-email", SENHA_BOA).executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");

        verify(usuarioService, never()).criar(any());
    }

    @Test
    void emailJaUsadoPorUsuarioSemSerAdminAtivoOrientaOOperador() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);
        doThrow(new ConflitoException("Já existe um usuário com este e-mail.")).when(usuarioService).criar(any());

        assertThatThrownBy(() -> runner("Administrador", "admin@empresa.com", SENHA_BOA).executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reative esse usuário");
    }

    @Test
    void corridaComOutraInstanciaQueCriouOAdminNaoEErro() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false, true);
        doThrow(new DataIntegrityViolationException("duplicado")).when(usuarioService).criar(any());

        runner("Administrador", "admin@empresa.com", SENHA_BOA).executar();

        verify(usuarioService).criar(any());
    }
}
