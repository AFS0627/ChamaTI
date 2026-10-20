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
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstração: 1 FECHADO tec2 ana | 2 RESOLVIDO tec3 bruno | 3 EM_ATENDIMENTO tec2 juliana | 4 AGUARDANDO_USUARIO tec3 ana
 * 5 ABERTO sem técnico bruno | 6 EM_ATENDIMENTO tec2 juliana | 7 RESOLVIDO tec3 ana | 8 AGUARDANDO_TERCEIRO tec2 bruno
 * 9 ABERTO sem técnico juliana | 10 FECHADO tec3 ana | 11 EM_ATENDIMENTO tec3 juliana | 12 RESOLVIDO tec2 ana.
 * Em cada teste, escritas e sucessos vêm antes das requisições que falham (a falha deixa a transação do teste
 * marcada para rollback).
 */
@Transactional
@AutoConfigureMockMvc
class ChamadoGestaoIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);
    static final RequestPostProcessor BRUNO = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 5L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    @Autowired MockMvc mvc;
    @Autowired ChamadoRepository chamados;
    @Autowired HistoricoChamadoRepository historico;
    @Autowired UsuarioRepository usuarios;

    // ---------- auxiliares ----------

    private ResultActions enviar(MockHttpServletRequestBuilder req, RequestPostProcessor quem, String json) throws Exception {
        return mvc.perform(req.with(quem).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions atribuir(RequestPostProcessor quem, long id, long tecnicoId) throws Exception {
        return enviar(put("/api/chamados/" + id + "/tecnico"), quem, "{\"tecnicoId\":" + tecnicoId + "}");
    }

    private ResultActions mudarStatus(RequestPostProcessor quem, long id, String status) throws Exception {
        return enviar(patch("/api/chamados/" + id + "/status"), quem, "{\"status\":\"" + status + "\"}");
    }

    private ResultActions resolver(RequestPostProcessor quem, long id, String solucao) throws Exception {
        return enviar(post("/api/chamados/" + id + "/resolver"), quem, "{\"solucao\":\"" + solucao + "\"}");
    }

    private ResultActions fechar(RequestPostProcessor quem, long id) throws Exception {
        return mvc.perform(post("/api/chamados/" + id + "/fechar").with(quem));
    }

    private ResultActions reabrir(RequestPostProcessor quem, long id, String motivo) throws Exception {
        return enviar(post("/api/chamados/" + id + "/reabrir"), quem, "{\"motivo\":\"" + motivo + "\"}");
    }

    private long criarChamado(RequestPostProcessor quem) throws Exception {
        String corpo = enviar(post("/api/chamados"), quem,
                "{\"titulo\":\"Chamado de apoio\",\"descricao\":\"Descrição\",\"categoriaId\":1}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }

    private Usuario novoTecnico(String email, boolean ativo) {
        Usuario u = new Usuario();
        u.setNome("Técnico " + email);
        u.setEmail(email);
        u.setSenhaHash("$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW");
        u.setPerfil(Perfil.TECNICO);
        u.setAtivo(ativo);
        return usuarios.saveAndFlush(u);
    }

    private List<HistoricoChamado> todosOsEventos(long id) {
        return historico.findByChamadoId(id, PageRequest.of(0, 100, Sort.by("criadoEm", "id"))).getContent();
    }

    private List<HistoricoChamado> eventos(long id, TipoEventoHistorico tipo) {
        return todosOsEventos(id).stream().filter(e -> e.getTipoEvento() == tipo).toList();
    }

    private Chamado chamado(long id) {
        return chamados.findById(id).orElseThrow();
    }

    // ---------- autenticação ----------

    @Test
    void exigeAutenticacao() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        mvc.perform(put("/api/chamados/5/tecnico").contentType(json).content("{\"tecnicoId\":2}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/chamados/3/status").contentType(json).content("{\"status\":\"AGUARDANDO_USUARIO\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chamados/3/resolver").contentType(json).content("{\"solucao\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chamados/7/fechar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chamados/1/reabrir").contentType(json).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- atribuição ----------

    @Test
    void adminAtribuiETrocaTecnicoRegistrandoHistoricoEData() throws Exception {
        Usuario inativo = novoTecnico("inativo@chamati.local", false);

        atribuir(ADMIN, 5, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.tecnico.id").value(2))
                .andExpect(jsonPath("$.tecnico.nome").value("Carlos Mendes"))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.atribuidoEm").isNotEmpty());
        var primeiro = eventos(5, TipoEventoHistorico.ATRIBUICAO);
        assertThat(primeiro).hasSize(1);
        assertThat(primeiro.get(0).getCampo()).isEqualTo("tecnico");
        assertThat(primeiro.get(0).getValorAnterior()).isNull();
        assertThat(primeiro.get(0).getValorNovo()).isEqualTo("Carlos Mendes");
        assertThat(primeiro.get(0).getDescricao()).isEqualTo("Chamado atribuído ao técnico");
        assertThat(primeiro.get(0).getAutor().getId()).isEqualTo(1L);
        Instant primeiraAtribuicao = chamado(5).getAtribuidoEm();

        atribuir(ADMIN, 5, 3).andExpect(status().isOk()).andExpect(jsonPath("$.tecnico.id").value(3));
        var todos = eventos(5, TipoEventoHistorico.ATRIBUICAO);
        assertThat(todos).hasSize(2);
        assertThat(todos.get(1).getValorAnterior()).isEqualTo("Carlos Mendes");
        assertThat(todos.get(1).getValorNovo()).isEqualTo("Fernanda Lima");
        assertThat(todos.get(1).getDescricao()).isEqualTo("Técnico responsável alterado");
        assertThat(chamado(5).getAtribuidoEm()).isAfterOrEqualTo(primeiraAtribuicao);

        atribuir(ADMIN, 5, 3).andExpect(status().isUnprocessableEntity());              // mesmo técnico
        atribuir(ADMIN, 5, 1).andExpect(status().isUnprocessableEntity());              // perfil ADMIN
        atribuir(ADMIN, 5, 4).andExpect(status().isUnprocessableEntity());              // perfil SOLICITANTE
        atribuir(ADMIN, 5, 9999).andExpect(status().isUnprocessableEntity());           // inexistente
        atribuir(ADMIN, 5, inativo.getId()).andExpect(status().isUnprocessableEntity()); // técnico inativo
        atribuir(ADMIN, 1, 2).andExpect(status().isUnprocessableEntity());              // FECHADO
        atribuir(ADMIN, 2, 2).andExpect(status().isUnprocessableEntity());              // RESOLVIDO
        enviar(put("/api/chamados/5/tecnico"), ADMIN, "{}").andExpect(status().isBadRequest());
        atribuir(ADMIN, 9999, 2).andExpect(status().isNotFound());
    }

    @Test
    void tecnicoAssumeChamadoDaFilaMasNaoTrocaNemAtribuiAOutro() throws Exception {
        atribuir(CARLOS, 9, 2).andExpect(status().isOk()).andExpect(jsonPath("$.tecnico.id").value(2));
        atribuir(FERNANDA, 5, 3).andExpect(status().isOk()).andExpect(jsonPath("$.tecnico.id").value(3));
        assertThat(eventos(9, TipoEventoHistorico.ATRIBUICAO).get(0).getAutor().getId()).isEqualTo(2L);

        atribuir(CARLOS, 8, 3).andExpect(status().isForbidden());   // a outro técnico
        atribuir(CARLOS, 8, 2).andExpect(status().isForbidden());   // já tem técnico: só ADMIN troca
        atribuir(CARLOS, 5, 2).andExpect(status().isNotFound());    // agora é do Fernanda: fora da visibilidade
        atribuir(ANA, 1, 2).andExpect(status().isForbidden());      // solicitante vê o próprio chamado, mas não atribui
        atribuir(JULIANA, 1, 2).andExpect(status().isNotFound());
        atribuir(FERNANDA, 3, 3).andExpect(status().isNotFound());
    }

    // ---------- alteração de status ----------

    @Test
    void percorreOsStatusPermitidosRegistrandoCadaTransicao() throws Exception {
        long meu = criarChamado(CARLOS);
        atribuir(ADMIN, meu, 3).andExpect(status().isOk());      // solicitante Carlos, responsável Fernanda
        atribuir(ADMIN, 5, 2).andExpect(status().isOk());

        enviar(patch("/api/chamados/5/status"), CARLOS, "{\"status\":\"EM_ATENDIMENTO\",\"observacao\":\"Iniciado\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));
        var inicio = eventos(5, TipoEventoHistorico.ALTERACAO_STATUS);
        assertThat(inicio).hasSize(1);
        assertThat(inicio.get(0).getCampo()).isEqualTo("status");
        assertThat(inicio.get(0).getValorAnterior()).isEqualTo("ABERTO");
        assertThat(inicio.get(0).getValorNovo()).isEqualTo("EM_ATENDIMENTO");
        assertThat(inicio.get(0).getDescricao()).isEqualTo("Iniciado");
        assertThat(inicio.get(0).getAutor().getId()).isEqualTo(2L);

        mudarStatus(CARLOS, 5, "AGUARDANDO_USUARIO").andExpect(status().isOk());
        mudarStatus(CARLOS, 5, "AGUARDANDO_TERCEIRO").andExpect(status().isOk());
        mudarStatus(CARLOS, 5, "AGUARDANDO_USUARIO").andExpect(status().isOk());
        mudarStatus(CARLOS, 5, "EM_ATENDIMENTO").andExpect(status().isOk());
        mudarStatus(ADMIN, 5, "AGUARDANDO_TERCEIRO").andExpect(status().isOk());
        assertThat(eventos(5, TipoEventoHistorico.ALTERACAO_STATUS)).hasSize(6);
        assertThat(chamado(5).getResolvidoEm()).isNull();
        assertThat(chamado(5).getFechadoEm()).isNull();

        mudarStatus(ADMIN, 5, "AGUARDANDO_TERCEIRO").andExpect(status().isUnprocessableEntity()); // mesmo status
        mudarStatus(ADMIN, 5, "ABERTO").andExpect(status().isUnprocessableEntity());
        mudarStatus(ADMIN, 5, "RESOLVIDO").andExpect(status().isUnprocessableEntity());
        mudarStatus(ADMIN, 5, "FECHADO").andExpect(status().isUnprocessableEntity());
        mudarStatus(ADMIN, 9, "EM_ATENDIMENTO").andExpect(status().isUnprocessableEntity());      // sem técnico
        mudarStatus(ADMIN, 2, "EM_ATENDIMENTO").andExpect(status().isUnprocessableEntity());      // RESOLVIDO: use reabrir
        mudarStatus(ADMIN, 1, "EM_ATENDIMENTO").andExpect(status().isUnprocessableEntity());      // FECHADO: use reabrir
        mudarStatus(CARLOS, meu, "EM_ATENDIMENTO").andExpect(status().isForbidden());             // solicitante, não responsável
        mudarStatus(FERNANDA, 9, "EM_ATENDIMENTO").andExpect(status().isForbidden());             // fila: visível, sem responsável
        mudarStatus(ANA, 4, "EM_ATENDIMENTO").andExpect(status().isForbidden());
        mudarStatus(JULIANA, 4, "EM_ATENDIMENTO").andExpect(status().isNotFound());
        enviar(patch("/api/chamados/5/status"), ADMIN, "{\"status\":\"XYZ\"}").andExpect(status().isBadRequest());
        enviar(patch("/api/chamados/5/status"), ADMIN, "{}").andExpect(status().isBadRequest());
        enviar(patch("/api/chamados/5/status"), ADMIN,
                "{\"status\":\"EM_ATENDIMENTO\",\"observacao\":\"" + "x".repeat(401) + "\"}")
                .andExpect(status().isBadRequest());
        mudarStatus(ADMIN, 9999, "EM_ATENDIMENTO").andExpect(status().isNotFound());
    }

    // ---------- resolução ----------

    @Test
    void resolveRegistrandoSolucaoDataEHistorico() throws Exception {
        atribuir(ADMIN, 9, 2).andExpect(status().isOk());   // ABERTO, mas já com técnico

        resolver(CARLOS, 3, "Certificado renovado").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.solucao").value("Certificado renovado"))
                .andExpect(jsonPath("$.resolvidoEm").isNotEmpty())
                .andExpect(jsonPath("$.fechadoEm").doesNotExist())
                .andExpect(jsonPath("$.situacaoSlaResolucao").value("ATRASADO"));   // limite de 4 h já vencido
        var evento = eventos(3, TipoEventoHistorico.RESOLUCAO);
        assertThat(evento).hasSize(1);
        assertThat(evento.get(0).getValorAnterior()).isEqualTo("EM_ATENDIMENTO");
        assertThat(evento.get(0).getValorNovo()).isEqualTo("RESOLVIDO");
        assertThat(evento.get(0).getDescricao()).isEqualTo("Solução registrada");
        assertThat(evento.get(0).getAutor().getId()).isEqualTo(2L);
        assertThat(chamado(3).getResolvidoEm()).isNotNull();

        resolver(ADMIN, 6, "Rolo de tração trocado").andExpect(status().isOk())
                .andExpect(jsonPath("$.situacaoSlaResolucao").value("DENTRO_DO_PRAZO"));
        resolver(FERNANDA, 4, "Versão do ERP atualizada").andExpect(status().isOk());   // de AGUARDANDO_USUARIO
        resolver(CARLOS, 8, "Monitor substituído").andExpect(status().isOk());          // de AGUARDANDO_TERCEIRO

        resolver(ADMIN, 9, "x").andExpect(status().isUnprocessableEntity());   // ABERTO: iniciar atendimento antes
        resolver(ADMIN, 2, "x").andExpect(status().isUnprocessableEntity());   // já RESOLVIDO
        resolver(ADMIN, 1, "x").andExpect(status().isUnprocessableEntity());   // FECHADO
        resolver(ADMIN, 5, "x").andExpect(status().isUnprocessableEntity());   // ABERTO sem técnico
        enviar(post("/api/chamados/11/resolver"), ADMIN, "{\"solucao\":\"  \"}").andExpect(status().isBadRequest());
        enviar(post("/api/chamados/11/resolver"), ADMIN, "{}").andExpect(status().isBadRequest());
        resolver(ANA, 12, "x").andExpect(status().isForbidden());              // solicitante não resolve
        resolver(CARLOS, 5, "x").andExpect(status().isForbidden());            // fila: sem responsável
        resolver(CARLOS, 11, "x").andExpect(status().isNotFound());            // chamado de outro técnico
        resolver(JULIANA, 4, "x").andExpect(status().isNotFound());
    }

    // ---------- fechamento ----------

    @Test
    void fechaSomenteResolvidosPreservandoAResolucao() throws Exception {
        Instant resolvidoAntes = chamado(7).getResolvidoEm();

        fechar(ANA, 7).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.fechadoEm").isNotEmpty())
                .andExpect(jsonPath("$.resolvidoEm").isNotEmpty());
        assertThat(chamado(7).getResolvidoEm()).isEqualTo(resolvidoAntes);
        assertThat(chamado(7).getFechadoEm()).isNotNull();
        var evento = eventos(7, TipoEventoHistorico.ENCERRAMENTO);
        assertThat(evento).hasSize(1);
        assertThat(evento.get(0).getValorAnterior()).isEqualTo("RESOLVIDO");
        assertThat(evento.get(0).getValorNovo()).isEqualTo("FECHADO");
        assertThat(evento.get(0).getDescricao()).isEqualTo("Chamado encerrado");
        assertThat(evento.get(0).getAutor().getId()).isEqualTo(4L);

        fechar(BRUNO, 2).andExpect(status().isOk());
        fechar(ADMIN, 12).andExpect(status().isOk());

        fechar(CARLOS, 12).andExpect(status().isForbidden());            // técnico não fecha
        fechar(FERNANDA, 12).andExpect(status().isNotFound());
        fechar(JULIANA, 7).andExpect(status().isNotFound());
        fechar(ANA, 4).andExpect(status().isUnprocessableEntity());      // não está RESOLVIDO
        fechar(ADMIN, 3).andExpect(status().isUnprocessableEntity());
        fechar(ADMIN, 1).andExpect(status().isUnprocessableEntity());    // já FECHADO
        fechar(ADMIN, 9999).andExpect(status().isNotFound());
    }

    // ---------- reabertura ----------

    @Test
    void reabreRestaurandoDatasEPreservandoOHistoricoELimitesDeSla() throws Exception {
        Chamado semTecnico = chamado(2);
        semTecnico.setTecnico(null);
        chamados.saveAndFlush(semTecnico);
        Instant limiteAntes = chamado(1).getSlaResolucaoLimite();

        reabrir(ANA, 1, "Voltou a falhar.").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.resolvidoEm").doesNotExist())
                .andExpect(jsonPath("$.fechadoEm").doesNotExist())
                .andExpect(jsonPath("$.reabertoEm").isNotEmpty())
                .andExpect(jsonPath("$.totalReaberturas").value(1));
        assertThat(chamado(1).getSlaResolucaoLimite()).isEqualTo(limiteAntes);
        var evento = eventos(1, TipoEventoHistorico.REABERTURA);
        assertThat(evento).hasSize(1);
        assertThat(evento.get(0).getValorAnterior()).isEqualTo("FECHADO");
        assertThat(evento.get(0).getValorNovo()).isEqualTo("EM_ATENDIMENTO");
        assertThat(evento.get(0).getAutor().getId()).isEqualTo(4L);
        assertThat(evento.get(0).getDescricao()).matches("Voltou a falhar\\. resolvido_em anterior: "
                + "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}; fechado_em anterior: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

        reabrir(ADMIN, 7, "Cliente reportou falha").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));
        assertThat(eventos(7, TipoEventoHistorico.REABERTURA).get(0).getDescricao())
                .endsWith("; fechado_em anterior: nenhum");
        reabrir(CARLOS, 12, "Resolvido por engano").andExpect(status().isOk());   // técnico responsável
        reabrir(BRUNO, 2, "Ainda sem acesso").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTO"))                  // sem técnico volta para a fila
                .andExpect(jsonPath("$.totalReaberturas").value(1));

        reabrir(ADMIN, 3, "x").andExpect(status().isUnprocessableEntity());       // EM_ATENDIMENTO
        reabrir(ADMIN, 1, "x").andExpect(status().isUnprocessableEntity());       // já reaberto
        reabrir(CARLOS, 5, "x").andExpect(status().isForbidden());                // fila: sem vínculo com o chamado
        reabrir(BRUNO, 1, "x").andExpect(status().isNotFound());
        enviar(post("/api/chamados/10/reabrir"), ADMIN, "{\"motivo\":\" \"}").andExpect(status().isBadRequest());
        enviar(post("/api/chamados/10/reabrir"), ADMIN, "{\"motivo\":\"" + "m".repeat(301) + "\"}")
                .andExpect(status().isBadRequest());
        reabrir(ADMIN, 9999, "x").andExpect(status().isNotFound());
    }

    // ---------- ciclo completo ----------

    @Test
    void cicloCompletoMantemHistoricoDatasEContadores() throws Exception {
        long id = criarChamado(ANA);
        Instant limiteResolucao = chamado(id).getSlaResolucaoLimite();
        Instant limitePrimeiraResposta = chamado(id).getSlaPrimeiraRespostaLimite();

        atribuir(ADMIN, id, 2).andExpect(status().isOk());
        mudarStatus(CARLOS, id, "EM_ATENDIMENTO").andExpect(status().isOk());
        resolver(CARLOS, id, "Primeira solução").andExpect(status().isOk());
        Instant primeiraResolucao = chamado(id).getResolvidoEm();
        fechar(ANA, id).andExpect(status().isOk());
        reabrir(ANA, id, "Voltou").andExpect(status().isOk());
        assertThat(chamado(id).getResolvidoEm()).isNull();
        assertThat(chamado(id).getFechadoEm()).isNull();
        resolver(CARLOS, id, "Segunda solução").andExpect(status().isOk())
                .andExpect(jsonPath("$.solucao").value("Segunda solução"));
        assertThat(chamado(id).getResolvidoEm()).isAfterOrEqualTo(primeiraResolucao);
        fechar(ANA, id).andExpect(status().isOk());
        reabrir(ANA, id, "Voltou de novo").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.totalReaberturas").value(2))
                .andExpect(jsonPath("$.reabertoEm").isNotEmpty())
                .andExpect(jsonPath("$.resolvidoEm").doesNotExist())
                .andExpect(jsonPath("$.fechadoEm").doesNotExist());

        assertThat(todosOsEventos(id)).extracting(HistoricoChamado::getTipoEvento).containsExactly(
                TipoEventoHistorico.CRIACAO, TipoEventoHistorico.ATRIBUICAO, TipoEventoHistorico.ALTERACAO_STATUS,
                TipoEventoHistorico.RESOLUCAO, TipoEventoHistorico.ENCERRAMENTO, TipoEventoHistorico.REABERTURA,
                TipoEventoHistorico.RESOLUCAO, TipoEventoHistorico.ENCERRAMENTO, TipoEventoHistorico.REABERTURA);
        assertThat(chamado(id).getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        assertThat(chamado(id).getSlaResolucaoLimite()).isEqualTo(limiteResolucao);
        assertThat(chamado(id).getSlaPrimeiraRespostaLimite()).isEqualTo(limitePrimeiraResposta);
    }

    // ---------- controle de versão ----------

    @Test
    void versaoOpcionalImpedeAlteracaoSobreDadosDesatualizados() throws Exception {
        mvc.perform(get("/api/chamados/3").with(ADMIN)).andExpect(jsonPath("$.versao").value(0));

        enviar(patch("/api/chamados/3/status"), ADMIN, "{\"status\":\"AGUARDANDO_USUARIO\",\"versao\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1));
        enviar(patch("/api/chamados/3/status"), ADMIN, "{\"status\":\"EM_ATENDIMENTO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(2));

        enviar(patch("/api/chamados/3/status"), ADMIN, "{\"status\":\"AGUARDANDO_TERCEIRO\",\"versao\":0}")
                .andExpect(status().isConflict());
        enviar(post("/api/chamados/3/resolver"), ADMIN, "{\"solucao\":\"x\",\"versao\":1}").andExpect(status().isConflict());
        enviar(put("/api/chamados/5/tecnico"), ADMIN, "{\"tecnicoId\":2,\"versao\":99}").andExpect(status().isConflict());
        enviar(post("/api/chamados/12/fechar"), ADMIN, "{\"versao\":99}").andExpect(status().isConflict());
        enviar(post("/api/chamados/12/reabrir"), ADMIN, "{\"motivo\":\"x\",\"versao\":99}").andExpect(status().isConflict());
    }
}
