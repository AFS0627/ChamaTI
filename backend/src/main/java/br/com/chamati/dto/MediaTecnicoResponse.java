package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MediaTecnicoResponse(
        Long tecnicoId,
        String tecnicoNome,
        @Schema(description = "Média das notas, com duas casas decimais.") double media,
        @Schema(description = "Quantidade de avaliações recebidas.") long quantidade) {
}
