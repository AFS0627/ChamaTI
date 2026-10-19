package br.com.chamati.security;

import br.com.chamati.entity.Usuario;
import br.com.chamati.repository.UsuarioRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Após a assinatura/validade do JWT serem validadas, confirma no banco que o usuário existe e está
 * ativo e usa o perfil ATUAL: desativação ou troca de perfil valem de imediato, sem esperar o token expirar.
 */
@Component
public class JwtUsuarioConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepository usuarios;

    public JwtUsuarioConverter(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Token inválido.");
        }
        Usuario usuario = usuarios.findById(id)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new InvalidBearerTokenException("Token inválido."));
        UsuarioPrincipal principal = new UsuarioPrincipal(usuario.getId(), usuario.getEmail(), usuario.getPerfil());
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, principal.getAuthorities());
    }
}
