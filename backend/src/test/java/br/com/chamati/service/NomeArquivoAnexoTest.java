package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NomeArquivoAnexoTest {

    @Test
    void mantemNomeSimplesComAcentos() {
        assertThat(NomeArquivoAnexo.normalizar("relatório de erro.pdf", TipoArquivoAnexo.PDF)).isEqualTo("relatório de erro.pdf");
    }

    @Test
    void removeCaminhosDeUnixEWindows() {
        assertThat(NomeArquivoAnexo.normalizar("../../etc/passwd.png", TipoArquivoAnexo.PNG)).isEqualTo("passwd.png");
        assertThat(NomeArquivoAnexo.normalizar("C:\\Users\\ana\\tela.jpg", TipoArquivoAnexo.JPEG)).isEqualTo("tela.jpg");
    }

    @Test
    void removeCaracteresDeControle() {
        assertThat(NomeArquivoAnexo.normalizar("tela\r\n.png\u0000", TipoArquivoAnexo.PNG)).isEqualTo("tela.png");
    }

    @Test
    void usaNomePadraoQuandoNaoSobraNome() {
        assertThat(NomeArquivoAnexo.normalizar(null, TipoArquivoAnexo.PDF)).isEqualTo("anexo.pdf");
        assertThat(NomeArquivoAnexo.normalizar("   ", TipoArquivoAnexo.PNG)).isEqualTo("anexo.png");
        assertThat(NomeArquivoAnexo.normalizar("..", TipoArquivoAnexo.JPEG)).isEqualTo("anexo.jpg");
        assertThat(NomeArquivoAnexo.normalizar("pasta/", TipoArquivoAnexo.JPEG)).isEqualTo("anexo.jpg");
    }

    @Test
    void limitaOTamanhoPreservandoAExtensao() {
        String nome = NomeArquivoAnexo.normalizar("a".repeat(400) + ".pdf", TipoArquivoAnexo.PDF);

        assertThat(nome).hasSize(NomeArquivoAnexo.TAMANHO_MAXIMO).endsWith(".pdf");
    }
}
