package br.com.chamati.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Prazos em minutos corridos (máx. 1 ano). */
public record PoliticaSlaRequest(
        @NotNull @Min(1) @Max(525600) Integer prazoPrimeiraRespostaMin,
        @NotNull @Min(1) @Max(525600) Integer prazoResolucaoMin) {
}
