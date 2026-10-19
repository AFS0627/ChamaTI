package br.com.chamati.mapper;

import br.com.chamati.dto.UsuarioResponse;
import br.com.chamati.entity.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public UsuarioResponse paraResponse(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                u.getDepartamento(), u.getTelefone(), u.isAtivo(), u.getUltimoLoginEm(), u.getCriadoEm());
    }
}
