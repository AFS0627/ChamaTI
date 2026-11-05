package br.com.chamati.service;

import java.util.Optional;

/** Tipos aceitos como anexo, identificados pelo conteúdo (assinatura do arquivo) e não pelo nome ou pelo Content-Type enviado. */
public enum TipoAnexo {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    PDF("application/pdf", "pdf");

    private final String tipoConteudo;
    private final String extensao;

    TipoAnexo(String tipoConteudo, String extensao) {
        this.tipoConteudo = tipoConteudo;
        this.extensao = extensao;
    }

    public String tipoConteudo() {
        return tipoConteudo;
    }

    public String extensao() {
        return extensao;
    }

    public static Optional<TipoAnexo> detectar(byte[] conteudo) {
        if (conteudo == null) {
            return Optional.empty();
        }
        if (comeca(conteudo, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (comeca(conteudo, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (comeca(conteudo, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean comeca(byte[] conteudo, int... assinatura) {
        if (conteudo.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if ((conteudo[i] & 0xFF) != assinatura[i]) {
                return false;
            }
        }
        return true;
    }
}
