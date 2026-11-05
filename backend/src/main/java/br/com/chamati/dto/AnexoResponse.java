package br.com.chamati.dto;

import java.time.Instant;

public record AnexoResponse(Long id, String nome, String tipoConteudo, long tamanho, AutorResponse autor,
                            Instant criadoEm) {
}
