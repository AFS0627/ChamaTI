package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 160) @Schema(description = "E-mail do usuário; não diferencia maiúsculas.", example = "admin@chamati.local") String email,
        @NotBlank @Size(max = 72) @Schema(description = "Senha do usuário.", format = "password") String senha) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", senha=***]";
    }
}
