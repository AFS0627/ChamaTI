package br.com.chamati.dto;

public record SubcategoriaResponse(Long id, Long categoriaId, String nome, String descricao, boolean ativo) {
}
