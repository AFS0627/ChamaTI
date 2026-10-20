package br.com.chamati.service;

import java.util.Locale;

/** Formato do protocolo: CT-AAAA-NNNNNN (ano em UTC + número sequencial do ano, 6 dígitos mínimos). */
public final class ProtocoloFormato {

    private ProtocoloFormato() {
    }

    public static String formatar(int ano, long numero) {
        if (ano < 1000 || ano > 9999) {
            throw new IllegalArgumentException("Ano inválido para o protocolo.");
        }
        if (numero < 1) {
            throw new IllegalArgumentException("Número de protocolo inválido.");
        }
        return String.format(Locale.ROOT, "CT-%04d-%06d", ano, numero);
    }
}
