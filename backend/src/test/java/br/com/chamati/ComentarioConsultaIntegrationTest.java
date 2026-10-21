package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.enums.Perfil;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chamado 3 (juliana / carlos): comentários PUBLICO(carlos), NOTA_INTERNA(carlos), PUBLICO(juliana) e 7 eventos de
 * histórico (CRIACAO, ALTERACAO_PRIORIDADE, ATRIBUICAO, 2 COMENTARIO, NOTA_INTERNA, ALTERACAO_STATUS).
 * Chamado 1 (ana / carlos, FECHADO): 3 comentários (1 interno) e 8 eventos (1 NOTA_INTERNA).
 */
@Transactional
@AutoConfigureMockMvc
class ComentarioConsultaIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);
    static final RequestPostProcessor BRUNO = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 5L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    @Autowired MockMvc mvc;

    private MockHttpServletRequestBuilder requisicao(RequestPostProcessor quem, String url, String... params) {
        MockHttpServletRequestBuilder req = get(url).with(quem);
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return req;
    }

    private String ler(RequestPostProcessor quem, String url, String... params) throws Exception {
        return mvc.perform(requisicao(quem, url, params)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions lerBruto(RequestPostProcessor quem, String url, String... params) throws Exception {
        return mvc.perform(requisicao(quem, url, params));
    }

    private static <T> List<T> lista(String corpo, String caminho) {
        return JsonPath.read(corpo, caminho);
    }

    private static int total(String corpo) {
        return JsonPath.<Integer>read(corpo, "$.totalElementos");
    }

    // ---------- comentários ----------

    @Test
    void equipeVeTodosOsComentariosEmOrdemCronologica() throws Exception {
        for (RequestPostProcessor quem : List.of(ADMIN, CARLOS)) {
            String corpo = ler(quem, "/api/chamados/3/comentarios");
            assertThat(total(corpo)).isEqualTo(3);
            assertThat(lista(corpo, "$.conteudo[*].tipo")).containsExactly("PUBLICO", "NOTA_INTERNA", "PUBLICO");
            assertThat(lista(corpo, "$.conteudo[*].autor.nome")).containsExactly("Carlos Mendes", "Carlos Mendes", "Juliana Rocha");
            assertThat(JsonPath.<String>read(corpo, "$.conteudo[0].conteudo")).isEqualTo(
                    "Estamos verificando o servidor de autenticação da VPN. Retorno em breve.");
        }
        String desc = ler(ADMIN, "/api/chamados/3/comentarios", "direcao", "DESC");
        assertThat(lista(desc, "$.conteudo[*].autor.id")).containsExactly(6, 2, 2);
        assertThat(lista(desc, "$.conteudo[*].autor.perfil")).containsExactly("SOLICITANTE", "TECNICO", "TECNICO");
    }

    @Test
    void equipeFiltraPorTipo() throws Exception {
        assertThat(total(ler(CARLOS, "/api/chamados/3/comentarios", "tipo", "NOTA_INTERNA"))).isEqualTo(1);
        assertThat(total(ler(CARLOS, "/api/chamados/3/comentarios", "tipo", "PUBLICO"))).isEqualTo(2);
    }

    @Test
    void solicitanteNuncaRecebeNotasInternas() throws Exception {
        String juliana = ler(JULIANA, "/api/chamados/3/comentarios");
        assertThat(total(juliana)).isEqualTo(2);
        assertThat(lista(juliana, "$.conteudo[*].tipo")).containsOnly("PUBLICO");
        assertThat(juliana).doesNotContain("gateway").doesNotContain("NOTA_INTERNA");
        assertThat(total(ler(JULIANA, "/api/chamados/3/comentarios", "tipo", "PUBLICO"))).isEqualTo(2);

        assertThat(total(ler(ANA, "/api/chamados/1/comentarios"))).isEqualTo(2);       // fechado também pode ser lido
        assertThat(total(ler(ADMIN, "/api/chamados/1/comentarios"))).isEqualTo(3);

        lerBruto(JULIANA, "/api/chamados/3/comentarios", "tipo", "NOTA_INTERNA").andExpect(status().isForbidden());
    }

    @Test
    void paginaOsComentarios() throws Exception {
        mvc.perform(requisicao(ADMIN, "/api/chamados/3/comentarios", "tamanho", "1", "pagina", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.conteudo[0].tipo").value("NOTA_INTERNA"))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.pagina").value(1));
    }

    @Test
    void naoExpoeDadosSensiveisDoAutor() throws Exception {
        String corpo = ler(ADMIN, "/api/chamados/3/comentarios");
        assertThat(corpo).doesNotContain("email").doesNotContain("senha").doesNotContain("chamati.local");
    }

    @Test
    void comentariosRespeitamVisibilidadeEParametros() throws Exception {
        lerBruto(FERNANDA, "/api/chamados/3/comentarios").andExpect(status().isNotFound());
        lerBruto(ANA, "/api/chamados/3/comentarios").andExpect(status().isNotFound());
        lerBruto(ADMIN, "/api/chamados/9999/comentarios").andExpect(status().isNotFound());
        lerBruto(ADMIN, "/api/chamados/3/comentarios", "tamanho", "0").andExpect(status().isBadRequest());
        lerBruto(ADMIN, "/api/chamados/3/comentarios", "tamanho", "101").andExpect(status().isBadRequest());
        lerBruto(ADMIN, "/api/chamados/3/comentarios", "pagina", "-1").andExpect(status().isBadRequest());
        lerBruto(ADMIN, "/api/chamados/3/comentarios", "direcao", "x").andExpect(status().isBadRequest());
        lerBruto(ADMIN, "/api/chamados/3/comentarios", "tipo", "XYZ").andExpect(status().isBadRequest());
    }

    // ---------- histórico ----------

    @Test
    void equipeVeHistoricoCompletoESolicitanteSemEventosDeNotaInterna() throws Exception {
        String admin = ler(ADMIN, "/api/chamados/3/historico");
        assertThat(total(admin)).isEqualTo(7);
        assertThat(lista(admin, "$.conteudo[*].tipoEvento")).containsExactlyInAnyOrder(
                "CRIACAO", "ALTERACAO_PRIORIDADE", "ATRIBUICAO", "COMENTARIO", "COMENTARIO", "NOTA_INTERNA",
                "ALTERACAO_STATUS");
        assertThat(total(ler(CARLOS, "/api/chamados/3/historico"))).isEqualTo(7);

        String juliana = ler(JULIANA, "/api/chamados/3/historico");
        assertThat(total(juliana)).isEqualTo(6);
        assertThat(lista(juliana, "$.conteudo[*].tipoEvento")).doesNotContain("NOTA_INTERNA");

        assertThat(total(ler(ADMIN, "/api/chamados/1/historico"))).isEqualTo(8);
        assertThat(total(ler(ANA, "/api/chamados/1/historico"))).isEqualTo(7);
    }

    @Test
    void historicoTrazAutorDataEMudancaRealizada() throws Exception {
        String corpo = ler(JULIANA, "/api/chamados/3/historico");
        String evento = "$.conteudo[?(@.tipoEvento=='ALTERACAO_PRIORIDADE')]";
        assertThat(lista(corpo, evento + ".campo")).containsExactly("prioridade");
        assertThat(lista(corpo, evento + ".valorAnterior")).containsExactly("MEDIA");
        assertThat(lista(corpo, evento + ".valorNovo")).containsExactly("CRITICA");
        assertThat(lista(corpo, evento + ".descricao")).containsExactly("Impacto em todos os usuários remotos");
        assertThat(lista(corpo, evento + ".autor.nome")).containsExactly("Administrador do Sistema");
        assertThat(lista(corpo, evento + ".autor.perfil")).containsExactly("ADMIN");
        assertThat(lista(corpo, evento + ".criadoEm")).hasSize(1).doesNotContainNull();
        assertThat(corpo).doesNotContain("email").doesNotContain("senha");
    }

    @Test
    void historicoSeguraOrdemCronologicaNasDuasDirecoes() throws Exception {
        String asc = ler(ADMIN, "/api/chamados/3/historico");
        List<Instant> datasAsc = lista(asc, "$.conteudo[*].criadoEm").stream().map(d -> Instant.parse((String) d)).toList();
        assertThat(datasAsc).isSorted();
        assertThat(JsonPath.<String>read(asc, "$.conteudo[0].tipoEvento")).isEqualTo("CRIACAO");

        String desc = ler(ADMIN, "/api/chamados/3/historico", "direcao", "desc");
        List<Instant> datasDesc = lista(desc, "$.conteudo[*].criadoEm").stream().map(d -> Instant.parse((String) d)).toList();
        assertThat(datasDesc).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(JsonPath.<String>read(desc, "$.conteudo[6].tipoEvento")).isEqualTo("CRIACAO");
    }

    @Test
    void paginaOHistorico() throws Exception {
        mvc.perform(requisicao(ADMIN, "/api/chamados/3/historico", "tamanho", "3", "pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.totalElementos").value(7))
                .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @Test
    void historicoRefleteAsAcoesFeitasPelaApi() throws Exception {
        mvc.perform(put("/api/chamados/5/tecnico").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tecnicoId\":2}")).andExpect(status().isOk());
        mvc.perform(patch("/api/chamados/5/status").with(CARLOS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"EM_ATENDIMENTO\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/chamados/5/comentarios").with(CARLOS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"PUBLICO\",\"conteudo\":\"Começando a análise\"}")).andExpect(status().isCreated());

        String admin = ler(ADMIN, "/api/chamados/5/historico");
        assertThat(lista(admin, "$.conteudo[*].tipoEvento")).containsExactly(
                "CRIACAO", "ATRIBUICAO", "ALTERACAO_STATUS", "COMENTARIO");
        assertThat(lista(admin, "$.conteudo[?(@.tipoEvento=='ATRIBUICAO')].valorNovo")).containsExactly("Carlos Mendes");
        assertThat(lista(admin, "$.conteudo[?(@.tipoEvento=='ATRIBUICAO')].autor.nome")).containsExactly("Administrador do Sistema");
        assertThat(lista(admin, "$.conteudo[?(@.tipoEvento=='ALTERACAO_STATUS')].valorNovo")).containsExactly("EM_ATENDIMENTO");
        assertThat(lista(admin, "$.conteudo[?(@.tipoEvento=='COMENTARIO')].campo")).containsExactly("primeira_resposta_em");
        assertThat(total(ler(BRUNO, "/api/chamados/5/historico"))).isEqualTo(4);

        mvc.perform(post("/api/chamados/5/comentarios").with(CARLOS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"NOTA_INTERNA\",\"conteudo\":\"Reproduzi o erro\"}")).andExpect(status().isCreated());
        assertThat(total(ler(ADMIN, "/api/chamados/5/historico"))).isEqualTo(5);
        assertThat(total(ler(CARLOS, "/api/chamados/5/historico"))).isEqualTo(5);
        assertThat(total(ler(BRUNO, "/api/chamados/5/historico"))).as("solicitante não vê a nota").isEqualTo(4);
        assertThat(total(ler(BRUNO, "/api/chamados/5/comentarios"))).isEqualTo(1);
    }

    @Test
    void historicoRespeitaVisibilidadeEParametros() throws Exception {
        lerBruto(FERNANDA, "/api/chamados/3/historico").andExpect(status().isNotFound());
        lerBruto(ANA, "/api/chamados/3/historico").andExpect(status().isNotFound());
        lerBruto(ADMIN, "/api/chamados/9999/historico").andExpect(status().isNotFound());
        lerBruto(ADMIN, "/api/chamados/3/historico", "tamanho", "0").andExpect(status().isBadRequest());
        lerBruto(ADMIN, "/api/chamados/3/historico", "tamanho", "101").andExpect(status().isBadRequest());
        lerBruto(ADMIN, "/api/chamados/3/historico", "pagina", "-1").andExpect(status().isBadRequest());
        lerBruto(ADMIN, "/api/chamados/3/historico", "direcao", "x").andExpect(status().isBadRequest());
    }
}
