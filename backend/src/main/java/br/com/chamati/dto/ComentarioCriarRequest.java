package br.com.chamati.dto;

import br.com.chamati.enums.TipoComentario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** O tipo é obrigatório de propósito: uma nota interna nunca deve virar pública por omissão. */
public record ComentarioCriarRequest(
        @NotNull TipoComentario tipo,
        @NotBlank @Size(max = 10000) String conteudo) {
}
