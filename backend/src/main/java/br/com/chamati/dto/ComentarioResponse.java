package br.com.chamati.dto;

import br.com.chamati.enums.TipoComentario;
import java.time.Instant;

public record ComentarioResponse(Long id, TipoComentario tipo, String conteudo, AutorResponse autor, Instant criadoEm) {
}
