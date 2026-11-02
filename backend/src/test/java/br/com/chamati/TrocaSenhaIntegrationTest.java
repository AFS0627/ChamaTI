package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUT /api/auth/senha com tokens reais. Cada teste usa um usuário diferente porque o limitador de tentativas é
 * em memória e compartilhado pelo contexto: admin (fluxo completo), Carlos (recusas), Bruno (bloqueio por tentativas).
 */
@Transactional
@AutoConfigureMockMvc
class TrocaSenhaIntegrationTest extends IntegrationTestBase {

    static final String SENHA = "Senha@123";
    static final String NOVA = "NovaSenha@2026";
    static final String ADMIN = "admin@chamati.local";
    static final String CARLOS = "carlos.mendes@chamati.local";
    static final String BRUNO = "bruno.costa@chamati.local";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwordEncoder;

    private ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    private String corpoDoLogin(String email) throws Exception {
        return login(email, SENHA).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions renovar(String refresh) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"));
    }

    private ResultActions trocar(String access, String json) throws Exception {
        return mvc.perform(put("/api/auth/senha").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions trocar(String access, String atual, String nova) throws Exception {
        return trocar(access, "{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}");
    }

    private static String campo(String corpo, String caminho) {
        return JsonPath.read(corpo, caminho);
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(put("/api/auth/senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"" + NOVA + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trocaDeSenhaEncerraAsDemaisSessoesEMantemASessaoAtual() throws Exception {
        String antigoHash = usuarios.findByEmail(ADMIN).orElseThrow().getSenhaHash();
        String sessaoA = corpoDoLogin(ADMIN);
        String sessaoB = corpoDoLogin(ADMIN);

        String corpo = trocar(campo(sessaoA, "$.accessToken"), SENHA, NOVA).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String refreshNovo = campo(corpo, "$.refreshToken");
        String accessNovo = campo(corpo, "$.accessToken");
        assertThat(refreshNovo).isNotEqualTo(campo(sessaoA, "$.refreshToken")).isNotEqualTo(campo(sessaoB, "$.refreshToken"));
        assertThat(corpo).doesNotContain(NOVA).doesNotContain(SENHA);

        String novoHash = usuarios.findByEmail(ADMIN).orElseThrow().getSenhaHash();
        assertThat(novoHash).isNotEqualTo(antigoHash).isNotEqualTo(NOVA);
        assertThat(passwordEncoder.matches(NOVA, novoHash)).isTrue();
        assertThat(passwordEncoder.matches(SENHA, novoHash)).isFalse();

        // a sessão atual segue aberta com o refresh token novo (a renovação o rotaciona)...
        renovar(refreshNovo).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessNovo))
                .andExpect(status().isOk());
        // ...e as sessões antigas não renovam mais
        renovar(campo(sessaoA, "$.refreshToken")).andExpect(status().isUnauthorized());
        renovar(campo(sessaoB, "$.refreshToken")).andExpect(status().isUnauthorized());
        // a senha antiga deixa de valer e a nova passa a valer
        login(ADMIN, SENHA).andExpect(status().isUnauthorized());
        login(ADMIN, NOVA).andExpect(status().isOk());
    }

    @Test
    void accessTokenJaEmitidoContinuaValidoAteExpirar() throws Exception {
        String sessaoA = corpoDoLogin(ADMIN);
        String sessaoB = corpoDoLogin(ADMIN);

        trocar(campo(sessaoA, "$.accessToken"), SENHA, NOVA).andExpect(status().isOk());

        // limitação documentada: só os refresh tokens são revogados; o access token de B vale até expirar (15 min)
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + campo(sessaoB, "$.accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void recusasDeSenhaAtualIncorretaIgualFracaOuLonga() throws Exception {
        String access = campo(corpoDoLogin(CARLOS), "$.accessToken");

        trocar(access, "errada!1", NOVA).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Senha atual incorreta."));
        trocar(access, SENHA, SENHA).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("A nova senha deve ser diferente da atual."));
        trocar(access, SENHA, "é".repeat(40) + "1").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("72 bytes")));
        trocar(access, SENHA, "abc1").andExpect(status().isBadRequest());
        trocar(access, SENHA, "somenteletras").andExpect(status().isBadRequest());
        trocar(access, SENHA, "123456789").andExpect(status().isBadRequest());
        trocar(access, SENHA, "a1".repeat(37)).andExpect(status().isBadRequest());
        trocar(access, "{\"senhaAtual\":\"\",\"novaSenha\":\"" + NOVA + "\"}").andExpect(status().isBadRequest());
        trocar(access, "{\"senhaAtual\":\"" + SENHA + "\"}").andExpect(status().isBadRequest());

        String hash = usuarios.findByEmail(CARLOS).orElseThrow().getSenhaHash();
        assertThat(passwordEncoder.matches(SENHA, hash)).isTrue();
    }

    @Test
    void errosDeValidacaoNaoRepetemAsSenhasEnviadas() throws Exception {
        String access = campo(corpoDoLogin(CARLOS), "$.accessToken");

        String corpo = trocar(access, SENHA, "abc1").andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("abc1").doesNotContain(SENHA);
    }

    @Test
    void cincoSenhasAtuaisIncorretasBloqueiamATrocaComRetryAfter() throws Exception {
        String access = campo(corpoDoLogin(BRUNO), "$.accessToken");

        for (int i = 0; i < 5; i++) {
            trocar(access, "errada!1", NOVA).andExpect(status().isUnprocessableEntity());
        }

        trocar(access, SENHA, NOVA).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }
}
