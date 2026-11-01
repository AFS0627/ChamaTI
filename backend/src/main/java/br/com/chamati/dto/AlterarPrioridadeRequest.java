package br.com.chamati.dto;

import br.com.chamati.enums.Prioridade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlterarPrioridadeRequest(
        @NotNull @Schema(description = "Nova prioridade. Os limites de SLA são recalculados a partir da abertura do chamado.") Prioridade prioridade,
        @Size(max = 400) @Schema(description = "Opcional; vai para o histórico e é visível ao solicitante.", example = "Impacto em todos os usuários remotos") String motivo,
        @Schema(description = "Opcional. Versão do chamado lida pelo cliente; se diferente da atual, a operação responde 409.") Long versao) {
}
