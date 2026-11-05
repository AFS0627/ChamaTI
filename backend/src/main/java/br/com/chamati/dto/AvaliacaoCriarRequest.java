package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvaliacaoCriarRequest(
        @NotNull @Min(1) @Max(5) @Schema(description = "Nota de 1 a 5 estrelas.") Integer nota,
        @Size(max = 1000) @Schema(description = "Comentário opcional.") String comentario) {
}
