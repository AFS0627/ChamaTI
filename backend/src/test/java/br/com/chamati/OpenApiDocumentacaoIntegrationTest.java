package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Confere o OpenAPI gerado (/v3/api-docs) contra o que o código realmente expõe: rotas, parâmetros, corpos,
 * códigos de resposta, exigência de token e propriedades dos DTOs. Não substitui os testes de comportamento;
 * garante que a documentação não se afaste deles.
 */
@AutoConfigureMockMvc
class OpenApiDocumentacaoIntegrationTest extends IntegrationTestBase {

    private static final String PACOTE_CONTROLLERS = "br.com.chamati.controller";
    private static final List<String> VERBOS = List.of("get", "put", "post", "delete", "patch", "head", "options", "trace");
    private static final ParameterNameDiscoverer NOMES = new DefaultParameterNameDiscoverer();

    /** Códigos de sucesso que não são 200. Todas as demais operações devem documentar 200. */
    private static final Map<String, String> SUCESSO_DIFERENTE_DE_200 = Map.of(
            "POST /api/usuarios", "201",
            "POST /api/categorias", "201",
            "POST /api/categorias/{categoriaId}/subcategorias", "201",
            "POST /api/chamados", "201",
            "POST /api/chamados/{chamadoId}/comentarios", "201",
            "POST /api/chamados/{chamadoId}/anexos", "201",
            "POST /api/auth/logout", "204",
            "POST /api/auth/logout-todos", "204");

    /** Operações cujo sucesso devolve um arquivo e não JSON. */
    private static final Set<String> DOWNLOADS = Set.of("GET /api/chamados/{chamadoId}/anexos/{anexoId}");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    // ---------- utilitários ----------

    private JsonNode doc() throws Exception {
        String corpo = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(corpo);
    }

    private static Map<String, JsonNode> operacoesDocumentadas(JsonNode doc) {
        Map<String, JsonNode> ops = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> paths = doc.get("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> p = paths.next();
            for (String verbo : VERBOS) {
                JsonNode operacao = p.getValue().get(verbo);
                if (operacao != null) {
                    ops.put(verbo.toUpperCase() + " " + p.getKey(), operacao);
                }
            }
        }
        return ops;
    }

    private Map<String, HandlerMethod> operacoesDoCodigo() {
        Map<String, HandlerMethod> ops = new TreeMap<>();
        handlerMapping.getHandlerMethods().forEach((info, metodo) -> {
            if (!metodo.getBeanType().getPackageName().equals(PACOTE_CONTROLLERS)) {
                return;
            }
            Set<String> padroes = info.getPathPatternsCondition().getPatternValues();
            for (RequestMethod verbo : info.getMethodsCondition().getMethods()) {
                for (String padrao : padroes) {
                    ops.put(verbo + " " + padrao, metodo);
                }
            }
        });
        return ops;
    }

    private static Set<String> parametrosEsperados(HandlerMethod metodo) {
        Set<String> esperados = new TreeSet<>();
        for (MethodParameter mp : metodo.getMethodParameters()) {
            mp.initParameterNameDiscovery(NOMES);
            RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
            PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
            if (rp != null) {
                String nome = !rp.name().isEmpty() ? rp.name() : !rp.value().isEmpty() ? rp.value() : mp.getParameterName();
                boolean obrigatorio = rp.required() && ValueConstants.DEFAULT_NONE.equals(rp.defaultValue());
                esperados.add("query:" + nome + ":" + obrigatorio);
            } else if (pv != null) {
                String nome = !pv.name().isEmpty() ? pv.name() : !pv.value().isEmpty() ? pv.value() : mp.getParameterName();
                esperados.add("path:" + nome + ":true");
            }
        }
        return esperados;
    }

    private static Set<String> parametrosDocumentados(JsonNode operacao) {
        Set<String> doc = new TreeSet<>();
        for (JsonNode p : operacao.path("parameters")) {
            doc.add(p.get("in").asText() + ":" + p.get("name").asText() + ":" + p.path("required").asBoolean(false));
        }
        return doc;
    }

    private static JsonNode resolver(JsonNode doc, JsonNode schema) {
        JsonNode ref = schema.get("$ref");
        if (ref == null) {
            return schema;
        }
        String nome = ref.asText().substring("#/components/schemas/".length());
        return doc.path("components").path("schemas").path(nome);
    }

