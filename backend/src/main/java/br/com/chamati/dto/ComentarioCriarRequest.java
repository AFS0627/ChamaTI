package br.com.chamati.dto;

import br.com.chamati.enums.TipoComentario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** O tipo é obrigatório de propósito: uma nota interna nunca deve virar pública por omissão. */
public record ComentarioCriarRequest(
        @NotNull @Schema(description = "PUBLICO ou NOTA_INTERNA. Solicitantes só podem usar PUBLICO.") TipoComentario tipo,
        @NotBlank @Size(max = 10000) @Schema(description = "Texto do comentário.") String conteudo) {
}
