package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.repository.AnexoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstração: 3 EM_ATENDIMENTO tec2 juliana | 5 ABERTO sem técnico bruno | 1 FECHADO tec2 ana
 * | 2 RESOLVIDO tec3 bruno. Em cada teste, os sucessos vêm antes das requisições que falham.
 */
@Transactional
@AutoConfigureMockMvc
class AnexoIntegrationTest extends IntegrationTestBase {

    static final RequestPostProcessor ADMIN = UsuarioControllerIntegrationTest.como(Perfil.ADMIN, 1L);
    static final RequestPostProcessor CARLOS = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 2L);
    static final RequestPostProcessor FERNANDA = UsuarioControllerIntegrationTest.como(Perfil.TECNICO, 3L);
    static final RequestPostProcessor ANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 4L);
    static final RequestPostProcessor BRUNO = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 5L);
    static final RequestPostProcessor JULIANA = UsuarioControllerIntegrationTest.como(Perfil.SOLICITANTE, 6L);

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 5, 6};
    static final byte[] PDF = "%PDF-1.4\n%conteudo".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired AnexoRepository anexos;
    @Autowired HistoricoChamadoRepository historico;

    private ResultActions enviar(RequestPostProcessor quem, long chamadoId, String nome, String tipo, byte[] bytes)
            throws Exception {
        return mvc.perform(multipart("/api/chamados/" + chamadoId + "/anexos")
                .file(new MockMultipartFile("arquivo", nome, tipo, bytes)).with(quem));
    }

    private long idDe(ResultActions r) throws Exception {
        String corpo = r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }

    private List<HistoricoChamado> eventosDeAnexo(long id) {
        return historico.findByChamadoId(id, PageRequest.of(0, 100, Sort.by("criadoEm", "id"))).getContent().stream()
                .filter(e -> e.getTipoEvento() == TipoEventoHistorico.EDICAO && "anexo".equals(e.getCampo()))
                .toList();
    }

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        mvc.perform(multipart("/api/chamados/3/anexos")
                        .file(new MockMultipartFile("arquivo", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/3/anexos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/3/anexos/1/conteudo")).andExpect(status().isUnauthorized());
    }

    @Test
    void anexaListaEBaixaOsTresTiposComHistorico() throws Exception {
        long png = idDe(enviar(JULIANA, 3, "foto.png", "image/png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("foto.png"))
                .andExpect(jsonPath("$.tipoConteudo").value("image/png"))
                .andExpect(jsonPath("$.tamanho").value(PNG.length))
                .andExpect(jsonPath("$.autor.id").value(6))
                .andExpect(jsonPath("$.chaveArmazenamento").doesNotExist()));
        enviar(CARLOS, 3, "scan.jpg", "image/jpeg", JPEG)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoConteudo").value("image/jpeg"));
        enviar(ADMIN, 3, "relatório.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoConteudo").value("application/pdf"));

        mvc.perform(get("/api/chamados/3/anexos").with(JULIANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(png))
                .andExpect(jsonPath("$[1].nome").value("scan.jpg"))
                .andExpect(jsonPath("$[2].nome").value("relatório.pdf"))
                .andExpect(jsonPath("$[0].chaveArmazenamento").doesNotExist());

        byte[] baixado = mvc.perform(get("/api/chamados/3/anexos/" + png + "/conteudo").with(CARLOS))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Length", String.valueOf(PNG.length)))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(baixado).isEqualTo(PNG);

        assertThat(eventosDeAnexo(3)).hasSize(3)
                .extracting(HistoricoChamado::getValorNovo).containsExactly("foto.png", "scan.jpg", "relatório.pdf");
        assertThat(eventosDeAnexo(3).get(0).getDescricao()).isEqualTo("Anexo adicionado");
        assertThat(eventosDeAnexo(3).get(0).getAutor().getId()).isEqualTo(6L);
    }

    @Test
    void oTipoVemDoConteudoENaoDoNomeNemDoContentTypeEnviado() throws Exception {
        enviar(JULIANA, 3, "documento.pdf", "application/pdf", PNG)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoConteudo").value("image/png"));
        enviar(JULIANA, 3, "..\\..\\pasta/foto.png", "image/png", PNG)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("foto.png"));
    }

    @Test
    void chamadoResolvidoAindaRecebeAnexosETecnicoSemAtribuicaoVeChamadoNaoAtribuido() throws Exception {
        enviar(BRUNO, 2, "foto.png", "image/png", PNG).andExpect(status().isCreated());
        enviar(CARLOS, 5, "foto.png", "image/png", PNG).andExpect(status().isCreated());
    }

    @Test
    void conteudoDeOutroChamadoOuInexistenteRetorna404() throws Exception {
        long id = idDe(enviar(JULIANA, 3, "foto.png", "image/png", PNG).andExpect(status().isCreated()));

        mvc.perform(get("/api/chamados/5/anexos/" + id + "/conteudo").with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/3/anexos/999999/conteudo").with(ADMIN))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensagem").value("Anexo não encontrado."));
    }

    @Test
    void quemNaoEnxergaOChamadoRecebe404EmTodasAsOperacoes() throws Exception {
        long id = idDe(enviar(JULIANA, 3, "foto.png", "image/png", PNG).andExpect(status().isCreated()));

        enviar(BRUNO, 3, "a.png", "image/png", PNG).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/3/anexos").with(BRUNO)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/3/anexos/" + id + "/conteudo").with(BRUNO)).andExpect(status().isNotFound());
        enviar(FERNANDA, 3, "a.png", "image/png", PNG).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/3/anexos").with(FERNANDA)).andExpect(status().isNotFound());
        enviar(ADMIN, 999999, "a.png", "image/png", PNG).andExpect(status().isNotFound());
    }

    @Test
    void chamadoFechadoNaoRecebeAnexosMasPermiteConsulta() throws Exception {
        mvc.perform(get("/api/chamados/1/anexos").with(ANA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        enviar(ANA, 1, "a.png", "image/png", PNG)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Chamados fechados não recebem anexos. Reabra o chamado."));
    }

    @Test
    void rejeitaTiposNaoPermitidosEArquivoVazio() throws Exception {
        enviar(JULIANA, 3, "a.png", "image/png", "<html>não sou imagem</html>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Tipo de arquivo não permitido. Envie JPG, PNG ou PDF."));
        enviar(JULIANA, 3, "a.gif", "image/gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isUnprocessableEntity());
        enviar(JULIANA, 3, "vazio.png", "image/png", new byte[0]).andExpect(status().isUnprocessableEntity());

        assertThat(anexos.countByChamadoId(3L)).isZero();
    }

    @Test
    void arquivoMaiorQueOLimiteRetorna413() throws Exception {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        enviar(JULIANA, 3, "grande.png", "image/png", grande)
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.mensagem").value("O arquivo excede o tamanho máximo permitido."));
    }

    @Test
    void semOCampoArquivoRetorna400() throws Exception {
        mvc.perform(multipart("/api/chamados/3/anexos").file(new MockMultipartFile("outro", PNG)).with(JULIANA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Envie o arquivo no campo 'arquivo'."));
    }

    @Test
    void respeitaOLimiteDeDezAnexosPorChamado() throws Exception {
        for (int i = 0; i < 10; i++) {
            enviar(JULIANA, 3, "f" + i + ".png", "image/png", PNG).andExpect(status().isCreated());
        }
        assertThat(anexos.countByChamadoId(3L)).isEqualTo(10);

        enviar(JULIANA, 3, "extra.png", "image/png", Arrays.copyOf(PNG, PNG.length))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("O chamado já tem o máximo de 10 anexos."));
    }
}
