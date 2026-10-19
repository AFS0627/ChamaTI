package br.com.chamati.dto;

import java.time.Instant;

public record TokenResponse(
        String tipo,
        String accessToken,
        long expiraEmSegundos,
        String refreshToken,
        Instant refreshExpiraEm,
        UsuarioSessaoResponse usuario) {

    @Override
    public String toString() {
        return "TokenResponse[usuario=" + usuario + ", tokens=***]";
    }
}
