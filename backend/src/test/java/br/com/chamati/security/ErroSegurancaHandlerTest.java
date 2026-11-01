package br.com.chamati.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ErroSegurancaHandlerTest {

    final ErroSegurancaHandler handler = new ErroSegurancaHandler(new ObjectMapper().findAndRegisterModules());
    final ObjectMapper json = new ObjectMapper();

    private JsonNode corpo(MockHttpServletResponse resposta) throws Exception {
        return json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void naoAutenticadoRecebe401NoModeloDeErroComChallengeBearer() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), resposta, new BadCredentialsException("detalhe interno do token"));

        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(resposta.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(resposta.getContentType()).startsWith("application/json");
        JsonNode erro = corpo(resposta);
        assertThat(erro.get("status").asInt()).isEqualTo(401);
        assertThat(erro.get("erro").asText()).isEqualTo("Unauthorized");
        assertThat(erro.get("mensagem").asText()).isEqualTo("Token ausente, inválido ou expirado.");
        assertThat(erro.has("timestamp")).isTrue();
    }

    @Test
    void semPermissaoRecebe403NoModeloDeErro() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), resposta, new AccessDeniedException("ROLE_ADMIN exigido"));

        assertThat(resposta.getStatus()).isEqualTo(403);
        assertThat(resposta.getHeader("WWW-Authenticate")).isNull();
        JsonNode erro = corpo(resposta);
        assertThat(erro.get("status").asInt()).isEqualTo(403);
        assertThat(erro.get("erro").asText()).isEqualTo("Forbidden");
        assertThat(erro.get("mensagem").asText()).isEqualTo("Seu perfil não tem permissão para esta operação.");
    }

    @Test
    void respostaNuncaExpoeOMotivoInternoDaRecusa() throws Exception {
        MockHttpServletResponse r401 = new MockHttpServletResponse();
        MockHttpServletResponse r403 = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), r401, new BadCredentialsException("assinatura inválida"));
        handler.handle(new MockHttpServletRequest(), r403, new AccessDeniedException("ROLE_ADMIN exigido"));

        assertThat(r401.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("assinatura");
        assertThat(r403.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("ROLE_ADMIN");
    }
}
