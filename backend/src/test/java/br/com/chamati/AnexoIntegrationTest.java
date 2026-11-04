package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.AnexoRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstração: 3 EM_ATENDIMENTO tec2 juliana | 5 ABERTO sem técnico bruno | 1 FECHADO tec2 ana. Em cada teste, os
 * sucessos vêm antes das requisições que falham.
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

    static final byte[] PDF = "%PDF-1.7\nconteudo do relatorio".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired MockMvc mvc;
    @Autowired AnexoRepository anexos;

    private ResultActions anexar(RequestPostProcessor quem, long chamadoId, String nome, String tipo, byte[] conteudo) throws Exception {
        return mvc.perform(multipart("/api/chamados/" + chamadoId + "/anexos")
                .file(new MockMultipartFile("arquivo", nome, tipo, conteudo)).with(quem));
    }

    private long anexarComSucesso(RequestPostProcessor quem, long chamadoId, String nome, String tipo, byte[] conteudo) throws Exception {
        String corpo = anexar(quem, chamadoId, nome, tipo, conteudo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }

    @Test
    void solicitanteAnexaPdfAoProprioChamadoEOConteudoFicaNoBanco() throws Exception {
        long antes = anexos.count();

        String corpo = anexar(JULIANA, 3, "relatório.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeArquivo").value("relatório.pdf"))
                .andExpect(jsonPath("$.tipoConteudo").value("application/pdf"))
                .andExpect(jsonPath("$.tamanho").value(PDF.length))
                .andExpect(jsonPath("$.autor.id").value(6))
                .andExpect(jsonPath("$.autor.perfil").value("SOLICITANTE"))
                .andExpect(jsonPath("$.criadoEm").exists())
                .andExpect(jsonPath("$.conteudo").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        long id = ((Number) JsonPath.read(corpo, "$.id")).longValue();
        assertThat(anexos.count()).isEqualTo(antes + 1);
        assertThat(anexos.findById(id).orElseThrow().getConteudo()).isEqualTo(PDF);
    }

    @Test
    void tecnicoResponsavelEAdminAnexamImagens() throws Exception {
        anexarComSucesso(CARLOS, 3, "tela.png", "image/png", PNG);
        anexarComSucesso(ADMIN, 3, "foto.jpg", "image/jpeg", JPG);

        mvc.perform(get("/api/chamados/3/anexos").with(JULIANA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipoConteudo").value("image/png"))
                .andExpect(jsonPath("$[1].tipoConteudo").value("image/jpeg"));
    }

    @Test
    void tecnicoPodeAnexarEmChamadoSemTecnico() throws Exception {
        anexarComSucesso(FERNANDA, 5, "log.pdf", "application/pdf", PDF);
    }

    @Test
    void tipoEDetectadoPeloConteudoENomeESaneado() throws Exception {
        String corpo = anexar(JULIANA, 3, "../../etc/foto.pdf", "application/pdf", PNG)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat((String) JsonPath.read(corpo, "$.nomeArquivo")).isEqualTo("foto.pdf");
        assertThat((String) JsonPath.read(corpo, "$.tipoConteudo")).isEqualTo("image/png");
    }

    @Test
    void listaSemOConteudoEBaixaComOTipoEOCabecalhoDeDownload() throws Exception {
        long id = anexarComSucesso(JULIANA, 3, "erro.pdf", "application/pdf", PDF);

        mvc.perform(get("/api/chamados/3/anexos").with(CARLOS)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].conteudo").doesNotExist());

        byte[] baixado = mvc.perform(get("/api/chamados/3/anexos/" + id).with(CARLOS))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("attachment"),
                        org.hamcrest.Matchers.containsString("erro.pdf"))))
                .andExpect(header().string("Content-Length", String.valueOf(PDF.length)))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(baixado).isEqualTo(PDF);
    }

    @Test
    void usuariosSemVisibilidadeRecebem404EmTodasAsOperacoes() throws Exception {
        long id = anexarComSucesso(JULIANA, 3, "erro.pdf", "application/pdf", PDF);

        mvc.perform(get("/api/chamados/3/anexos").with(ANA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/3/anexos/" + id).with(BRUNO)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/3/anexos/" + id).with(FERNANDA)).andExpect(status().isNotFound());
        anexar(ANA, 3, "x.pdf", "application/pdf", PDF).andExpect(status().isNotFound());
    }

    @Test
    void anexoInexistenteOuDeOutroChamadoResponde404() throws Exception {
        long id = anexarComSucesso(JULIANA, 3, "erro.pdf", "application/pdf", PDF);

        mvc.perform(get("/api/chamados/5/anexos/" + id).with(ADMIN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Anexo não encontrado."));
        mvc.perform(get("/api/chamados/3/anexos/999999").with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chamados/999999/anexos").with(ADMIN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Chamado não encontrado."));
    }

    @Test
    void chamadoFechadoNaoRecebeAnexosMasPermiteConsultar() throws Exception {
        mvc.perform(get("/api/chamados/1/anexos").with(ANA)).andExpect(status().isOk());

        anexar(ANA, 1, "x.pdf", "application/pdf", PDF).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Chamados fechados não recebem anexos. Reabra o chamado."));
    }

    @Test
    void recusaArquivoVazioETiposNaoPermitidos() throws Exception {
        anexar(JULIANA, 3, "vazio.pdf", "application/pdf", new byte[0]).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("O arquivo está vazio."));
        anexar(JULIANA, 3, "pagina.png", "image/png", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Tipo de arquivo não permitido. Envie JPG, PNG ou PDF."));
        anexar(JULIANA, 3, "animacao.gif", "image/gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isUnprocessableEntity());
        assertThat(anexos.countByChamadoId(3L)).isZero();
    }

    @Test
    void arquivoMaiorQue5MbRetorna413() throws Exception {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);

        anexar(JULIANA, 3, "grande.pdf", "application/pdf", grande).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.mensagem").value("O arquivo excede o tamanho máximo de 5 MB."));
    }

    @Test
    void semAParteArquivoRetorna400EmErroResponse() throws Exception {
        mvc.perform(multipart("/api/chamados/3/anexos").with(JULIANA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("Parte obrigatória ausente: 'arquivo'."));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(multipart("/api/chamados/3/anexos").file(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", PDF)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/3/anexos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/3/anexos/1")).andExpect(status().isUnauthorized());
    }
}
