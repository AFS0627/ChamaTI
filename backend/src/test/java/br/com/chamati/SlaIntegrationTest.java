package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.entity.Chamado;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.SituacaoSla;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.service.SlaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class SlaIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor TECNICO = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor SOLICITANTE = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);

    @Autowired MockMvc mvc;
    @Autowired SlaService sla;
    @PersistenceContext EntityManager em;

    private ResultActions atualizar(String prioridade, RequestPostProcessor quem, String json) throws Exception {
        return mvc.perform(put("/api/politicas-sla/" + prioridade).with(quem)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void listaPoliticasDaMaisUrgenteParaAMenosUrgente() throws Exception {
        mvc.perform(get("/api/politicas-sla")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/politicas-sla").with(SOLICITANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].prioridade").value("CRITICA"))
                .andExpect(jsonPath("$[0].prazoPrimeiraRespostaMin").value(15))
                .andExpect(jsonPath("$[0].prazoResolucaoMin").value(240))
                .andExpect(jsonPath("$[1].prioridade").value("ALTA"))
                .andExpect(jsonPath("$[2].prioridade").value("MEDIA"))
                .andExpect(jsonPath("$[3].prioridade").value("BAIXA"))
                .andExpect(jsonPath("$[3].prazoResolucaoMin").value(4320));
    }

    @Test
    void somenteAdminAlteraPoliticas() throws Exception {
        String json = "{\"prazoPrimeiraRespostaMin\":45,\"prazoResolucaoMin\":600}";
        atualizar("ALTA", TECNICO, json).andExpect(status().isForbidden());
        atualizar("ALTA", SOLICITANTE, json).andExpect(status().isForbidden());
        atualizar("ALTA", ADMIN, json).andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.prazoPrimeiraRespostaMin").value(45))
                .andExpect(jsonPath("$.prazoResolucaoMin").value(600));
        mvc.perform(get("/api/politicas-sla").with(SOLICITANTE)).andExpect(jsonPath("$[1].prazoResolucaoMin").value(600));
    }

    @Test
    void validaPrazos() throws Exception {
        atualizar("MEDIA", ADMIN, "{\"prazoPrimeiraRespostaMin\":300,\"prazoResolucaoMin\":200}")
                .andExpect(status().isUnprocessableEntity());                  // resolução < primeira resposta
        atualizar("MEDIA", ADMIN, "{\"prazoPrimeiraRespostaMin\":200,\"prazoResolucaoMin\":200}")
                .andExpect(status().isOk());                                    // igual é permitido (CHECK >=)
        atualizar("MEDIA", ADMIN, "{\"prazoPrimeiraRespostaMin\":0,\"prazoResolucaoMin\":100}")
                .andExpect(status().isBadRequest());
        atualizar("MEDIA", ADMIN, "{\"prazoPrimeiraRespostaMin\":10}").andExpect(status().isBadRequest());
        atualizar("MEDIA", ADMIN, "{\"prazoPrimeiraRespostaMin\":10,\"prazoResolucaoMin\":9999999}")
                .andExpect(status().isBadRequest());
        atualizar("URGENTE", ADMIN, "{\"prazoPrimeiraRespostaMin\":10,\"prazoResolucaoMin\":20}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculaLimitesAPartirDeCriadoEmEmRecalculosDePrioridade() {
        Chamado c = em.find(Chamado.class, 3L); // CRITICA

        sla.aplicarLimites(c);
        assertThat(c.getSlaPrimeiraRespostaLimite()).isEqualTo(c.getCriadoEm().plus(15, ChronoUnit.MINUTES));
        assertThat(c.getSlaResolucaoLimite()).isEqualTo(c.getCriadoEm().plus(240, ChronoUnit.MINUTES));

        c.setPrioridade(Prioridade.BAIXA);   // mudança de prioridade: recalcula a partir de criado_em, não de agora
        sla.aplicarLimites(c);
        assertThat(c.getSlaPrimeiraRespostaLimite()).isEqualTo(c.getCriadoEm().plus(8, ChronoUnit.HOURS));
        assertThat(c.getSlaResolucaoLimite()).isEqualTo(c.getCriadoEm().plus(72, ChronoUnit.HOURS));
    }

    @Test
    void recalculoUsaPoliticaAtualizada() throws Exception {
        atualizar("CRITICA", ADMIN, "{\"prazoPrimeiraRespostaMin\":10,\"prazoResolucaoMin\":120}").andExpect(status().isOk());
        Chamado c = em.find(Chamado.class, 3L);
        sla.aplicarLimites(c);
        assertThat(c.getSlaResolucaoLimite()).isEqualTo(c.getCriadoEm().plus(120, ChronoUnit.MINUTES));
    }

    @Test
    void situacaoDoSlaDeChamadosDaDemonstracao() {
        Instant agora = Instant.now();
        // chamado 3: CRITICA, sem resolução, criado há 300 min com limite de 240 -> atrasado
        assertThat(sla.situacaoResolucao(em.find(Chamado.class, 3L), agora)).isEqualTo(SituacaoSla.ATRASADO);
        // chamado 5: MEDIA aberto há 90 min -> em andamento
        assertThat(sla.situacaoResolucao(em.find(Chamado.class, 5L), agora)).isEqualTo(SituacaoSla.EM_ANDAMENTO);
        // chamado 1: ALTA resolvido em 400 min (limite 480) -> dentro do prazo
        assertThat(sla.situacaoResolucao(em.find(Chamado.class, 1L), agora)).isEqualTo(SituacaoSla.DENTRO_DO_PRAZO);
    }

    @Test
    void semPoliticaAtivaNaoCalcula() {
        em.createQuery("update PoliticaSla p set p.ativo = false where p.prioridade = :p")
                .setParameter("p", Prioridade.ALTA).executeUpdate();
        em.clear();
        assertThatThrownBy(() -> sla.calcularLimites(Prioridade.ALTA, Instant.now()))
                .isInstanceOf(RegraNegocioException.class);
    }
}
