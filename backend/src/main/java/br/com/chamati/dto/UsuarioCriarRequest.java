package br.com.chamati.dto;

import br.com.chamati.enums.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioCriarRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank
        @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "deve conter ao menos uma letra e um número")
        @Schema(description = "De 8 a 72 caracteres, com ao menos uma letra e um número.", format = "password") String senha,
        @NotNull @Schema(description = "ADMIN, TECNICO ou SOLICITANTE.") Perfil perfil,
        @Size(max = 80) String departamento,
        @Size(max = 30) String telefone) {

    /** A senha nunca deve aparecer em logs. */
    @Override
    public String toString() {
        return "UsuarioCriarRequest[nome=" + nome + ", email=" + email + ", perfil=" + perfil + ", senha=***]";
    }
}
