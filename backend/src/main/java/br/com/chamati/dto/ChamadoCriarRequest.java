package br.com.chamati.dto;

import br.com.chamati.enums.Prioridade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Somente estes campos são aceitos. Solicitante, status, protocolo, técnico e prazos nunca vêm
 * do cliente (qualquer outro campo enviado é ignorado). Prioridade omitida = MEDIA.
 */
public record ChamadoCriarRequest(
        @NotBlank @Size(max = 150) @Schema(description = "Resumo do problema.", example = "Sem acesso à VPN") String titulo,
        @NotBlank @Size(max = 10000) @Schema(description = "Descrição detalhada.", example = "Não conecto desde ontem.") String descricao,
        @NotNull @Schema(description = "Categoria ativa.", example = "3") Long categoriaId,
        @Schema(description = "Opcional; precisa ser ativa e pertencer à categoria.", example = "8") Long subcategoriaId,
        @Schema(description = "Opcional (padrão MEDIA). CRITICA só para TECNICO e ADMIN.") Prioridade prioridade) {
}
