package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
 * Demonstração (id = número do protocolo):
 * 1 FECHADO ALTA cat1 ana/carlos | 2 RESOLVIDO BAIXA cat2 bruno/fernanda | 3 EM_ATENDIMENTO CRITICA cat3 juliana/carlos
 * 4 AGUARDANDO_USUARIO ALTA cat4 ana/fernanda | 5 ABERTO MEDIA cat5 bruno/- | 6 EM_ATENDIMENTO BAIXA cat6 juliana/carlos
 * 7 RESOLVIDO MEDIA cat7 ana/fernanda | 8 AGUARDANDO_TERCEIRO MEDIA cat1 bruno/carlos | 9 ABERTO ALTA cat3 juliana/-
 * 10 FECHADO CRITICA cat4 ana/fernanda | 11 EM_ATENDIMENTO BAIXA cat8 juliana/fernanda | 12 RESOLVIDO MEDIA cat2 ana/carlos
 * Atrasados (SLA de resolução): 3, 4 e 8. Antes de qualquer requisição que falhe, as escritas do teste vêm primeiro.
 */
@Transactional
@AutoConfigureMockMvc
class ChamadoConsultaIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    @Autowired MockMvc mvc;
    @Autowired ChamadoRepository chamados;
    @Autowired UsuarioRepository usuarios;
    @PersistenceContext EntityManager em;

    private MockHttpServletRequestBuilder requisicao(RequestPostProcessor quem, String... params) {
        MockHttpServletRequestBuilder req = get("/api/chamados").with(quem);
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return req;
    }

    private String listar(RequestPostProcessor quem, String... params) throws Exception {
        return mvc.perform(requisicao(quem, params)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions listarBruto(RequestPostProcessor quem, String... params) throws Exception {
        return mvc.perform(requisicao(quem, params));
    }

    private static Set<Integer> ids(String corpo) {
        List<Integer> lista = JsonPath.read(corpo, "$.conteudo[*].id");
        return new TreeSet<>(lista);
    }

    private void esperaIds(String corpo, Integer... esperados) {
        assertThat(ids(corpo)).containsExactlyInAnyOrder(esperados);
        assertThat(JsonPath.<Integer>read(corpo, "$.totalElementos")).isEqualTo(esperados.length);
    }

    // ---------- autenticação ----------

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/chamados")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/1")).andExpect(status().isUnauthorized());
    }

    // ---------- listagem: paginação e ordenação ----------

    @Test
    void adminListaTudoComPaginacaoPadraoEOrdemPorCriacaoDecrescente() throws Exception {
        mvc.perform(requisicao(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(12))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.conteudo.length()").value(12))
                .andExpect(jsonPath("$.conteudo[0].id").value(9))
                .andExpect(jsonPath("$.conteudo[11].id").value(1))
                .andExpect(jsonPath("$.conteudo[0].protocolo").value(matchesPattern("CT-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.conteudo[0].categoriaNome").value("Rede e conectividade"))
                .andExpect(jsonPath("$.conteudo[0].solicitanteNome").value("Juliana Rocha"))
                .andExpect(jsonPath("$.conteudo[0].tecnicoNome").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].descricao").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].solucao").doesNotExist());
    }

    @Test
    void paginaOsResultados() throws Exception {
        mvc.perform(requisicao(ADMIN, "tamanho", "5"))
                .andExpect(jsonPath("$.conteudo.length()").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.totalElementos").value(12));
        mvc.perform(requisicao(ADMIN, "tamanho", "5", "pagina", "2"))
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.pagina").value(2));
        mvc.perform(requisicao(ADMIN, "tamanho", "5", "pagina", "9"))
                .andExpect(jsonPath("$.conteudo.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(12));

        Set<Integer> todos = new TreeSet<>();
        for (int p = 0; p < 3; p++) {
            todos.addAll(ids(listar(ADMIN, "tamanho", "5", "pagina", String.valueOf(p))));
        }
        assertThat(todos).hasSize(12);
    }

    @Test
    void ordenaPorCampoPermitidoNasDuasDirecoes() throws Exception {
        mvc.perform(requisicao(ADMIN, "ordenarPor", "protocolo", "direcao", "asc"))
                .andExpect(jsonPath("$.conteudo[0].id").value(1));
        mvc.perform(requisicao(ADMIN, "ordenarPor", "protocolo", "direcao", "DESC"))
                .andExpect(jsonPath("$.conteudo[0].id").value(12));
        mvc.perform(requisicao(ADMIN, "ordenarPor", "titulo", "direcao", "asc"))
                .andExpect(jsonPath("$.conteudo[0].id").value(11));
        mvc.perform(requisicao(ADMIN, "ordenarPor", "titulo", "direcao", "desc"))
                .andExpect(jsonPath("$.conteudo[0].id").value(9));
        mvc.perform(requisicao(ADMIN, "ordenarPor", "criadoEm", "direcao", "asc"))
                .andExpect(jsonPath("$.conteudo[0].id").value(1));
        mvc.perform(requisicao(ADMIN, "ordenarPor", "atualizadoEm")).andExpect(status().isOk());
    }

    @Test
    void rejeitaParametrosDePaginacaoEOrdenacaoInvalidos() throws Exception {
        listarBruto(ADMIN, "tamanho", "0").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "tamanho", "101").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "pagina", "-1").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "pagina", "abc").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "ordenarPor", "descricao").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "ordenarPor", "solicitante.senhaHash").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "direcao", "para-cima").andExpect(status().isBadRequest());
    }

    // ---------- listagem: filtros ----------

    @Test
    void filtraPorStatus() throws Exception {
        esperaIds(listar(ADMIN, "status", "ABERTO"), 5, 9);
        esperaIds(listar(ADMIN, "status", "EM_ATENDIMENTO"), 3, 6, 11);
        esperaIds(listar(ADMIN, "status", "AGUARDANDO_USUARIO"), 4);
        esperaIds(listar(ADMIN, "status", "AGUARDANDO_TERCEIRO"), 8);
        esperaIds(listar(ADMIN, "status", "RESOLVIDO"), 2, 7, 12);
        esperaIds(listar(ADMIN, "status", "FECHADO"), 1, 10);
    }

    @Test
    void filtraPorPrioridade() throws Exception {
        esperaIds(listar(ADMIN, "prioridade", "CRITICA"), 3, 10);
        esperaIds(listar(ADMIN, "prioridade", "ALTA"), 1, 4, 9);
        esperaIds(listar(ADMIN, "prioridade", "MEDIA"), 5, 7, 8, 12);
        esperaIds(listar(ADMIN, "prioridade", "BAIXA"), 2, 6, 11);
    }

    @Test
    void filtraPorCategoria() throws Exception {
        esperaIds(listar(ADMIN, "categoriaId", "1"), 1, 8);
        esperaIds(listar(ADMIN, "categoriaId", "3"), 3, 9);
        esperaIds(listar(ADMIN, "categoriaId", "8"), 11);
        esperaIds(listar(ADMIN, "categoriaId", "9999"));
    }

    @Test
    void filtraPorTecnicoESemTecnico() throws Exception {
        esperaIds(listar(ADMIN, "tecnicoId", "2"), 1, 3, 6, 8, 12);
        esperaIds(listar(ADMIN, "tecnicoId", "3"), 2, 4, 7, 10, 11);
        esperaIds(listar(ADMIN, "semTecnico", "true"), 5, 9);
    }

    @Test
    void filtraPorProtocoloExato() throws Exception {
        String protocolo = JsonPath.read(listar(ADMIN, "ordenarPor", "protocolo", "direcao", "asc"),
                "$.conteudo[4].protocolo");
        esperaIds(listar(ADMIN, "protocolo", protocolo), 5);
        esperaIds(listar(ADMIN, "protocolo", protocolo.toLowerCase()), 5);
        esperaIds(listar(ADMIN, "protocolo", "  " + protocolo + "  "), 5);
        esperaIds(listar(ADMIN, "protocolo", protocolo.substring(0, protocolo.length() - 2)));
        esperaIds(listar(ADMIN, "protocolo", "CT-1999-000001"));
        esperaIds(listar(ADMIN, "protocolo", "   "), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void pesquisaPorTextoNoTituloOuProtocolo() throws Exception {
        esperaIds(listar(ADMIN, "q", "VPN"), 3);
        esperaIds(listar(ADMIN, "q", "vpn"), 3);
        esperaIds(listar(ADMIN, "q", "monitor"), 8);
        esperaIds(listar(ADMIN, "q", "inexistente-xyz"));
        String protocolo = JsonPath.read(listar(ADMIN, "ordenarPor", "protocolo", "direcao", "asc"),
                "$.conteudo[6].protocolo");
        esperaIds(listar(ADMIN, "q", protocolo), 7);
    }

    @Test
    void filtraPorPeriodoDeCriacao() throws Exception {
        Instant agora = Instant.now();
        String ha1000 = agora.minus(1000, ChronoUnit.MINUTES).toString();
        String ha2500 = agora.minus(2500, ChronoUnit.MINUTES).toString();
        String ha5000 = agora.minus(5000, ChronoUnit.MINUTES).toString();

        esperaIds(listar(ADMIN, "criadoDe", ha1000), 3, 5, 6, 9);
        esperaIds(listar(ADMIN, "criadoAte", ha1000), 1, 2, 4, 7, 8, 10, 11, 12);
        esperaIds(listar(ADMIN, "criadoDe", ha5000, "criadoAte", ha2500), 2, 8, 11);
        esperaIds(listar(ADMIN, "criadoDe", agora.plus(1, ChronoUnit.DAYS).toString()));
    }

    @Test
    void filtraSomenteComSlaDeResolucaoEstourado() throws Exception {
        esperaIds(listar(ADMIN, "atrasados", "true"), 3, 4, 8);
        esperaIds(listar(ADMIN, "atrasados", "false"), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void combinaFiltros() throws Exception {
        esperaIds(listar(ADMIN, "status", "EM_ATENDIMENTO", "tecnicoId", "2"), 3, 6);
        esperaIds(listar(ADMIN, "status", "EM_ATENDIMENTO", "tecnicoId", "2", "prioridade", "CRITICA"), 3);
        esperaIds(listar(ADMIN, "status", "RESOLVIDO", "categoriaId", "2"), 2, 12);
        esperaIds(listar(ADMIN, "status", "ABERTO", "tecnicoId", "2"));
        esperaIds(listar(ADMIN, "atrasados", "true", "tecnicoId", "3"), 4);
        esperaIds(listar(ADMIN, "q", "vpn", "status", "FECHADO"));
    }

    @Test
    void rejeitaFiltrosInvalidos() throws Exception {
        listarBruto(ADMIN, "status", "XYZ").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "prioridade", "URGENTE").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "categoriaId", "abc").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "tecnicoId", "abc").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "criadoDe", "ontem").andExpect(status().isBadRequest());
        listarBruto(ADMIN, "protocolo", "x".repeat(21)).andExpect(status().isBadRequest());
        listarBruto(ADMIN, "q", "x".repeat(101)).andExpect(status().isBadRequest());
        listarBruto(ADMIN, "criadoDe", "2026-02-01T00:00:00Z", "criadoAte", "2026-01-01T00:00:00Z")
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- restrição por perfil ----------

    @Test
    void solicitanteVeSomenteOsProprios() throws Exception {
        esperaIds(listar(ANA), 1, 4, 7, 10, 12);
        esperaIds(listar(JULIANA), 3, 6, 9, 11);

        esperaIds(listar(ANA, "status", "FECHADO"), 1, 10);
        esperaIds(listar(ANA, "tecnicoId", "3"), 4, 7, 10);
        esperaIds(listar(ANA, "atrasados", "true"), 4);
        esperaIds(listar(ANA, "categoriaId", "3"));
        esperaIds(listar(ANA, "q", "vpn"));
    }

    @Test
    void solicitanteNaoAmpliaOEscopoPorParametros() throws Exception {
        esperaIds(listar(ANA, "solicitanteId", "5"), 1, 4, 7, 10, 12);
        esperaIds(listar(ANA, "protocolo", "CT-2026-000002"));
        String protocoloDeOutro = JsonPath.read(
                listar(ADMIN, "ordenarPor", "protocolo", "direcao", "asc"), "$.conteudo[1].protocolo");
        esperaIds(listar(ANA, "protocolo", protocoloDeOutro));
        esperaIds(listar(ANA, "semTecnico", "true"));
    }

    @Test
    void tecnicoVeAtribuidosFilaEProprios() throws Exception {
        esperaIds(listar(CARLOS), 1, 3, 5, 6, 8, 9, 12);
        esperaIds(listar(FERNANDA), 2, 4, 5, 7, 9, 10, 11);

        esperaIds(listar(CARLOS, "semTecnico", "true"), 5, 9);
        esperaIds(listar(CARLOS, "status", "ABERTO"), 5, 9);
        esperaIds(listar(CARLOS, "status", "FECHADO"), 1);
        esperaIds(listar(CARLOS, "tecnicoId", "2"), 1, 3, 6, 8, 12);
        esperaIds(listar(CARLOS, "tecnicoId", "3"));
        esperaIds(listar(CARLOS, "atrasados", "true"), 3, 8);
        esperaIds(listar(CARLOS, "categoriaId", "1"), 1, 8);
    }

    @Test
    void tecnicoVeChamadoQueAbriuMesmoAtribuidoAOutroEOutroTecnicoNao() throws Exception {
        Usuario terceiro = new Usuario();
        terceiro.setNome("Terceiro Técnico");
        terceiro.setEmail("terceiro@chamati.local");
        terceiro.setSenhaHash("$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW");
        terceiro.setPerfil(Perfil.TECNICO);
        terceiro.setAtivo(true);
        usuarios.saveAndFlush(terceiro);
        RequestPostProcessor tecnicoTerceiro = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, terceiro.getId());

        String criado = mvc.perform(post("/api/chamados").with(CARLOS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Aberto pelo Carlos\",\"descricao\":\"D\",\"categoriaId\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(criado, "$.id");
        var chamado = chamados.findById(id.longValue()).orElseThrow();
        chamado.setTecnico(usuarios.getReferenceById(3L));
        chamado.setAtribuidoEm(Instant.now());
        chamados.saveAndFlush(chamado);

        assertThat(ids(listar(CARLOS))).contains(id);
        assertThat(ids(listar(FERNANDA))).contains(id);
        assertThat(ids(listar(tecnicoTerceiro))).doesNotContain(id).containsExactlyInAnyOrder(5, 9);
        assertThat(ids(listar(ADMIN, "tamanho", "100"))).contains(id);
        mvc.perform(get("/api/chamados/" + id).with(CARLOS)).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/" + id).with(tecnicoTerceiro)).andExpect(status().isNotFound());
    }

    // ---------- detalhe ----------

    @Test
    void detalheCompletoParaAdmin() throws Exception {
        mvc.perform(get("/api/chamados/3").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.protocolo").value(matchesPattern("CT-\\d{4}-000003")))
                .andExpect(jsonPath("$.titulo").value("Sem acesso à VPN"))
                .andExpect(jsonPath("$.descricao").isNotEmpty())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.prioridade").value("CRITICA"))
                .andExpect(jsonPath("$.categoria.nome").value("Rede e conectividade"))
                .andExpect(jsonPath("$.subcategoria.nome").value("VPN"))
                .andExpect(jsonPath("$.solicitante.id").value(6))
                .andExpect(jsonPath("$.solicitante.nome").value("Juliana Rocha"))
                .andExpect(jsonPath("$.tecnico.nome").value("Carlos Mendes"))
                .andExpect(jsonPath("$.atribuidoEm").isNotEmpty())
                .andExpect(jsonPath("$.primeiraRespostaEm").isNotEmpty())
                .andExpect(jsonPath("$.resolvidoEm").doesNotExist())
                .andExpect(jsonPath("$.totalReaberturas").value(0))
                .andExpect(jsonPath("$.slaResolucaoLimite").isNotEmpty())
                .andExpect(jsonPath("$.situacaoSlaPrimeiraResposta").value("DENTRO_DO_PRAZO"))
                .andExpect(jsonPath("$.situacaoSlaResolucao").value("ATRASADO"))
                .andExpect(jsonPath("$.versao").value(0))
                .andExpect(jsonPath("$.solicitante.email").doesNotExist())
                .andExpect(jsonPath("$.solicitante.senhaHash").doesNotExist());
    }

    @Test
    void detalheDeChamadoFechadoResolvidoEReaberto() throws Exception {
        mvc.perform(get("/api/chamados/1").with(ADMIN))
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.solucao").isNotEmpty())
                .andExpect(jsonPath("$.resolvidoEm").isNotEmpty())
                .andExpect(jsonPath("$.fechadoEm").isNotEmpty())
                .andExpect(jsonPath("$.situacaoSlaResolucao").value("DENTRO_DO_PRAZO"))
                .andExpect(jsonPath("$.situacaoSlaPrimeiraResposta").value("DENTRO_DO_PRAZO"));
        mvc.perform(get("/api/chamados/11").with(ADMIN))
                .andExpect(jsonPath("$.totalReaberturas").value(1))
                .andExpect(jsonPath("$.reabertoEm").isNotEmpty())
                .andExpect(jsonPath("$.subcategoria").doesNotExist())
                .andExpect(jsonPath("$.situacaoSlaResolucao").value("EM_ANDAMENTO"));
        mvc.perform(get("/api/chamados/5").with(ADMIN))
                .andExpect(jsonPath("$.tecnico").doesNotExist())
                .andExpect(jsonPath("$.atribuidoEm").doesNotExist())
                .andExpect(jsonPath("$.primeiraRespostaEm").doesNotExist())
                .andExpect(jsonPath("$.situacaoSlaPrimeiraResposta").value("EM_ANDAMENTO"));
    }

    @Test
    void detalheRespeitaAVisibilidadeDeCadaPerfil() throws Exception {
        mvc.perform(get("/api/chamados/1").with(ANA)).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/3").with(CARLOS)).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/5").with(CARLOS)).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/5").with(FERNANDA)).andExpect(status().isOk());

        mvc.perform(get("/api/chamados/2").with(ANA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/1").with(JULIANA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/4").with(CARLOS)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/1").with(FERNANDA)).andExpect(status().isNotFound());
    }

    @Test
    void detalheInexistenteOuInvalido() throws Exception {
        mvc.perform(get("/api/chamados/9999").with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/abc").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void listagemEDetalheUsamAMesmaRegraDeVisibilidade() throws Exception {
        for (RequestPostProcessor quem : List.of(ADMIN, CARLOS, FERNANDA, ANA, JULIANA)) {
            Set<Integer> visiveis = ids(listar(quem, "tamanho", "100"));
            for (int id = 1; id <= 12; id++) {
                int esperado = visiveis.contains(id) ? 200 : 404;
                assertThat(mvc.perform(get("/api/chamados/" + id).with(quem)).andReturn().getResponse().getStatus())
                        .as("chamado %d", id).isEqualTo(esperado);
            }
        }
    }

    // ---------- ponta a ponta com token real ----------

    @Test
    void fluxoComLoginETokenReal() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.souza@chamati.local\",\"senha\":\"Senha@123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(login, "$.accessToken");

        String lista = mvc.perform(get("/api/chamados").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        esperaIds(lista, 1, 4, 7, 10, 12);
        mvc.perform(get("/api/chamados/1").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/2").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }
}
