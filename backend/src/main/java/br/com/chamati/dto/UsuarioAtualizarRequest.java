package br.com.chamati.dto;

import br.com.chamati.enums.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Não altera senha nem situação (ativo): há endpoints próprios para isso. */
public record UsuarioAtualizarRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 160) String email,
        @NotNull Perfil perfil,
        @Size(max = 80) String departamento,
        @Size(max = 30) String telefone) {
}
