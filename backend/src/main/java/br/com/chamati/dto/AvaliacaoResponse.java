package br.com.chamati.dto;

import java.time.Instant;

public record AvaliacaoResponse(Long id, Long chamadoId, String protocolo, int nota, String comentario,
                                AutorResponse solicitante, AutorResponse tecnico, Instant criadoEm) {
}
