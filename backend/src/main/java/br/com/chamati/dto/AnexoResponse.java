package br.com.chamati.dto;

import java.time.Instant;

public record AnexoResponse(Long id, String nomeArquivo, String tipoConteudo, long tamanho, AutorResponse autor,
                            Instant criadoEm) {
}
