package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Prazos em minutos corridos (máx. 1 ano). */
public record PoliticaSlaRequest(
        @NotNull @Min(1) @Max(525600) @Schema(description = "Prazo da primeira resposta, em minutos corridos.", example = "30") Integer prazoPrimeiraRespostaMin,
        @NotNull @Min(1) @Max(525600) @Schema(description = "Prazo de resolução, em minutos corridos; não pode ser menor que o da primeira resposta.", example = "480") Integer prazoResolucaoMin) {
}
