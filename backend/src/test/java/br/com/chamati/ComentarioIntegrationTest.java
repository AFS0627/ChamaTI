package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.ComentarioRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstração: 3 EM_ATENDIMENTO tec2 juliana (já respondido) | 4 AGUARDANDO_USUARIO tec3 ana | 5 ABERTO sem técnico bruno
 * | 9 ABERTO sem técnico juliana | 1 FECHADO tec2 ana | 2 RESOLVIDO tec3 bruno. Os chamados 5 e 9 ainda não têm
 * primeira resposta. Em cada teste, os sucessos vêm antes das requisições que falham.
 */
@Transactional
@AutoConfigureMockMvc
class ComentarioIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);
    static final RequestPostProcessor BRUNO = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 5L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    @Autowired MockMvc mvc;
    @Autowired ChamadoRepository chamados;
    @Autowired ComentarioRepository comentarios;
    @Autowired HistoricoChamadoRepository historico;

    private ResultActions comentar(RequestPostProcessor quem, long chamadoId, String tipo, String conteudo) throws Exception {
        return mvc.perform(post("/api/chamados/" + chamadoId + "/comentarios").with(quem)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"" + tipo + "\",\"conteudo\":\"" + conteudo + "\"}"));
    }

    private long criarChamado(RequestPostProcessor quem) throws Exception {
        String corpo = mvc.perform(post("/api/chamados").with(quem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Chamado de apoio\",\"descricao\":\"Descrição\",\"categoriaId\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }

    private Chamado chamado(long id) {
        return chamados.findById(id).orElseThrow();
    }

    private List<HistoricoChamado> eventos(long id, TipoEventoHistorico tipo) {
        return historico.findByChamadoId(id, PageRequest.of(0, 100, Sort.by("criadoEm", "id"))).getContent().stream()
                .filter(e -> e.getTipoEvento() == tipo).toList();
    }

    private List<HistoricoChamado> marcosDePrimeiraResposta(long id) {
        return historico.findByChamadoId(id, PageRequest.of(0, 100, Sort.by("criadoEm", "id"))).getContent().stream()
                .filter(e -> "primeira_resposta_em".equals(e.getCampo())).toList();
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(post("/api/chamados/3/comentarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"PUBLICO\",\"conteudo\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/3/comentarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/3/historico")).andExpect(status().isUnauthorized());
    }

    // ---------- primeira resposta ----------

    @Test
    void primeiraRespostaSoNoPrimeiroComentarioPublicoValidoDeTecnicoOuAdmin() throws Exception {
        comentar(BRUNO, 5, "PUBLICO", "Alguma novidade?").andExpect(status().isCreated());
        assertThat(chamado(5).getPrimeiraRespostaEm()).as("solicitante não responde").isNull();

        comentar(FERNANDA, 5, "NOTA_INTERNA", "Aguardando triagem").andExpect(status().isCreated());
        assertThat(chamado(5).getPrimeiraRespostaEm()).as("nota interna não é resposta").isNull();

        String corpo = comentar(CARLOS, 5, "PUBLICO", "Olá, vamos analisar.").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Instant instanteDoComentario = Instant.parse(JsonPath.read(corpo, "$.criadoEm"));
        assertThat(chamado(5).getPrimeiraRespostaEm()).isEqualTo(instanteDoComentario);
        var marcos = marcosDePrimeiraResposta(5);
        assertThat(marcos).hasSize(1);
        assertThat(marcos.get(0).getTipoEvento()).isEqualTo(TipoEventoHistorico.COMENTARIO);
        assertThat(marcos.get(0).getDescricao()).isEqualTo("Comentário adicionado (primeira resposta)");
        assertThat(marcos.get(0).getValorNovo()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        assertThat(marcos.get(0).getAutor().getId()).isEqualTo(2L);

        comentar(FERNANDA, 5, "PUBLICO", "Também estou olhando").andExpect(status().isCreated());
        assertThat(chamado(5).getPrimeiraRespostaEm()).as("segunda resposta não altera").isEqualTo(instanteDoComentario);
        assertThat(marcosDePrimeiraResposta(5)).hasSize(1);
    }

    @Test
    void administradorTambemRegistraAPrimeiraRespostaEOSlaConsideraOMomento() throws Exception {
        comentar(ADMIN, 9, "PUBLICO", "Recebemos o chamado.").andExpect(status().isCreated());

        assertThat(chamado(9).getPrimeiraRespostaEm()).isNotNull();
        mvc.perform(get("/api/chamados/9").with(ADMIN))
                .andExpect(jsonPath("$.primeiraRespostaEm").isNotEmpty())
                .andExpect(jsonPath("$.situacaoSlaPrimeiraResposta").value("DENTRO_DO_PRAZO"));
    }

    @Test
    void quemAbriuOChamadoNaoRegistraAPrimeiraRespostaMesmoSendoTecnicoOuAdmin() throws Exception {
        long doCarlos = criarChamado(CARLOS);
        long doAdmin = criarChamado(ADMIN);

        comentar(CARLOS, doCarlos, "PUBLICO", "Detalhando o problema").andExpect(status().isCreated());
        comentar(ADMIN, doAdmin, "PUBLICO", "Mais detalhes").andExpect(status().isCreated());
        assertThat(chamado(doCarlos).getPrimeiraRespostaEm()).isNull();
        assertThat(chamado(doAdmin).getPrimeiraRespostaEm()).isNull();

        comentar(ADMIN, doCarlos, "PUBLICO", "Vamos atender").andExpect(status().isCreated());
        comentar(CARLOS, doAdmin, "PUBLICO", "Vamos atender").andExpect(status().isCreated());
        assertThat(chamado(doCarlos).getPrimeiraRespostaEm()).isNotNull();
        assertThat(chamado(doAdmin).getPrimeiraRespostaEm()).isNotNull();
    }

    @Test
    void primeiraRespostaJaExistenteNaoEhAlterada() throws Exception {
        Instant antes = chamado(3).getPrimeiraRespostaEm();
        assertThat(antes).isNotNull();

        comentar(CARLOS, 3, "PUBLICO", "Nova atualização").andExpect(status().isCreated());

        assertThat(chamado(3).getPrimeiraRespostaEm()).isEqualTo(antes);
        assertThat(marcosDePrimeiraResposta(3)).isEmpty();
    }

    // ---------- autor, data, tipo e histórico ----------

    @Test
    void registraAutorDataTipoEMudancaNoComentarioENoHistorico() throws Exception {
        comentar(CARLOS, 3, "PUBLICO", "  Atualização com espaços  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("PUBLICO"))
                .andExpect(jsonPath("$.conteudo").value("Atualização com espaços"))
                .andExpect(jsonPath("$.autor.id").value(2))
                .andExpect(jsonPath("$.autor.nome").value("Carlos Mendes"))
                .andExpect(jsonPath("$.autor.perfil").value("TECNICO"))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.autor.email").doesNotExist());
        comentar(CARLOS, 3, "NOTA_INTERNA", "Verificar certificado").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("NOTA_INTERNA"));

        assertThat(comentarios.countByChamadoIdAndTipo(3L, TipoComentario.PUBLICO)).isEqualTo(3);        // 2 da demonstração + 1
        assertThat(comentarios.countByChamadoIdAndTipo(3L, TipoComentario.NOTA_INTERNA)).isEqualTo(2);   // 1 da demonstração + 1

        var publicos = eventos(3, TipoEventoHistorico.COMENTARIO);
        assertThat(publicos).hasSize(3);
        HistoricoChamado ultimoPublico = publicos.get(2);
        assertThat(ultimoPublico.getDescricao()).isEqualTo("Comentário adicionado");
        assertThat(ultimoPublico.getCampo()).isNull();
        assertThat(ultimoPublico.getAutor().getId()).isEqualTo(2L);
        assertThat(ultimoPublico.getCriadoEm()).isNotNull();

        var internas = eventos(3, TipoEventoHistorico.NOTA_INTERNA);
        assertThat(internas).hasSize(2);
        assertThat(internas.get(1).getDescricao()).isEqualTo("Nota interna adicionada");
        assertThat(internas.get(1).getAutor().getId()).isEqualTo(2L);
    }

    @Test
    void solicitanteComentaPublicamenteNoProprioChamadoERespostaEmChamadoResolvido() throws Exception {
        comentar(ANA, 4, "PUBLICO", "Segue o horário do travamento: 10h15.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor.perfil").value("SOLICITANTE"));
        comentar(BRUNO, 2, "PUBLICO", "Ainda estou sem o Excel.").andExpect(status().isCreated());   // RESOLVIDO aceita

        assertThat(eventos(4, TipoEventoHistorico.COMENTARIO)).hasSize(2);   // 1 da demonstração + 1
    }

    // ---------- recusas ----------

    @Test
    void recusaOQueNaoPodeSerFeito() throws Exception {
        comentar(ANA, 4, "NOTA_INTERNA", "x").andExpect(status().isForbidden());           // solicitante não cria nota
        comentar(ANA, 1, "NOTA_INTERNA", "x").andExpect(status().isForbidden());           // permissão antes do estado
        comentar(ADMIN, 1, "PUBLICO", "x").andExpect(status().isUnprocessableEntity());    // FECHADO
        comentar(ANA, 1, "PUBLICO", "x").andExpect(status().isUnprocessableEntity());
        comentar(JULIANA, 1, "PUBLICO", "x").andExpect(status().isNotFound());             // fora da visibilidade
        comentar(FERNANDA, 3, "PUBLICO", "x").andExpect(status().isNotFound());
        comentar(ADMIN, 9999, "PUBLICO", "x").andExpect(status().isNotFound());

        comentar(ADMIN, 3, "PUBLICO", " ").andExpect(status().isBadRequest());
        comentar(ADMIN, 3, "XYZ", "x").andExpect(status().isBadRequest());
        comentar(ADMIN, 3, "PUBLICO", "x".repeat(10001)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chamados/3/comentarios").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":\"sem tipo\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chamados/3/comentarios").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        mvc.perform(post("/api/chamados/3/comentarios").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("não é json")).andExpect(status().isBadRequest());
    }
}
