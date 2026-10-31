package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record TokenResponse(
        @Schema(description = "Esquema de autenticação do header Authorization.", example = "Bearer") String tipo,
        @Schema(description = "JWT de acesso (HS256); enviar em Authorization: Bearer.") String accessToken,
        @Schema(description = "Validade do access token, em segundos.", example = "900") long expiraEmSegundos,
        @Schema(description = "Token opaco de renovação, de uso único.") String refreshToken,
        @Schema(description = "Data limite do refresh token.") Instant refreshExpiraEm,
        UsuarioSessaoResponse usuario) {

    @Override
    public String toString() {
        return "TokenResponse[usuario=" + usuario + ", tokens=***]";
    }
}
