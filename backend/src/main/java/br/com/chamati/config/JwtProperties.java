package br.com.chamati.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("chamati") String issuer,
        @DefaultValue("PT15M") Duration accessTtl,
        @DefaultValue("P7D") Duration refreshTtl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret (JWT_SECRET) é obrigatório e deve ter ao menos 32 bytes.");
        }
        if (accessTtl.isNegative() || accessTtl.isZero() || refreshTtl.compareTo(accessTtl) <= 0) {
            throw new IllegalArgumentException("refresh-ttl deve ser maior que access-ttl, e ambos positivos.");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTtl=" + accessTtl + ", refreshTtl=" + refreshTtl + "]";
    }
}
