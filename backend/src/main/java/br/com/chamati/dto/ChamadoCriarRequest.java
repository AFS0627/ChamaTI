package br.com.chamati.dto;

import br.com.chamati.enums.Prioridade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Somente estes campos são aceitos. Solicitante, status, protocolo, técnico e prazos nunca vêm
 * do cliente (qualquer outro campo enviado é ignorado). Prioridade omitida = MEDIA.
 */
public record ChamadoCriarRequest(
        @NotBlank @Size(max = 150) String titulo,
        @NotBlank @Size(max = 10000) String descricao,
        @NotNull Long categoriaId,
        Long subcategoriaId,
        Prioridade prioridade) {
}
