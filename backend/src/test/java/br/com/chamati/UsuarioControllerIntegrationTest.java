package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class UsuarioControllerIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    static RequestPostProcessor como(Perfil perfil, long id) {
        var p = new UsuarioPrincipal(id, "ator@chamati.local", perfil);
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    static final RequestPostProcessor ADMIN = como(Perfil.ADMIN, 1L); // admin da demonstração

    static final String NOVO = """
            {"nome":"  Paulo Dias ","email":"Paulo.Dias@Chamati.local","senha":"Segredo123",
             "perfil":"TECNICO","departamento":"TI","telefone":"  "}""";

    @Test
    void semAutenticacaoRetorna401EnaoAdminRetorna403() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").with(como(Perfil.TECNICO, 2L))).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(como(Perfil.SOLICITANTE, 4L))).andExpect(status().isForbidden());
    }

    @Test
    void criaUsuarioSemExporSenhaEgravaBcrypt() throws Exception {
        mvc.perform(post("/api/usuarios").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(NOVO))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("paulo.dias@chamati.local"))
                .andExpect(jsonPath("$.nome").value("Paulo Dias"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        var salvo = usuarios.findByEmail("paulo.dias@chamati.local").orElseThrow();
        assertThat(salvo.getSenhaHash()).startsWith("$2").isNotEqualTo("Segredo123");
        assertThat(encoder.matches("Segredo123", salvo.getSenhaHash())).isTrue();
    }

    @Test
    void rejeitaEmailDuplicadoEDadosInvalidos() throws Exception {
        mvc.perform(post("/api/usuarios").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"X","email":"ADMIN@chamati.local","senha":"Segredo123","perfil":"ADMIN"}"""))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/usuarios").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"","email":"nao-e-email","senha":"123","perfil":null}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).doesNotContain("123"));
    }

    @Test
    void listaFiltraEConsulta() throws Exception {
        mvc.perform(get("/api/usuarios").with(ADMIN).param("perfil", "SOLICITANTE").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.conteudo[0].senhaHash").doesNotExist());
        mvc.perform(get("/api/usuarios").with(ADMIN).param("q", "souza"))
                .andExpect(jsonPath("$.conteudo[0].email").value("ana.souza@chamati.local"));
        mvc.perform(get("/api/usuarios").with(ADMIN).param("tamanho", "1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/usuarios").with(ADMIN).param("perfil", "XPTO")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/usuarios/4").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departamento").value("Financeiro"));
        mvc.perform(get("/api/usuarios/9999").with(ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void atualizaUsuarioERegrasDoProprioAdmin() throws Exception {
        mvc.perform(put("/api/usuarios/4").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Ana S. Souza","email":"ana.souza@chamati.local","perfil":"SOLICITANTE","departamento":"Fiscal"}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departamento").value("Fiscal"));

        mvc.perform(put("/api/usuarios/4").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Ana","email":"bruno.costa@chamati.local","perfil":"SOLICITANTE"}"""))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/usuarios/1").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Administrador","email":"admin@chamati.local","perfil":"TECNICO"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ativaEDesativaRevogandoTokens() throws Exception {
        mvc.perform(patch("/api/usuarios/4/desativar").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(patch("/api/usuarios/4/ativar").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(patch("/api/usuarios/1/desativar").with(ADMIN)).andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/usuarios/9999/desativar").with(ADMIN)).andExpect(status().isNotFound());
    }
}
