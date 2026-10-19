package br.com.chamati.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResponse(Instant timestamp, int status, String erro, String mensagem, List<Campo> campos) {

    public record Campo(String campo, String mensagem) {
    }

    public static ErroResponse de(int status, String erro, String mensagem) {
        return new ErroResponse(Instant.now(), status, erro, mensagem, List.of());
    }
}
