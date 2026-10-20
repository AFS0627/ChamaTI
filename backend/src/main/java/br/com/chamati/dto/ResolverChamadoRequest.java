package br.com.chamati.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolverChamadoRequest(@NotBlank @Size(max = 10000) String solucao, Long versao) {
}
