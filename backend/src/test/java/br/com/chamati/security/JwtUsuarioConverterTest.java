package br.com.chamati.security;

import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class JwtUsuarioConverterTest {

    @Mock UsuarioRepository usuarios;
    @InjectMocks JwtUsuarioConverter converter;

    private static Jwt jwt(String subject, String perfil) {
        Jwt.Builder b = Jwt.withTokenValue("token").header("alg", "HS256").claim("perfil", perfil);
        if (subject != null) {
            b.subject(subject);
        }
        return b.build();
    }

    @Test
    void usuarioAtivoViraPrincipalAutenticadoComPerfilDoBanco() {
        Usuario tecnico = usuario(7L, "Carlos Mendes", Perfil.TECNICO);
        when(usuarios.findById(7L)).thenReturn(Optional.of(tecnico));

        // o token ainda diz ADMIN, mas o perfil atual do banco é TECNICO
        Authentication auth = converter.convert(jwt("7", "ADMIN"));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(new UsuarioPrincipal(7L, tecnico.getEmail(), Perfil.TECNICO));
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_TECNICO");
    }

    @Test
    void usuarioDesativadoTemOTokenRecusadoNaHora() {
        Usuario inativo = usuario(7L, "Carlos Mendes", Perfil.TECNICO);
        inativo.setAtivo(false);
        when(usuarios.findById(7L)).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> converter.convert(jwt("7", "TECNICO")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void usuarioInexistenteTemOTokenRecusado() {
        when(usuarios.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt("7", "TECNICO")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void subjectNaoNumericoOuAusenteERecusadoSemConsultarOBanco() {
        assertThatThrownBy(() -> converter.convert(jwt("abc", "ADMIN")))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(jwt(null, "ADMIN")))
                .isInstanceOf(InvalidBearerTokenException.class);

        verifyNoInteractions(usuarios);
    }
}
