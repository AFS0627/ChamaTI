package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.entity.Chamado;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.repository.CategoriaRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.SubcategoriaRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Demonstração: categoria 6 (Impressoras) tem as subcategorias 15 e 16; a subcategoria 8 (VPN) é da categoria 3. */
@Transactional
@AutoConfigureMockMvc
class ChamadoAberturaIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor TECNICO = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);

    @Autowired MockMvc mvc;
    @Autowired ChamadoRepository chamados;
    @Autowired CategoriaRepository categorias;
    @Autowired SubcategoriaRepository subcategorias;
    @Autowired HistoricoChamadoRepository historico;
    @PersistenceContext EntityManager em;

    private ResultActions abrir(RequestPostProcessor quem, String json) throws Exception {
        return mvc.perform(post("/api/chamados").with(quem).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String corpo(String extras) {
        return "{\"titulo\":\"Impressora travada\",\"descricao\":\"Papel enroscado\",\"categoriaId\":6" + extras + "}";
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(post("/api/chamados").contentType(MediaType.APPLICATION_JSON).content(corpo("")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void abreChamadoComPadroesECalculaSlaEHistorico() throws Exception {
        String resposta = abrir(ANA, "{\"titulo\":\"  Impressora travada \",\"descricao\":\" Papel enroscado \","
                + "\"categoriaId\":6,\"subcategoriaId\":15}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocolo").value(matchesPattern("CT-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.titulo").value("Impressora travada"))
                .andExpect(jsonPath("$.descricao").value("Papel enroscado"))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.prioridade").value("MEDIA"))
                .andExpect(jsonPath("$.solicitante.id").value(4))
                .andExpect(jsonPath("$.solicitante.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.tecnico").doesNotExist())
                .andExpect(jsonPath("$.categoria.id").value(6))
                .andExpect(jsonPath("$.subcategoria.id").value(15))
                .andReturn().getResponse().getContentAsString();

        em.flush();
        em.clear();
        Chamado c = chamados.findByProtocolo(JsonPath.read(resposta, "$.protocolo")).orElseThrow();
        assertThat(c.getVersao()).isZero();
        assertThat(c.getTecnico()).isNull();
        assertThat(c.getSlaPrimeiraRespostaLimite()).isEqualTo(c.getCriadoEm().plus(240, ChronoUnit.MINUTES));
        assertThat(c.getSlaResolucaoLimite()).isEqualTo(c.getCriadoEm().plus(1440, ChronoUnit.MINUTES));

        var eventos = historico.findByChamadoId(c.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).getTipoEvento()).isEqualTo(TipoEventoHistorico.CRIACAO);
        assertThat(eventos.get(0).getAutor().getId()).isEqualTo(4L);
    }

    @Test
    void ignoraCamposDeAutoridadeEnviadosPeloCliente() throws Exception {
        abrir(ANA, "{\"titulo\":\"T\",\"descricao\":\"D\",\"categoriaId\":1,\"solicitanteId\":6,\"tecnicoId\":2,"
                + "\"status\":\"FECHADO\",\"protocolo\":\"CT-1999-000001\",\"id\":999,"
                + "\"slaResolucaoLimite\":\"2030-01-01T00:00:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.solicitante.id").value(4))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.tecnico").doesNotExist())
                .andExpect(jsonPath("$.protocolo").value(not("CT-1999-000001")))
                .andExpect(jsonPath("$.id").value(not(999)))
                .andExpect(jsonPath("$.slaResolucaoLimite").value(not("2030-01-01T00:00:00Z")));
    }

    @Test
    void solicitanteEhSempreOUsuarioAutenticadoQualquerQueSejaOPerfil() throws Exception {
        abrir(TECNICO, corpo("")).andExpect(status().isCreated()).andExpect(jsonPath("$.solicitante.id").value(2));
        abrir(ADMIN, corpo("")).andExpect(status().isCreated()).andExpect(jsonPath("$.solicitante.id").value(1));
    }

    @Test
    void aplicaPrioridadesPermitidasPorPerfil() throws Exception {
        // sucessos antes das falhas: após uma exceção de serviço a transação do teste fica marcada para rollback
        abrir(TECNICO, corpo(",\"prioridade\":\"CRITICA\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.prioridade").value("CRITICA"));
        abrir(ADMIN, corpo(",\"prioridade\":\"CRITICA\"")).andExpect(status().isCreated());
        abrir(ANA, corpo(",\"prioridade\":\"ALTA\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.prioridade").value("ALTA"));
        abrir(ANA, corpo(",\"prioridade\":\"BAIXA\"")).andExpect(status().isCreated());

        abrir(ANA, corpo(",\"prioridade\":\"CRITICA\"")).andExpect(status().isUnprocessableEntity());
        abrir(ANA, corpo(",\"prioridade\":\"URGENTE\"")).andExpect(status().isBadRequest());
    }

    @Test
    void validaCategoriaESubcategoria() throws Exception {
        // preparação (escritas) e caso válido antes de qualquer requisição que falhe
        var subInativa = subcategorias.findById(16L).orElseThrow();
        subInativa.setAtivo(false);
        subcategorias.saveAndFlush(subInativa);
        var catInativa = categorias.findById(7L).orElseThrow();
        catInativa.setAtivo(false);
        categorias.saveAndFlush(catInativa);

        abrir(ANA, corpo(",\"subcategoriaId\":15")).andExpect(status().isCreated());

        abrir(ANA, corpo("").replace("\"categoriaId\":6", "\"categoriaId\":9999"))
                .andExpect(status().isUnprocessableEntity());                                    // inexistente
        abrir(ANA, corpo("").replace("\"categoriaId\":6", "\"categoriaId\":7"))
                .andExpect(status().isUnprocessableEntity());                                    // categoria inativa
        abrir(ANA, corpo(",\"subcategoriaId\":9999")).andExpect(status().isUnprocessableEntity());
        abrir(ANA, corpo(",\"subcategoriaId\":8")).andExpect(status().isUnprocessableEntity());  // VPN é da categoria 3
        abrir(ANA, corpo(",\"subcategoriaId\":16")).andExpect(status().isUnprocessableEntity()); // subcategoria inativa
    }

    @Test
    void validaCorpo() throws Exception {
        abrir(ANA, "{\"titulo\":\" \",\"descricao\":\"D\",\"categoriaId\":6}").andExpect(status().isBadRequest());
        abrir(ANA, "{\"titulo\":\"" + "x".repeat(151) + "\",\"descricao\":\"D\",\"categoriaId\":6}")
                .andExpect(status().isBadRequest());
        abrir(ANA, "{\"titulo\":\"T\",\"descricao\":\"\",\"categoriaId\":6}").andExpect(status().isBadRequest());
        abrir(ANA, "{\"titulo\":\"T\",\"descricao\":\"D\"}").andExpect(status().isBadRequest());
        abrir(ANA, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
        abrir(ANA, "não é json").andExpect(status().isBadRequest());
    }

    @Test
    void protocolosSaoSequenciais() throws Exception {
        long primeiro = numero(abrir(ANA, corpo("")).andExpect(status().isCreated()));
        long segundo = numero(abrir(ANA, corpo("")).andExpect(status().isCreated()));
        assertThat(segundo).isEqualTo(primeiro + 1);
    }

    private static long numero(ResultActions r) throws Exception {
        String protocolo = JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.protocolo");
        return Long.parseLong(protocolo.substring(protocolo.lastIndexOf('-') + 1));
    }
}
