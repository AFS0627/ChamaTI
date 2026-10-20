package br.com.chamati;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.enums.Perfil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Dados da demonstração: 8 categorias; categoria 3 (Rede) tem Wi-Fi(7), VPN(8), Internet(9); categoria 2 tem Instalação(4). */
@Transactional
@AutoConfigureMockMvc
class CatalogoIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor TECNICO = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor SOLICITANTE = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);

    @Autowired MockMvc mvc;

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                 RequestPostProcessor quem, String json) throws Exception {
        return mvc.perform(req.with(quem).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void leituraParaAutenticadosEAdministracaoSoParaAdmin() throws Exception {
        mvc.perform(get("/api/categorias/ativas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categorias/ativas").with(SOLICITANTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
        mvc.perform(get("/api/categorias/3/subcategorias").with(TECNICO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));

        mvc.perform(get("/api/categorias").with(SOLICITANTE)).andExpect(status().isForbidden());
        enviar(post("/api/categorias"), TECNICO, "{\"nome\":\"X\"}").andExpect(status().isForbidden());
        enviar(put("/api/categorias/1"), SOLICITANTE, "{\"nome\":\"X\"}").andExpect(status().isForbidden());
        mvc.perform(patch("/api/categorias/1/desativar").with(TECNICO)).andExpect(status().isForbidden());
        enviar(post("/api/categorias/3/subcategorias"), TECNICO, "{\"nome\":\"X\"}").andExpect(status().isForbidden());
        mvc.perform(patch("/api/categorias/3/subcategorias/7/desativar").with(SOLICITANTE))
                .andExpect(status().isForbidden());
    }

    @Test
    void criaEAtualizaCategoriaComNomeUnico() throws Exception {
        // sucessos antes das falhas: após uma exceção de serviço a transação do teste fica marcada para rollback
        String corpo = enviar(post("/api/categorias"), ADMIN, "{\"nome\":\"  Segurança \",\"descricao\":\"Incidentes\"}")
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nome").value("Segurança")).andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(corpo, "$.id");

        enviar(put("/api/categorias/" + id), ADMIN, "{\"nome\":\"Segurança da Informação\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Segurança da Informação"));
        mvc.perform(get("/api/categorias").with(ADMIN).param("q", "segur"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));

        enviar(post("/api/categorias"), ADMIN, "{\"nome\":\"seguranca da informacao\"}")
                .andExpect(status().isConflict());                                     // sem acento/caixa
        enviar(post("/api/categorias"), ADMIN, "{\"nome\":\"HARDWARE\"}").andExpect(status().isConflict());
        enviar(post("/api/categorias"), ADMIN, "{\"nome\":\"  \"}").andExpect(status().isBadRequest());
        enviar(put("/api/categorias/" + id), ADMIN, "{\"nome\":\"Software\"}").andExpect(status().isConflict());
        enviar(put("/api/categorias/9999"), ADMIN, "{\"nome\":\"Y\"}").andExpect(status().isNotFound());
    }

    @Test
    void categoriaInativaFicaVisivelSoParaAdminEEscondeSuasSubcategorias() throws Exception {
        mvc.perform(patch("/api/categorias/1/desativar").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(get("/api/categorias/ativas").with(SOLICITANTE)).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/categorias/1").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/api/categorias/1/subcategorias").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/categorias").with(ADMIN).param("ativo", "false"))
                .andExpect(jsonPath("$.totalElementos").value(1));

        mvc.perform(get("/api/categorias/1").with(SOLICITANTE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/categorias/1/subcategorias").with(SOLICITANTE)).andExpect(status().isNotFound());
    }

    @Test
    void reativarCategoriaDevolveAVisibilidade() throws Exception {
        mvc.perform(patch("/api/categorias/1/desativar").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(patch("/api/categorias/1/ativar").with(ADMIN)).andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(get("/api/categorias/1").with(SOLICITANTE)).andExpect(status().isOk());
        mvc.perform(get("/api/categorias/ativas").with(SOLICITANTE)).andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void subcategoriasPertencemSempreACategoriaDaUrl() throws Exception {
        // sucessos antes das falhas: após uma exceção de serviço a transação do teste fica marcada para rollback
        enviar(post("/api/categorias/3/subcategorias"), ADMIN, "{\"nome\":\"Firewall\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoriaId").value(3));
        enviar(post("/api/categorias/1/subcategorias"), ADMIN, "{\"nome\":\"Wi-Fi\"}")   // outro pai: permitido
                .andExpect(status().isCreated());
        enviar(put("/api/categorias/3/subcategorias/8"), ADMIN, "{\"nome\":\"VPN corporativa\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaId").value(3));

        enviar(post("/api/categorias/3/subcategorias"), ADMIN, "{\"nome\":\"wi-fi\"}").andExpect(status().isConflict());
        enviar(post("/api/categorias/9999/subcategorias"), ADMIN, "{\"nome\":\"Z\"}").andExpect(status().isNotFound());
        enviar(put("/api/categorias/1/subcategorias/8"), ADMIN, "{\"nome\":\"Qualquer\"}")  // VPN não é da categoria 1
                .andExpect(status().isNotFound());
        enviar(put("/api/categorias/3/subcategorias/8"), ADMIN, "{\"nome\":\"Wi-Fi\",\"categoriaId\":1}")
                .andExpect(status().isConflict());                                           // categoriaId do corpo é ignorado
        mvc.perform(patch("/api/categorias/1/subcategorias/8/desativar").with(ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void desativarSubcategoriaEscondeParaNaoAdmins() throws Exception {
        mvc.perform(patch("/api/categorias/3/subcategorias/9/desativar").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/api/categorias/3/subcategorias").with(SOLICITANTE)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/categorias/3/subcategorias").with(ADMIN)).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(patch("/api/categorias/3/subcategorias/9/ativar").with(ADMIN)).andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void naoPermiteSubcategoriaAtivaEmCategoriaInativa() throws Exception {
        mvc.perform(patch("/api/categorias/2/desativar").with(ADMIN)).andExpect(status().isOk());
        enviar(post("/api/categorias/2/subcategorias"), ADMIN, "{\"nome\":\"Nova\"}")
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/categorias/2/subcategorias/4/ativar").with(ADMIN))
                .andExpect(status().isUnprocessableEntity());
    }
}
