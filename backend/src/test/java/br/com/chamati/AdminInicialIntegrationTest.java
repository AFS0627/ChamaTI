package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.config.AdminInicialRunner;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.service.UsuarioService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Criação do primeiro ADMIN sobre o banco real. A demonstração já tem um ADMIN; os testes o desativam quando preciso. */
@Transactional
@AutoConfigureMockMvc
class AdminInicialIntegrationTest extends IntegrationTestBase {

    static final String EMAIL = "primeiro.admin@chamati.local";
    static final String SENHA = "Primeira#Senha2026";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioService usuarioService;
    @Autowired Validator validator;
    @Autowired PasswordEncoder passwordEncoder;

    private AdminInicialRunner runner(String email, String senha) {
        return new AdminInicialRunner(usuarios, usuarioService, validator, "Primeiro Admin", email, senha);
    }

    private void desativarOsAdministradores() {
        for (Usuario u : usuarios.findAll()) {
            if (u.getPerfil() == Perfil.ADMIN) {
                u.setAtivo(false);
            }
        }
        usuarios.flush();
    }

    @Test
    void comAdministradorAtivoNaoCriaNada() {
        long antes = usuarios.count();

        runner(EMAIL, SENHA).run(null);

        assertThat(usuarios.count()).isEqualTo(antes);
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void semAdministradorAtivoCriaOPrimeiroAdminQueConsegueAutenticarEAGeracaoEIdempotente() throws Exception {
        desativarOsAdministradores();
        assertThat(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).isFalse();
        long antes = usuarios.count();

        runner(EMAIL.toUpperCase(), SENHA).run(null);

        assertThat(usuarios.count()).isEqualTo(antes + 1);
        Usuario criado = usuarios.findByEmail(EMAIL).orElseThrow();
        assertThat(criado.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(criado.isAtivo()).isTrue();
        assertThat(criado.getNome()).isEqualTo("Primeiro Admin");
        assertThat(criado.getSenhaHash()).isNotEqualTo(SENHA).startsWith("$2");
        assertThat(passwordEncoder.matches(SENHA, criado.getSenhaHash())).isTrue();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.email").value(EMAIL));

        runner(EMAIL, SENHA).run(null);
        runner("outro.admin@chamati.local", SENHA).run(null);
        assertThat(usuarios.count()).isEqualTo(antes + 1);
        assertThat(usuarios.findByEmail("outro.admin@chamati.local")).isEmpty();
    }

    @Test
    void configuracaoInvalidaNaoCriaUsuario() {
        desativarOsAdministradores();
        long antes = usuarios.count();

        assertThatThrownBy(() -> runner(EMAIL, "fraca").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runner("nao-e-email", SENHA).run(null)).isInstanceOf(IllegalStateException.class);

        assertThat(usuarios.count()).isEqualTo(antes);
    }

    @Test
    void emailDeUsuarioInativoJaExistenteInterrompeComOrientacaoESemCriarDuplicado() {
        desativarOsAdministradores();
        long antes = usuarios.count();

        assertThatThrownBy(() -> runner("admin@chamati.local", SENHA).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reative esse usuário");

        assertThat(usuarios.count()).isEqualTo(antes);
    }
}
