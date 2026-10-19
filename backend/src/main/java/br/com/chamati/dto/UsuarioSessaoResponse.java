package br.com.chamati.dto;

import br.com.chamati.enums.Perfil;

public record UsuarioSessaoResponse(Long id, String nome, String email, Perfil perfil) {
}
