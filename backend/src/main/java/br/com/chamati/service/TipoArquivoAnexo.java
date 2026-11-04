package br.com.chamati.service;

import java.util.Arrays;
import java.util.Optional;

/** Tipos aceitos como anexo. O tipo é identificado pelos primeiros bytes, nunca pelo nome ou pelo Content-Type enviado. */
public enum TipoArquivoAnexo {
    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    PDF("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'});

    private final String tipoConteudo;
    private final byte[] assinatura;

    TipoArquivoAnexo(String tipoConteudo, byte[] assinatura) {
        this.tipoConteudo = tipoConteudo;
        this.assinatura = assinatura;
    }

    public String tipoConteudo() {
        return tipoConteudo;
    }

    public static Optional<TipoArquivoAnexo> identificar(byte[] conteudo) {
        if (conteudo == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(t -> t.confere(conteudo)).findFirst();
    }

    private boolean confere(byte[] conteudo) {
        if (conteudo.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if (conteudo[i] != assinatura[i]) {
                return false;
            }
        }
        return true;
    }
}
