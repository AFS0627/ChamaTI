package br.com.chamati.dto;

import br.com.chamati.enums.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlterarStatusRequest(
        @NotNull @Schema(description = "Novo status em andamento (não aceita RESOLVIDO nem FECHADO).") StatusChamado status,
        @Size(max = 400) @Schema(description = "Opcional; vai para o histórico e é visível ao solicitante.") String observacao,
        @Schema(description = "Opcional. Versão do chamado lida pelo cliente; se diferente da atual, a operação responde 409.") Long versao) {
}