    private static Set<String> nomes(JsonNode objeto) {
        Set<String> nomes = new TreeSet<>();
        objeto.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }

    private static Set<String> textos(JsonNode array) {
        Set<String> textos = new TreeSet<>();
        array.forEach(n -> textos.add(n.asText()));
        return textos;
    }

    private static JsonNode parametro(JsonNode operacao, String nome) {
        for (JsonNode p : operacao.path("parameters")) {
            if (nome.equals(p.get("name").asText())) {
                return p;
            }
        }
        throw new AssertionError("Parâmetro não documentado: " + nome);
    }

    private static void coletarReferencias(JsonNode no, List<String> destino) {
        if (no.isObject()) {
            no.fields().forEachRemaining(e -> {
                if (e.getKey().equals("$ref") && e.getValue().isTextual()) {
                    destino.add(e.getValue().asText());
                } else {
                    coletarReferencias(e.getValue(), destino);
                }
            });
        } else if (no.isArray()) {
            no.forEach(n -> coletarReferencias(n, destino));
        }
    }

    // ---------- acesso e metadados ----------

    @Test
    void documentacaoEPublicaEDescreveAApi() throws Exception {
        JsonNode doc = doc();

        assertThat(doc.get("openapi").asText()).startsWith("3.");
        assertThat(doc.at("/info/title").asText()).isEqualTo("chamaTI API");
        assertThat(doc.at("/info/description").asText()).contains("POST /api/auth/login");
        mvc.perform(get("/swagger-ui.html")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isNotIn(401, 403, 404));
    }

