package br.com.chamati.dto;

import br.com.chamati.enums.Perfil;

public record AutorResponse(Long id, String nome, Perfil perfil) {
}
