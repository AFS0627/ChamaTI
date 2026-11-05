package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TipoAnexoTest {

    @Test
    void reconheceJpegPngEPdfPelaAssinatura() {
        assertThat(TipoAnexo.detectar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0}))
                .contains(TipoAnexo.JPEG);
        assertThat(TipoAnexo.detectar(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1}))
                .contains(TipoAnexo.PNG);
        assertThat(TipoAnexo.detectar("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains(TipoAnexo.PDF);
    }

    @Test
    void rejeitaOutrosConteudosVaziosEAssinaturasIncompletas() {
        assertThat(TipoAnexo.detectar("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(TipoAnexo.detectar("texto qualquer".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(TipoAnexo.detectar("MZ executavel".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(TipoAnexo.detectar(new byte[0])).isEmpty();
        assertThat(TipoAnexo.detectar(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(TipoAnexo.detectar(new byte[]{(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
        assertThat(TipoAnexo.detectar(null)).isEmpty();
    }

    @Test
    void expoTipoDeConteudoEExtensao() {
        assertThat(TipoAnexo.JPEG.tipoConteudo()).isEqualTo("image/jpeg");
        assertThat(TipoAnexo.JPEG.extensao()).isEqualTo("jpg");
        assertThat(TipoAnexo.PNG.tipoConteudo()).isEqualTo("image/png");
        assertThat(TipoAnexo.PDF.tipoConteudo()).isEqualTo("application/pdf");
    }
}
