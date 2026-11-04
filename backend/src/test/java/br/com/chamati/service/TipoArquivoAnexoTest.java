package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TipoArquivoAnexoTest {

    static byte[] bytes(int... valores) {
        byte[] b = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            b[i] = (byte) valores[i];
        }
        return b;
    }

    @Test
    void identificaJpegPngEPdfPeloConteudo() {
        assertThat(TipoArquivoAnexo.identificar(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10))).contains(TipoArquivoAnexo.JPEG);
        assertThat(TipoArquivoAnexo.identificar(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0))).contains(TipoArquivoAnexo.PNG);
        assertThat(TipoArquivoAnexo.identificar("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains(TipoArquivoAnexo.PDF);
    }

    @Test
    void tiposDevolvemOContentTypeCorreto() {
        assertThat(TipoArquivoAnexo.JPEG.tipoConteudo()).isEqualTo("image/jpeg");
        assertThat(TipoArquivoAnexo.PNG.tipoConteudo()).isEqualTo("image/png");
        assertThat(TipoArquivoAnexo.PDF.tipoConteudo()).isEqualTo("application/pdf");
    }

    @Test
    void recusaOutrosTiposVaziosEAssinaturasIncompletas() {
        assertThat(TipoArquivoAnexo.identificar("MZ executavel".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(TipoArquivoAnexo.identificar("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(TipoArquivoAnexo.identificar("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(TipoArquivoAnexo.identificar(new byte[0])).isEmpty();
        assertThat(TipoArquivoAnexo.identificar(null)).isEmpty();
        assertThat(TipoArquivoAnexo.identificar(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(TipoArquivoAnexo.identificar(bytes(0x89, 'P', 'N', 'G'))).isEmpty();
    }
}
