package br.com.chamati.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResponse(
        @Schema(description = "Momento do erro (UTC).") Instant timestamp,
        @Schema(description = "Código HTTP.") int status,
        @Schema(description = "Descrição padrão do código HTTP.") String erro,
        @Schema(description = "Mensagem para o usuário; nunca inclui valores enviados.") String mensagem,
        @Schema(description = "Campos inválidos (somente em erros de validação 400).") List<Campo> campos) {

    public record Campo(String campo, String mensagem) {
    }

    public static ErroResponse de(int status, String erro, String mensagem) {
        return new ErroResponse(Instant.now(), status, erro, mensagem, List.of());
    }
}
