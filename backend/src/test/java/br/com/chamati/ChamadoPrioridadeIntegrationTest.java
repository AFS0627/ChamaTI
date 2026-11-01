package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.PoliticaSlaRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * PATCH /api/chamados/{id}/prioridade sobre a demonstração: 1 FECHADO ALTA tec2 | 2 RESOLVIDO BAIXA tec3
 * 3 EM_ATENDIMENTO CRITICA tec2 | 5 ABERTO MEDIA sem técnico | 6 EM_ATENDIMENTO BAIXA tec2 (solicitante: Juliana).
 * Escritas e sucessos vêm antes das requisições que falham (a falha deixa a transação do teste para rollback).
 */
@Transactional
@AutoConfigureMockMvc
class ChamadoPrioridadeIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    @Autowired MockMvc mvc;
    @Autowired ChamadoRepository chamados;
    @Autowired HistoricoChamadoRepository historico;
    @Autowired PoliticaSlaRepository politicas;

    private ResultActions alterar(RequestPostProcessor quem, long id, String json) throws Exception {
        return mvc.perform(patch("/api/chamados/" + id + "/prioridade").with(quem)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions alterar(RequestPostProcessor quem, long id, String prioridade, String motivo) throws Exception {
        String corpo = motivo == null ? "{\"prioridade\":\"" + prioridade + "\"}"
                : "{\"prioridade\":\"" + prioridade + "\",\"motivo\":\"" + motivo + "\"}";
        return alterar(quem, id, corpo);
    }

    private Chamado chamado(long id) {
        return chamados.findById(id).orElseThrow();
    }

    private List<HistoricoChamado> eventosDePrioridade(long id) {
        return historico.findByChamadoId(id, PageRequest.of(0, 100, Sort.by("criadoEm", "id"))).getContent().stream()
                .filter(e -> e.getTipoEvento() == TipoEventoHistorico.ALTERACAO_PRIORIDADE).toList();
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(patch("/api/chamados/6/prioridade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prioridade\":\"ALTA\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tecnicoResponsavelAlteraAPrioridadeERecalculaOSlaAPartirDaAbertura() throws Exception {
        PoliticaSla alta = politicas.findByPrioridadeAndAtivoTrue(Prioridade.ALTA).orElseThrow();
        Chamado antes = chamado(6);
        var criadoEm = antes.getCriadoEm();
        long versaoAntes = antes.getVersao();
        assertThat(antes.getPrioridade()).isEqualTo(Prioridade.BAIXA);

        alterar(CARLOS, 6, "ALTA", "Impacto em toda a equipe").andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));

        Chamado depois = chamado(6);
        assertThat(depois.getPrioridade()).isEqualTo(Prioridade.ALTA);
        assertThat(depois.getCriadoEm()).isEqualTo(criadoEm);
        assertThat(depois.getSlaPrimeiraRespostaLimite())
                .isEqualTo(criadoEm.plus(Duration.ofMinutes(alta.getPrazoPrimeiraRespostaMin())));
        assertThat(depois.getSlaResolucaoLimite())
                .isEqualTo(criadoEm.plus(Duration.ofMinutes(alta.getPrazoResolucaoMin())));
        assertThat(depois.getVersao()).isGreaterThanOrEqualTo(versaoAntes);

        List<HistoricoChamado> eventos = eventosDePrioridade(6);
        assertThat(eventos).hasSize(1);
        HistoricoChamado e = eventos.get(0);
        assertThat(e.getCampo()).isEqualTo("prioridade");
        assertThat(e.getValorAnterior()).isEqualTo("BAIXA");
        assertThat(e.getValorNovo()).isEqualTo("ALTA");
        assertThat(e.getDescricao()).isEqualTo("Impacto em toda a equipe");
        assertThat(e.getAutor().getId()).isEqualTo(2L);
    }

    @Test
    void solicitanteVeAMudancaDePrioridadeNoHistoricoDoSeuChamado() throws Exception {
        alterar(ADMIN, 6, "MEDIA", "Reavaliado").andExpect(status().isOk());

        mvc.perform(get("/api/chamados/6/historico").with(JULIANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[?(@.tipoEvento=='ALTERACAO_PRIORIDADE')].valorNovo").value("MEDIA"))
                .andExpect(jsonPath("$.conteudo[?(@.tipoEvento=='ALTERACAO_PRIORIDADE')].descricao").value("Reavaliado"));
    }

    @Test
    void adminAlteraAPrioridadeDeChamadoDaFilaSemTecnico() throws Exception {
        PoliticaSla critica = politicas.findByPrioridadeAndAtivoTrue(Prioridade.CRITICA).orElseThrow();

        alterar(ADMIN, 5, "CRITICA", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridade").value("CRITICA"))
                .andExpect(jsonPath("$.status").value("ABERTO"));

        Chamado c = chamado(5);
        assertThat(c.getSlaResolucaoLimite())
                .isEqualTo(c.getCriadoEm().plus(Duration.ofMinutes(critica.getPrazoResolucaoMin())));
        assertThat(eventosDePrioridade(5)).hasSize(1);
        assertThat(eventosDePrioridade(5).get(0).getDescricao()).isNull();
    }

    @Test
    void alterarComVersaoAtualEAceito() throws Exception {
        long versao = chamado(6).getVersao();

        alterar(CARLOS, 6, "{\"prioridade\":\"MEDIA\",\"versao\":" + versao + "}").andExpect(status().isOk());
    }

    @Test
    void recusasDePermissaoVisibilidadeVersaoERegrasDeNegocio() throws Exception {
        long versao = chamado(6).getVersao();

        alterar(JULIANA, 6, "ALTA", null).andExpect(status().isForbidden());
        alterar(FERNANDA, 5, "ALTA", null).andExpect(status().isForbidden());
        alterar(FERNANDA, 6, "ALTA", null).andExpect(status().isNotFound());
        alterar(ADMIN, 999999, "ALTA", null).andExpect(status().isNotFound());
        alterar(CARLOS, 6, "{\"prioridade\":\"ALTA\",\"versao\":" + (versao + 5) + "}").andExpect(status().isConflict());
        alterar(CARLOS, 6, "BAIXA", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("O chamado já tem a prioridade BAIXA."));
        alterar(ADMIN, 1, "BAIXA", null).andExpect(status().isUnprocessableEntity());
        alterar(ADMIN, 2, "ALTA", null).andExpect(status().isUnprocessableEntity());

        assertThat(chamado(6).getPrioridade()).isEqualTo(Prioridade.BAIXA);
        assertThat(eventosDePrioridade(6)).isEmpty();
    }

    @Test
    void corpoInvalidoResponde400() throws Exception {
        alterar(CARLOS, 6, "{}").andExpect(status().isBadRequest());
        alterar(CARLOS, 6, "{\"prioridade\":\"URGENTE\"}").andExpect(status().isBadRequest());
        alterar(CARLOS, 6, "ALTA", "x".repeat(401)).andExpect(status().isBadRequest());

        assertThat(chamado(6).getPrioridade()).isEqualTo(Prioridade.BAIXA);
    }

    @Test
    void semPoliticaAtivaParaANovaPrioridadeNadaEhAlterado() throws Exception {
        PoliticaSla alta = politicas.findByPrioridade(Prioridade.ALTA).orElseThrow();
        alta.setAtivo(false);
        politicas.saveAndFlush(alta);
        Chamado antes = chamado(6);
        var limiteResolucao = antes.getSlaResolucaoLimite();

        alterar(CARLOS, 6, "ALTA", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("ALTA")));

        Chamado depois = chamado(6);
        assertThat(depois.getPrioridade()).isEqualTo(Prioridade.BAIXA);
        assertThat(depois.getSlaResolucaoLimite()).isEqualTo(limiteResolucao);
        assertThat(eventosDePrioridade(6)).isEmpty();
    }
}
