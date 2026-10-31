package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReabrirChamadoRequest(@NotBlank @Size(max = 300) @Schema(description = "Motivo da reabertura.") String motivo, @Schema(description = "Opcional. Versão do chamado lida pelo cliente; se diferente da atual, a operação responde 409.") Long versao) {
}
