package br.com.chamati.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chamati.config.JwtConfig;
import br.com.chamati.config.JwtProperties;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    static final String SEGREDO = "segredo-apenas-para-testes-0123456789-abcdefghij";

    static JwtProperties props(String segredo, String emissor) {
        return new JwtProperties(segredo, emissor, Duration.ofMinutes(15), Duration.ofDays(7));
    }

    static Usuario usuario() {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", 7L);
        u.setPerfil(Perfil.TECNICO);
        return u;
    }

    final JwtProperties props = props(SEGREDO, "chamati");
    final JwtConfig config = new JwtConfig();
    final JwtDecoder decoder = config.jwtDecoder(props);

    JwtService servico(JwtProperties p, Clock relogio) {
        return new JwtService(config.jwtEncoder(p), p, relogio);
    }

    @Test
    void geraTokenValidoComClaimsEsperadas() {
        String token = servico(props, Clock.systemUTC()).gerarAccessToken(usuario());
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("chamati");
        assertThat(jwt.<String>getClaim("perfil")).isEqualTo("TECNICO");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getClaims()).doesNotContainKeys("senha", "senhaHash", "email");
    }

    @Test
    void rejeitaTokenExpirado() {
        Clock passado = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = servico(props, passado).gerarAccessToken(usuario());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejeitaAssinaturaDeOutroSegredo() {
        String token = servico(props("outro-segredo-outro-segredo-outro-segredo-123", "chamati"), Clock.systemUTC())
                .gerarAccessToken(usuario());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejeitaEmissorDiferente() {
        String token = servico(props(SEGREDO, "outro-emissor"), Clock.systemUTC()).gerarAccessToken(usuario());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejeitaTokenAdulterado() {
        String token = servico(props, Clock.systemUTC()).gerarAccessToken(usuario());
        String[] partes = token.split("\\.");
        String adulterado = partes[0] + "." + partes[1].substring(0, partes[1].length() - 2) + "AA." + partes[2];
        assertThatThrownBy(() -> decoder.decode(adulterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejeitaTokenSemAssinatura() {
        String token = servico(props, Clock.systemUTC()).gerarAccessToken(usuario());
        String[] partes = token.split("\\.");
        String semAssinatura = "eyJhbGciOiJub25lIn0." + partes[1] + ".";
        assertThatThrownBy(() -> decoder.decode(semAssinatura)).isInstanceOf(JwtException.class);
    }

    @Test
    void segredoCurtoImpedeInicializacao() {
        assertThatThrownBy(() -> props("curto", "chamati")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props(null, "chamati")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNaoExpoeSegredo() {
        assertThat(props.toString()).doesNotContain(SEGREDO);
    }
}
