package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TrocarSenhaRequest(
        @NotBlank @Size(max = 72)
        @Schema(description = "Senha em uso, para confirmar que quem pede é o titular da conta.", format = "password") String senhaAtual,
        @NotBlank
        @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "deve conter ao menos uma letra e um número")
        @Schema(description = "De 8 a 72 caracteres (até 72 bytes), com ao menos uma letra e um número, e diferente da atual.", format = "password") String novaSenha) {

    /** As senhas nunca devem aparecer em logs. */
    @Override
    public String toString() {
        return "TrocarSenhaRequest[senhaAtual=***, novaSenha=***]";
    }
}