    @Test
    void esquemaBearerJwtEDeclaradoEExigidoPorPadrao() throws Exception {
        JsonNode doc = doc();

        JsonNode esquema = doc.at("/components/securitySchemes/bearerAuth");
        assertThat(esquema.get("type").asText()).isEqualTo("http");
        assertThat(esquema.get("scheme").asText()).isEqualTo("bearer");
        assertThat(esquema.get("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(doc.get("security").toString()).contains("bearerAuth");
    }

    @Test
    void documentacaoNaoVazaCamposSensiveis() throws Exception {
        String corpo = doc().toString();

        assertThat(corpo).doesNotContain("senhaHash").doesNotContain("senha_hash").doesNotContain("tokenHash");
    }

    // ---------- correspondência com o código ----------

    @Test
    void todosOsEndpointsDoCodigoEstaoDocumentadosEViceVersa() throws Exception {
        Set<String> codigo = operacoesDoCodigo().keySet();
        Set<String> documentados = operacoesDocumentadas(doc()).keySet();

        assertThat(codigo).as("endpoints do código").hasSizeGreaterThanOrEqualTo(30);
        assertThat(documentados).as("endpoints documentados").isEqualTo(codigo);
    }

    @Test
    void parametrosECorpoDocumentadosCorrespondemAoCodigo() throws Exception {
        Map<String, JsonNode> documentadas = operacoesDocumentadas(doc());

        for (Map.Entry<String, HandlerMethod> e : operacoesDoCodigo().entrySet()) {
            JsonNode operacao = documentadas.get(e.getKey());
            assertThat(operacao).as("operação %s", e.getKey()).isNotNull();
            assertThat(parametrosDocumentados(operacao)).as("parâmetros de %s", e.getKey())
                    .isEqualTo(parametrosEsperados(e.getValue()));

            MethodParameter corpo = Arrays.stream(e.getValue().getMethodParameters())
                    .filter(mp -> mp.hasParameterAnnotation(RequestBody.class) || mp.hasParameterAnnotation(RequestPart.class))
                    .findFirst().orElse(null);
            assertThat(operacao.has("requestBody")).as("corpo de %s", e.getKey()).isEqualTo(corpo != null);
            if (corpo != null) {
                RequestBody corpoJson = corpo.getParameterAnnotation(RequestBody.class);
                boolean obrigatorio = corpoJson != null ? corpoJson.required()
                        : corpo.getParameterAnnotation(RequestPart.class).required();
                assertThat(operacao.at("/requestBody/required").asBoolean(false))
                        .as("corpo obrigatório em %s", e.getKey()).isEqualTo(obrigatorio);
            }
        }
    }

    @Test
    void cadaOperacaoDocumentaExatamenteUmaRespostaDeSucessoComOCodigoCerto() throws Exception {
        for (Map.Entry<String, JsonNode> e : operacoesDocumentadas(doc()).entrySet()) {
            Set<String> sucessos = nomes(e.getValue().get("responses")).stream()
                    .filter(c -> c.startsWith("2")).collect(Collectors.toCollection(TreeSet::new));
            String esperado = SUCESSO_DIFERENTE_DE_200.getOrDefault(e.getKey(), "200");

            assertThat(sucessos).as("respostas 2xx de %s", e.getKey()).containsExactly(esperado);

            JsonNode resposta = e.getValue().at("/responses/" + esperado);
            if (esperado.equals("204")) {
                assertThat(resposta.has("content")).as("%s não tem corpo", e.getKey()).isFalse();
            } else if (DOWNLOADS.contains(e.getKey())) {
                assertThat(resposta.at("/content/application~1octet-stream/schema/format").asText())
                        .as("download de %s", e.getKey()).isEqualTo("binary");
            } else {
                assertThat(resposta.at("/content/application~1json/schema").isMissingNode())
                        .as("schema do sucesso de %s", e.getKey()).isFalse();
            }
        }
    }

    @Test
    void todaReferenciaApontaParaUmSchemaExistente() throws Exception {
        JsonNode doc = doc();
        List<String> referencias = new ArrayList<>();
        coletarReferencias(doc, referencias);

        assertThat(referencias).isNotEmpty();
        for (String ref : referencias) {
            assertThat(ref).startsWith("#/components/schemas/");
            assertThat(doc.at("/components/schemas/" + ref.substring("#/components/schemas/".length())).isMissingNode())
                    .as("referência %s", ref).isFalse();
        }
    }

    @Test
    void propriedadesDosSchemasCorrespondemAosRecordsDeDto() throws Exception {
        JsonNode schemas = doc().at("/components/schemas");
        List<String> conferidos = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> it = schemas.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> s = it.next();
            Class<?> tipo;
            try {
                tipo = Class.forName("br.com.chamati.dto." + s.getKey());
            } catch (ClassNotFoundException e) {
                continue;
            }
            if (!tipo.isRecord()) {
                continue;
            }
            Set<String> esperadas = Arrays.stream(tipo.getRecordComponents()).map(RecordComponent::getName)
                    .collect(Collectors.toCollection(TreeSet::new));
            assertThat(nomes(s.getValue().path("properties"))).as("propriedades de %s", s.getKey()).isEqualTo(esperadas);
            conferidos.add(s.getKey());
        }

        assertThat(conferidos).contains("ChamadoResponse", "ChamadoCriarRequest", "TokenResponse", "UsuarioResponse",
                "ErroResponse", "LoginRequest", "ComentarioResponse", "HistoricoResponse", "PoliticaSlaResponse");
    }

    @Test
    void enumsDocumentadosSaoOsDoCodigo() throws Exception {
        JsonNode doc = doc();
        JsonNode listagem = operacoesDocumentadas(doc).get("GET /api/chamados");

        JsonNode status = resolver(doc, parametro(listagem, "status").get("schema"));
        JsonNode prioridade = resolver(doc, parametro(listagem, "prioridade").get("schema"));

        assertThat(textos(status.get("enum"))).isEqualTo(Arrays.stream(StatusChamado.values()).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new)));
        assertThat(textos(prioridade.get("enum"))).isEqualTo(Arrays.stream(Prioridade.values()).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    @Test
    void restricoesDeValidacaoDoCodigoAparecemNosSchemasEParametros() throws Exception {
        JsonNode doc = doc();
        JsonNode login = doc.at("/components/schemas/LoginRequest");
        JsonNode abertura = doc.at("/components/schemas/ChamadoCriarRequest");
        JsonNode listagem = operacoesDocumentadas(doc).get("GET /api/chamados");

        assertThat(textos(login.get("required"))).contains("email", "senha");
        assertThat(login.at("/properties/senha/maxLength").asInt()).isEqualTo(72);
        assertThat(login.at("/properties/email/maxLength").asInt()).isEqualTo(160);
        assertThat(textos(abertura.get("required"))).contains("titulo", "descricao", "categoriaId");
        assertThat(abertura.at("/properties/titulo/maxLength").asInt()).isEqualTo(150);
        assertThat(abertura.at("/properties/descricao/maxLength").asInt()).isEqualTo(10000);

        JsonNode tamanho = resolver(doc, parametro(listagem, "tamanho").get("schema"));
        assertThat(tamanho.path("default").asInt()).isEqualTo(20);
        assertThat(tamanho.path("minimum").asInt()).isEqualTo(1);
        assertThat(tamanho.path("maximum").asInt()).isEqualTo(100);
        JsonNode pagina = resolver(doc, parametro(listagem, "pagina").get("schema"));
        assertThat(pagina.path("default").asInt()).isEqualTo(0);
        assertThat(pagina.path("minimum").asInt()).isEqualTo(0);
        assertThat(resolver(doc, parametro(listagem, "ordenarPor").get("schema")).path("default").asText())
                .isEqualTo("criadoEm");
    }

    // ---------- autenticação e respostas de erro ----------

    @Test
    void operacoesDocumentadasComoPublicasSaoExatamenteAsQueNaoExigemToken() throws Exception {
        Set<String> publicas = new TreeSet<>();

        for (Map.Entry<String, JsonNode> e : operacoesDocumentadas(doc()).entrySet()) {
            String[] chave = e.getKey().split(" ", 2);
            boolean documentadaComoPublica = e.getValue().has("security") && e.getValue().get("security").isEmpty();
            int resposta = mvc.perform(request(HttpMethod.valueOf(chave[0]), chave[1].replaceAll("\\{[^}]+}", "1"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();

            assertThat(resposta != 401).as("sem token, %s respondeu %s", e.getKey(), resposta)
                    .isEqualTo(documentadaComoPublica);
            if (documentadaComoPublica) {
                publicas.add(e.getKey());
            }
        }

        assertThat(publicas).containsExactly("POST /api/auth/login", "POST /api/auth/logout", "POST /api/auth/refresh");
    }

    @Test
    void respostasDeErroPadraoEEspecificasEstaoDocumentadas() throws Exception {
        Map<String, JsonNode> ops = operacoesDocumentadas(doc());

        assertThat(nomes(ops.get("GET /api/usuarios").get("responses"))).contains("200", "400", "401", "403");
        assertThat(ops.get("GET /api/usuarios").at("/responses/403").has("content")).isTrue();
        assertThat(nomes(ops.get("PUT /api/chamados/{id}/tecnico").get("responses")))
                .contains("200", "400", "401", "403", "404", "409", "422");
        assertThat(nomes(ops.get("POST /api/chamados/{chamadoId}/comentarios").get("responses")))
                .contains("201", "400", "401", "403", "404", "409", "422");
        assertThat(nomes(ops.get("POST /api/chamados/{chamadoId}/anexos").get("responses")))
                .contains("201", "400", "401", "404", "413", "422");
        assertThat(nomes(ops.get("POST /api/auth/login").get("responses"))).contains("200", "400", "401", "429")
                .doesNotContain("403");
        assertThat(ops.get("POST /api/auth/login").at("/responses/429/headers").has("Retry-After")).isTrue();
        assertThat(nomes(ops.get("GET /api/auth/me").get("responses"))).contains("200", "401").doesNotContain("400");
        assertThat(nomes(ops.get("GET /api/categorias/{id}").get("responses"))).contains("404").doesNotContain("403");

        for (Map.Entry<String, JsonNode> e : ops.entrySet()) {
            for (String codigo : List.of("400", "401", "403", "404", "409", "413", "422", "429")) {
                JsonNode resposta = e.getValue().at("/responses/" + codigo);
                if (!resposta.isMissingNode()) {
                    assertThat(resposta.at("/content/application~1json/schema/$ref").asText())
                            .as("%s %s", e.getKey(), codigo).endsWith("/ErroResponse");
                }
            }
        }
    }

    @Test
    void modeloDeErroDocumentadoEOMesmoQueAApiDevolve() throws Exception {
        JsonNode erro = doc().at("/components/schemas/ErroResponse/properties");
        assertThat(nomes(erro)).containsExactly("campos", "erro", "mensagem", "status", "timestamp");

        mvc.perform(get("/api/chamados/999999").with(UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro").value("Not Found"))
                .andExpect(jsonPath("$.mensagem").value("Chamado não encontrado."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void respostas401E403TemOMesmoModeloDeErroDocumentado() throws Exception {
        mvc.perform(get("/api/usuarios").with(UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Forbidden"))
                .andExpect(jsonPath("$.mensagem").value("Seu perfil não tem permissão para esta operação."))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Unauthorized"))
                .andExpect(jsonPath("$.mensagem").value("Token ausente, inválido ou expirado."));
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer token.invalido.qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").value("Token ausente, inválido ou expirado."));
    }
}
