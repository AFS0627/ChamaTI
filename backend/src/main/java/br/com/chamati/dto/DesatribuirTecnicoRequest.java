package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record DesatribuirTecnicoRequest(
        @Size(max = 400) @Schema(description = "Opcional; vai para o histórico e é visível ao solicitante.", example = "Técnico em férias") String motivo,
        @Schema(description = "Opcional. Versão do chamado lida pelo cliente; se diferente da atual, a operação responde 409.") Long versao) {
}
