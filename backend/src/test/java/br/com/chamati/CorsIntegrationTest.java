package br.com.chamati;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** CORS do perfil dev: origens padrão http://localhost:5173 e http://localhost:3000. */
@AutoConfigureMockMvc
class CorsIntegrationTest extends IntegrationTestBase {

    private static final String PERMITIDA = "http://localhost:5173";
    private static final String NAO_PERMITIDA = "https://malicioso.example.com";

    @Autowired MockMvc mvc;

    @Test
    void preflightDeOrigemPermitidaEAceitoComMetodosECabecalhosDaApi() throws Exception {
        mvc.perform(options("/api/chamados/1/status")
                        .header("Origin", PERMITIDA)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDA))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Allow-Headers"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void segundaOrigemPadraoDeDesenvolvimentoTambemEPermitida() throws Exception {
        mvc.perform(options("/api/chamados")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void preflightDeOrigemNaoPermitidaEhRecusado() throws Exception {
        mvc.perform(options("/api/chamados")
                        .header("Origin", NAO_PERMITIDA)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void metodoForaDaListaPermitidaNoPreflightEhRecusado() throws Exception {
        mvc.perform(options("/api/chamados")
                        .header("Origin", PERMITIDA)
                        .header("Access-Control-Request-Method", "TRACE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void respostaRealCarregaOsCabecalhosCorsInclusiveNosErros() throws Exception {
        mvc.perform(get("/api/auth/me").header("Origin", PERMITIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDA));

        mvc.perform(post("/api/auth/login").header("Origin", PERMITIDA)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDA))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("Retry-After")));
    }

    @Test
    void naoPermiteCredenciaisNemOrigemCuringa() throws Exception {
        mvc.perform(options("/api/chamados")
                        .header("Origin", PERMITIDA)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDA));
    }
}
