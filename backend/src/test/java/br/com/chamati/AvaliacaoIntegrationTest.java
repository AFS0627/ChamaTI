package br.com.chamati;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.repository.ChamadoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstração: 1 FECHADO tec2 ana | 2 RESOLVIDO tec3 bruno | 3 EM_ATENDIMENTO tec2 juliana | 5 ABERTO sem técnico bruno.
 * Em cada teste, os sucessos vêm antes das requisições que falham.
 */
@Transactional
@AutoConfigureMockMvc
class AvaliacaoIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);
    static final RequestPostProcessor BRUNO = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 5L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    @Autowired MockMvc mvc;
    @Autowired ChamadoRepository chamados;

    private ResultActions avaliar(RequestPostProcessor quem, long chamadoId, String corpo) throws Exception {
        return mvc.perform(post("/api/chamados/" + chamadoId + "/avaliacao").with(quem)
                .contentType(MediaType.APPLICATION_JSON).content(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        mvc.perform(post("/api/chamados/2/avaliacao").contentType(MediaType.APPLICATION_JSON).content("{\"nota\":5}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/avaliacoes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/avaliacoes/tecnicos")).andExpect(status().isUnauthorized());
    }

    @Test
    void consultaEMediasSaoSomenteDoAdmin() throws Exception {
        mvc.perform(get("/api/avaliacoes").with(ANA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/avaliacoes").with(CARLOS)).andExpect(status().isForbidden());
        mvc.perform(get("/api/avaliacoes/tecnicos").with(CARLOS)).andExpect(status().isForbidden());
    }

    @Test
    void solicitanteAvaliaChamadoResolvidoEFechado() throws Exception {
        avaliar(BRUNO, 2, "{\"nota\":5,\"comentario\":\"  Ótimo atendimento  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chamadoId").value(2))
                .andExpect(jsonPath("$.nota").value(5))
                .andExpect(jsonPath("$.comentario").value("Ótimo atendimento"))
                .andExpect(jsonPath("$.solicitante.id").value(5))
                .andExpect(jsonPath("$.tecnico.id").value(3));
        avaliar(ANA, 1, "{\"nota\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comentario").doesNotExist())
                .andExpect(jsonPath("$.tecnico.id").value(2));
    }

    @Test
    void adminConsultaAvaliacoesEFiltraPorTecnico() throws Exception {
        avaliar(BRUNO, 2, "{\"nota\":5,\"comentario\":\"Ótimo\"}").andExpect(status().isCreated());
        avaliar(ANA, 1, "{\"nota\":3}").andExpect(status().isCreated());

        mvc.perform(get("/api/avaliacoes").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[0].chamadoId").value(1))
                .andExpect(jsonPath("$.conteudo[1].chamadoId").value(2))
                .andExpect(jsonPath("$.conteudo[1].protocolo").isNotEmpty());
        mvc.perform(get("/api/avaliacoes").param("tecnicoId", "3").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].chamadoId").value(2))
                .andExpect(jsonPath("$.conteudo[0].nota").value(5));
        mvc.perform(get("/api/avaliacoes").param("tamanho", "0").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void adminVeMediaEQuantidadePorTecnico() throws Exception {
        var resolvido = chamados.findById(3L).orElseThrow();
        resolvido.setStatus(StatusChamado.RESOLVIDO);
        resolvido.setResolvidoEm(Instant.now());
        avaliar(ANA, 1, "{\"nota\":3}").andExpect(status().isCreated());
        avaliar(JULIANA, 3, "{\"nota\":4}").andExpect(status().isCreated());
        avaliar(BRUNO, 2, "{\"nota\":5}").andExpect(status().isCreated());

        mvc.perform(get("/api/avaliacoes/tecnicos").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.tecnicoId==2)].media").value(hasItem(3.5)))
                .andExpect(jsonPath("$[?(@.tecnicoId==2)].quantidade").value(hasItem(2)))
                .andExpect(jsonPath("$[?(@.tecnicoId==3)].media").value(hasItem(5.0)))
                .andExpect(jsonPath("$[?(@.tecnicoId==3)].quantidade").value(hasItem(1)))
                .andExpect(jsonPath("$[0].tecnicoNome").isNotEmpty());
    }

    @Test
    void semAvaliacoesAListaDeMediasVemVazia() throws Exception {
        mvc.perform(get("/api/avaliacoes/tecnicos").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cadaChamadoRecebeUmaUnicaAvaliacao() throws Exception {
        avaliar(BRUNO, 2, "{\"nota\":4}").andExpect(status().isCreated());

        avaliar(BRUNO, 2, "{\"nota\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Este chamado já foi avaliado."));
    }

    @Test
    void soOSolicitanteDoChamadoAvalia() throws Exception {
        avaliar(ADMIN, 2, "{\"nota\":5}").andExpect(status().isForbidden());
        avaliar(CARLOS, 1, "{\"nota\":5}").andExpect(status().isForbidden());
        avaliar(JULIANA, 2, "{\"nota\":5}").andExpect(status().isNotFound());
        avaliar(ADMIN, 999999, "{\"nota\":5}").andExpect(status().isNotFound());
    }

    @Test
    void chamadoNaoResolvidoNaoPodeSerAvaliado() throws Exception {
        avaliar(JULIANA, 3, "{\"nota\":5}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Só é possível avaliar chamados resolvidos."));
        avaliar(BRUNO, 5, "{\"nota\":5}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void validaNotaEComentario() throws Exception {
        avaliar(BRUNO, 2, "{\"nota\":0}").andExpect(status().isBadRequest());
        avaliar(BRUNO, 2, "{\"nota\":6}").andExpect(status().isBadRequest());
        avaliar(BRUNO, 2, "{\"comentario\":\"sem nota\"}").andExpect(status().isBadRequest());
        avaliar(BRUNO, 2, "{\"nota\":\"abc\"}").andExpect(status().isBadRequest());
        avaliar(BRUNO, 2, "{\"nota\":5,\"comentario\":\"" + "x".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest());
    }
}
