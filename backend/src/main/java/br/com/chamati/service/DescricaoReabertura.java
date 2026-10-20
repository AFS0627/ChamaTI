package br.com.chamati.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Texto do evento REABERTURA: motivo + datas anteriores de resolução e fechamento (UTC),
 * no formato da demonstração. Limitado a 500 caracteres (historico_chamados.descricao).
 */
public final class DescricaoReabertura {

    private static final int LIMITE = 500;
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private DescricaoReabertura() {
    }

    public static String formatar(String motivo, Instant resolvidoEm, Instant fechadoEm) {
        String sufixo = ". resolvido_em anterior: " + data(resolvidoEm)
                + "; fechado_em anterior: " + data(fechadoEm);
        String texto = motivo.trim().replaceAll("[.\\s]+$", "");
        int maximo = LIMITE - sufixo.length();
        if (texto.length() > maximo) {
            texto = texto.substring(0, maximo);
        }
        return texto + sufixo;
    }

    private static String data(Instant instante) {
        return instante == null ? "nenhum" : DATA.format(instante);
    }
}
