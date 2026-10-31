package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(@NotBlank @Size(max = 200) @Schema(description = "Refresh token recebido no login ou na última renovação.") String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
