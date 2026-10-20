package br.com.chamati.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReabrirChamadoRequest(@NotBlank @Size(max = 300) String motivo, Long versao) {
}
