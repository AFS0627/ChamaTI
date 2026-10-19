package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.config.JwtProperties;
import br.com.chamati.entity.RefreshToken;
import br.com.chamati.repository.RefreshTokenRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.TokenHash;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Senha dos usuários de demonstração (V100): Senha@123. */
@Transactional
@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestBase {

    static final String SENHA = "Senha@123";
    static final String ADMIN = "admin@chamati.local";
    static final String TECNICO = "carlos.mendes@chamati.local";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RefreshTokenRepository tokens;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties props;

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

    private static String campo(String corpo, String caminho) {
        return JsonPath.read(corpo, caminho);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void loginValidoDevolveTokensSemExporSenhaEGuardaApenasHashDoRefresh() throws Exception {
        String corpo = login(ADMIN, SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEmSegundos").value(900))
                .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String senhaHash = usuarios.findByEmail(ADMIN).orElseThrow().getSenhaHash();
        assertThat(corpo).doesNotContain(senhaHash).doesNotContain("senha");
        assertThat(usuarios.findByEmail(ADMIN).orElseThrow().getUltimoLoginEm()).isNotNull();

        String refresh = campo(corpo, "$.refreshToken");
        assertThat(tokens.findByTokenHash(TokenHash.sha256Hex(refresh))).isPresent();
        assertThat(tokens.findByTokenHash(refresh)).isEmpty();
    }

    @Test
    void credenciaisInvalidasRetornamMesmaMensagemGenerica() throws Exception {
        String senhaErrada = login("juliana.rocha@chamati.local", "errada123")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String emailInexistente = login("naoexiste@chamati.local", SENHA)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(campo(senhaErrada, "$.mensagem")).isEqualTo(campo(emailInexistente, "$.mensagem"));
    }

    @Test
    void usuarioInativoNaoFazLogin() throws Exception {
        var u = usuarios.findByEmail("fernanda.lima@chamati.local").orElseThrow();
        u.setAtivo(false);
        usuarios.saveAndFlush(u);
        login(u.getEmail(), SENHA).andExpect(status().isUnauthorized());
    }

    @Test
    void loginComCorpoInvalidoRetorna400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bloqueiaTentativasAposCincoFalhas() throws Exception {
        String email = "bruno.costa@chamati.local";
        for (int i = 0; i < 5; i++) {
            login(email, "errada123").andExpect(status().isUnauthorized());
        }
        login(email, SENHA).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void protegeEndpointsEAplicaPerfis() throws Exception {
        String admin = campo(corpoDoLogin(ADMIN), "$.accessToken");
        String tecnico = campo(corpoDoLogin(TECNICO), "$.accessToken");

        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header("Authorization", bearer(tecnico))).andExpect(status().isForbidden());
    }

    @Test
    void rejeitaTokensInvalidos() throws Exception {
        Instant agora = Instant.now();
        JwtClaimsSet expirado = JwtClaimsSet.builder().issuer(props.issuer()).subject("1")
                .issuedAt(agora.minus(10, ChronoUnit.MINUTES)).expiresAt(agora.minus(5, ChronoUnit.MINUTES)).build();
        JwtClaimsSet emissorErrado = JwtClaimsSet.builder().issuer("outro").subject("1")
                .issuedAt(agora).expiresAt(agora.plus(5, ChronoUnit.MINUTES)).build();
        JwtClaimsSet valido = JwtClaimsSet.builder().issuer(props.issuer()).subject("1")
                .issuedAt(agora).expiresAt(agora.plus(5, ChronoUnit.MINUTES)).build();
        JwtClaimsSet subjetoInvalido = JwtClaimsSet.builder().issuer(props.issuer()).subject("abc")
                .issuedAt(agora).expiresAt(agora.plus(5, ChronoUnit.MINUTES)).build();
        JwtEncoder outroSegredo = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "outro-segredo-outro-segredo-outro-segredo-123".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

        String[] invalidos = {
                "lixo.total.aqui",
                assinar(encoder, expirado),
                assinar(encoder, emissorErrado),
                assinar(encoder, subjetoInvalido),
                assinar(outroSegredo, valido),
        };
        for (String token : invalidos) {
            mvc.perform(get("/api/usuarios").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
        // sanidade: o mesmo claim válido, assinado com o segredo correto, é aceito
        mvc.perform(get("/api/usuarios").header("Authorization", bearer(assinar(encoder, valido))))
                .andExpect(status().isOk());
    }

    private static String assinar(JwtEncoder enc, JwtClaimsSet claims) {
        return enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void desativarUsuarioInvalidaAcessoERefreshImediatamente() throws Exception {
        String admin = campo(corpoDoLogin(ADMIN), "$.accessToken");
        String corpoTecnico = corpoDoLogin(TECNICO);
        String accessTecnico = campo(corpoTecnico, "$.accessToken");
        String refreshTecnico = campo(corpoTecnico, "$.refreshToken");

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(accessTecnico))).andExpect(status().isOk());
        mvc.perform(patch("/api/usuarios/2/desativar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(accessTecnico)))
                .andExpect(status().isUnauthorized());
        renovar(refreshTecnico).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotacionaEDetectaReuso() throws Exception {
        String primeiro = campo(corpoDoLogin("ana.souza@chamati.local"), "$.refreshToken");

        String corpo2 = renovar(primeiro).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String segundo = campo(corpo2, "$.refreshToken");
        assertThat(segundo).isNotEqualTo(primeiro);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(campo(corpo2, "$.accessToken"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ana.souza@chamati.local"));

        // reuso do token já rotacionado: negado e todas as sessões do usuário são revogadas
        renovar(primeiro).andExpect(status().isUnauthorized());
        renovar(segundo).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshInvalidoOuExpiradoRetorna401() throws Exception {
        renovar("token-que-nao-existe").andExpect(status().isUnauthorized());

        RefreshToken expirado = new RefreshToken();
        expirado.setUsuario(usuarios.getReferenceById(4L));
        expirado.setTokenHash(TokenHash.sha256Hex("token-expirado-de-teste"));
        expirado.setExpiraEm(Instant.now().minus(1, ChronoUnit.HOURS));
        tokens.saveAndFlush(expirado);
        renovar("token-expirado-de-teste").andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevogaSessaoDeFormaIdempotente() throws Exception {
        String refresh = campo(corpoDoLogin("juliana.rocha@chamati.local"), "$.refreshToken");
        String json = "{\"refreshToken\":\"" + refresh + "\"}";

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"desconhecido\"}")).andExpect(status().isNoContent());
        renovar(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutTodosRevogaTodasAsSessoes() throws Exception {
        String a = corpoDoLogin("fernanda.lima@chamati.local");
        String b = corpoDoLogin("fernanda.lima@chamati.local");

        mvc.perform(post("/api/auth/logout-todos").header("Authorization", bearer(campo(a, "$.accessToken"))))
                .andExpect(status().isNoContent());
        renovar(campo(a, "$.refreshToken")).andExpect(status().isUnauthorized());
        renovar(campo(b, "$.refreshToken")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout-todos")).andExpect(status().isUnauthorized());
    }
}
