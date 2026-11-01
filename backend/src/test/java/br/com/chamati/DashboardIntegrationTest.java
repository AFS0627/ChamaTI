package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.enums.Perfil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumo do painel sobre a demonstração (12 chamados): 1 FECHADO | 2 RESOLVIDO | 3 EM_ATENDIMENTO CRITICA tec2 |
 * 4 AGUARDANDO_USUARIO ALTA tec3 | 5 ABERTO MEDIA sem técnico | 6 EM_ATENDIMENTO BAIXA tec2 | 7 RESOLVIDO |
 * 8 AGUARDANDO_TERCEIRO MEDIA tec2 | 9 ABERTO ALTA sem técnico | 10 FECHADO | 11 EM_ATENDIMENTO BAIXA tec3 | 12 RESOLVIDO.
 * Solicitantes: Ana (4) = 1, 4, 7, 10, 12; Bruno (5) = 2, 5, 8; Juliana (6) = 3, 6, 9, 11.
 */
@Transactional
@AutoConfigureMockMvc
class DashboardIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode resumo(RequestPostProcessor quem, String dias) throws Exception {
        var req = get("/api/dashboard/resumo").with(quem);
        if (dias != null) {
            req = req.param("dias", dias);
        }
        String corpo = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(corpo);
    }

    /** Chamados em atraso conforme a listagem (mesma visibilidade), descartando os já resolvidos. */
    private long atrasadosEmAbertoPelaListagem(RequestPostProcessor quem) throws Exception {
        String corpo = mvc.perform(get("/api/chamados").param("atrasados", "true").param("tamanho", "100").with(quem))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long total = 0;
        for (JsonNode c : json.readTree(corpo).get("conteudo")) {
            String s = c.get("status").asText();
            if (!s.equals("RESOLVIDO") && !s.equals("FECHADO")) {
                total++;
            }
        }
        return total;
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminVeTodosOsChamadosComTodasAsChaves() throws Exception {
        JsonNode r = resumo(ADMIN, null);

        assertThat(r.get("periodoDias").asInt()).isEqualTo(30);
        assertThat(r.get("totais").get("total").asLong()).isEqualTo(12);
        assertThat(r.get("totais").get("emAberto").asLong()).isEqualTo(7);
        assertThat(r.get("totais").get("semTecnico").asLong()).isEqualTo(2);
        assertThat(r.get("totais").get("atribuidosAMim").asLong()).isZero();
        JsonNode porStatus = r.get("porStatus");
        assertThat(porStatus.get("ABERTO").asLong()).isEqualTo(2);
        assertThat(porStatus.get("EM_ATENDIMENTO").asLong()).isEqualTo(3);
        assertThat(porStatus.get("AGUARDANDO_USUARIO").asLong()).isEqualTo(1);
        assertThat(porStatus.get("AGUARDANDO_TERCEIRO").asLong()).isEqualTo(1);
        assertThat(porStatus.get("RESOLVIDO").asLong()).isEqualTo(3);
        assertThat(porStatus.get("FECHADO").asLong()).isEqualTo(2);
        JsonNode porPrioridade = r.get("emAbertoPorPrioridade");
        assertThat(porPrioridade.get("CRITICA").asLong()).isEqualTo(1);
        assertThat(porPrioridade.get("ALTA").asLong()).isEqualTo(2);
        assertThat(porPrioridade.get("MEDIA").asLong()).isEqualTo(2);
        assertThat(porPrioridade.get("BAIXA").asLong()).isEqualTo(2);
    }

    @Test
    void atrasosCoincidemComAListagemEPrimeiraRespostaNaoExcedeOsEmAberto() throws Exception {
        JsonNode r = resumo(ADMIN, null);

        long emAtraso = r.get("totais").get("emAtrasoResolucao").asLong();
        assertThat(emAtraso).isPositive().isEqualTo(atrasadosEmAbertoPelaListagem(ADMIN));
        assertThat(r.get("totais").get("emAtrasoPrimeiraResposta").asLong())
                .isBetween(0L, r.get("totais").get("emAberto").asLong());
    }

    @Test
    void categoriasEmAbertoSomamOTotalEVemEmOrdemDecrescente() throws Exception {
        JsonNode r = resumo(ADMIN, null);

        long soma = 0;
        long anterior = Long.MAX_VALUE;
        for (JsonNode c : r.get("emAbertoPorCategoria")) {
            long total = c.get("total").asLong();
            assertThat(total).isLessThanOrEqualTo(anterior);
            assertThat(c.get("nome").asText()).isNotBlank();
            anterior = total;
            soma += total;
        }
        assertThat(soma).isEqualTo(r.get("totais").get("emAberto").asLong());
    }

    @Test
    void periodoConsideraAJanelaDeDias() throws Exception {
        JsonNode ano = resumo(ADMIN, "365");
        JsonNode dia = resumo(ADMIN, "1");

        assertThat(ano.get("periodoDias").asInt()).isEqualTo(365);
        assertThat(ano.get("periodo").get("abertos").asLong()).isEqualTo(12);
        assertThat(ano.get("periodo").get("resolvidos").asLong()).isEqualTo(5);
        assertThat(dia.get("periodo").get("abertos").asLong()).isLessThanOrEqualTo(12);
        assertThat(dia.get("periodo").get("resolvidos").asLong()).isLessThanOrEqualTo(5);
        assertThat(dia.get("periodo").get("desde").asText()).isNotBlank();
    }

    @Test
    void solicitanteVeSomenteOsSeusChamadosEOsDemaisStatusFicamZerados() throws Exception {
        JsonNode r = resumo(ANA, null);

        assertThat(r.get("totais").get("total").asLong()).isEqualTo(5);
        assertThat(r.get("totais").get("emAberto").asLong()).isEqualTo(1);
        assertThat(r.get("totais").get("semTecnico").asLong()).isZero();
        assertThat(r.get("porStatus").get("AGUARDANDO_USUARIO").asLong()).isEqualTo(1);
        assertThat(r.get("porStatus").get("RESOLVIDO").asLong()).isEqualTo(2);
        assertThat(r.get("porStatus").get("FECHADO").asLong()).isEqualTo(2);
        assertThat(r.get("porStatus").get("ABERTO").asLong()).isZero();
        assertThat(r.get("porStatus").get("EM_ATENDIMENTO").asLong()).isZero();
        assertThat(r.get("emAbertoPorPrioridade").get("ALTA").asLong()).isEqualTo(1);
        assertThat(r.get("emAbertoPorCategoria")).hasSize(1);
    }

    @Test
    void tecnicoVeAtribuidosFilaEOsQueAbriu() throws Exception {
        JsonNode carlos = resumo(CARLOS, null);
        JsonNode fernanda = resumo(FERNANDA, null);

        // Carlos: atribuídos 1, 3, 6, 8, 12 + fila 5, 9
        assertThat(carlos.get("totais").get("total").asLong()).isEqualTo(7);
        assertThat(carlos.get("totais").get("emAberto").asLong()).isEqualTo(5);
        assertThat(carlos.get("totais").get("semTecnico").asLong()).isEqualTo(2);
        assertThat(carlos.get("totais").get("atribuidosAMim").asLong()).isEqualTo(3);
        assertThat(carlos.get("totais").get("emAtrasoResolucao").asLong()).isEqualTo(atrasadosEmAbertoPelaListagem(CARLOS));
        // Fernanda: atribuídos 2, 4, 7, 10, 11 + fila 5, 9
        assertThat(fernanda.get("totais").get("total").asLong()).isEqualTo(7);
        assertThat(fernanda.get("totais").get("atribuidosAMim").asLong()).isEqualTo(2);
        assertThat(fernanda.get("totais").get("emAtrasoResolucao").asLong()).isEqualTo(atrasadosEmAbertoPelaListagem(FERNANDA));
    }

    @Test
    void janelaForaDoIntervaloPermitidoResponde400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("dias", "0").with(ADMIN))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/dashboard/resumo").param("dias", "366").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/resumo").param("dias", "abc").with(ADMIN)).andExpect(status().isBadRequest());
    }
}
