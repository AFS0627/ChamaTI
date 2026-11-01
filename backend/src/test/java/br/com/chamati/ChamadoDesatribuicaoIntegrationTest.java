package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
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
 * POST /api/chamados/{id}/desatribuir sobre a demonstração: 1 FECHADO tec2 | 2 RESOLVIDO tec3 | 4 AGUARDANDO_USUARIO tec3 (Ana)
 * 5 ABERTO sem técnico | 6 EM_ATENDIMENTO tec2 (Juliana). Escritas e sucessos vêm antes das requisições que falham.
 */
@Transactional
@AutoConfigureMockMvc
class ChamadoDesatribuicaoIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    @Autowired MockMvc mvc;
    @Autowired ChamadoRepository chamados;
    @Autowired HistoricoChamadoRepository historico;

    private ResultActions desatribuir(RequestPostProcessor quem, long id, String json) throws Exception {
        return mvc.perform(post("/api/chamados/" + id + "/desatribuir").with(quem)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions desatribuirSemCorpo(RequestPostProcessor quem, long id) throws Exception {
        return mvc.perform(post("/api/chamados/" + id + "/desatribuir").with(quem));
    }

    private Chamado chamado(long id) {
        return chamados.findById(id).orElseThrow();
    }

    private List<HistoricoChamado> eventos(long id, TipoEventoHistorico tipo) {
        return historico.findByChamadoId(id, PageRequest.of(0, 100, Sort.by("criadoEm", "id"))).getContent().stream()
                .filter(e -> e.getTipoEvento() == tipo).toList();
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(post("/api/chamados/6/desatribuir")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRemoveOTecnicoSemExcluirOChamadoELimpaOsDadosDeAtribuicao() throws Exception {
        Chamado antes = chamado(6);
        var primeiraResposta = antes.getPrimeiraRespostaEm();
        var limiteResolucao = antes.getSlaResolucaoLimite();
        int totalEventosDeStatus = eventos(6, TipoEventoHistorico.ALTERACAO_STATUS).size();
        assertThat(antes.getTecnico().getId()).isEqualTo(2L);
        assertThat(antes.getAtribuidoEm()).isNotNull();

        desatribuir(ADMIN, 6, "{\"motivo\":\"Técnico em férias\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.tecnico").doesNotExist());

        Chamado depois = chamado(6);
        assertThat(depois.getTecnico()).isNull();
        assertThat(depois.getAtribuidoEm()).isNull();
        assertThat(depois.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(depois.getPrimeiraRespostaEm()).isEqualTo(primeiraResposta);
        assertThat(depois.getSlaResolucaoLimite()).isEqualTo(limiteResolucao);

        List<HistoricoChamado> remocoes = eventos(6, TipoEventoHistorico.ATRIBUICAO).stream()
                .filter(e -> e.getValorNovo() == null).toList();
        assertThat(remocoes).hasSize(1);
        HistoricoChamado remocao = remocoes.get(0);
        assertThat(remocao.getCampo()).isEqualTo("tecnico");
        assertThat(remocao.getValorAnterior()).isEqualTo("Carlos Mendes");
        assertThat(remocao.getDescricao()).isEqualTo("Técnico responsável removido: Técnico em férias");
        assertThat(remocao.getAutor().getId()).isEqualTo(1L);
        List<HistoricoChamado> status = eventos(6, TipoEventoHistorico.ALTERACAO_STATUS);
        assertThat(status).hasSize(totalEventosDeStatus + 1);
        HistoricoChamado ultimo = status.get(status.size() - 1);
        assertThat(ultimo.getValorAnterior()).isEqualTo("EM_ATENDIMENTO");
        assertThat(ultimo.getValorNovo()).isEqualTo("ABERTO");
    }

    @Test
    void solicitanteVeARemocaoNoHistoricoEOChamadoVoltaAFila() throws Exception {
        desatribuir(ADMIN, 6, "{}").andExpect(status().isOk());

        mvc.perform(get("/api/chamados/6/historico").with(JULIANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[?(@.descricao=='Técnico responsável removido')].valorAnterior")
                        .value("Carlos Mendes"));
        mvc.perform(get("/api/chamados").param("semTecnico", "true").param("tamanho", "100").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[?(@.id==6)].status").value("ABERTO"));
    }

    @Test
    void depoisDeDesatribuidoOChamadoPodeSerAssumidoPorOutroTecnicoEAtendido() throws Exception {
        desatribuir(ADMIN, 6, "{}").andExpect(status().isOk());

        mvc.perform(put("/api/chamados/6/tecnico").with(FERNANDA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tecnicoId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tecnico.id").value(3));
        mvc.perform(patch("/api/chamados/6/status").with(FERNANDA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ATENDIMENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));

        Chamado c = chamado(6);
        assertThat(c.getTecnico().getId()).isEqualTo(3L);
        assertThat(c.getAtribuidoEm()).isNotNull();
    }

    @Test
    void chamadoAguardandoUsuarioVoltaParaAFilaSemCorpoNaRequisicao() throws Exception {
        var primeiraResposta = chamado(4).getPrimeiraRespostaEm();

        desatribuirSemCorpo(ADMIN, 4).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.tecnico").doesNotExist());

        Chamado c = chamado(4);
        assertThat(c.getTecnico()).isNull();
        assertThat(c.getAtribuidoEm()).isNull();
        assertThat(c.getPrimeiraRespostaEm()).isEqualTo(primeiraResposta);
        assertThat(eventos(4, TipoEventoHistorico.ATRIBUICAO).stream().filter(e -> e.getValorNovo() == null)
                .map(HistoricoChamado::getDescricao)).containsExactly("Técnico responsável removido");
    }

    @Test
    void recusasDePermissaoVisibilidadeVersaoERegrasDeNegocio() throws Exception {
        long versao = chamado(6).getVersao();

        desatribuirSemCorpo(CARLOS, 6).andExpect(status().isForbidden());
        desatribuirSemCorpo(JULIANA, 6).andExpect(status().isForbidden());
        desatribuirSemCorpo(FERNANDA, 5).andExpect(status().isForbidden());
        desatribuirSemCorpo(FERNANDA, 6).andExpect(status().isNotFound());
        desatribuirSemCorpo(ADMIN, 999999).andExpect(status().isNotFound());
        desatribuir(ADMIN, 6, "{\"versao\":" + (versao + 5) + "}").andExpect(status().isConflict());
        desatribuirSemCorpo(ADMIN, 5).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("O chamado não tem técnico atribuído."));
        desatribuirSemCorpo(ADMIN, 1).andExpect(status().isUnprocessableEntity());
        desatribuirSemCorpo(ADMIN, 2).andExpect(status().isUnprocessableEntity());
        desatribuir(ADMIN, 6, "{\"motivo\":\"" + "x".repeat(401) + "\"}").andExpect(status().isBadRequest());

        assertThat(chamado(6).getTecnico().getId()).isEqualTo(2L);
        assertThat(chamado(6).getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        assertThat(chamado(1).getTecnico()).isNotNull();
        assertThat(chamado(2).getTecnico()).isNotNull();
    }

    @Test
    void desatribuirComVersaoAtualEAceito() throws Exception {
        long versao = chamado(6).getVersao();

        desatribuir(ADMIN, 6, "{\"versao\":" + versao + "}").andExpect(status().isOk());
    }
}
