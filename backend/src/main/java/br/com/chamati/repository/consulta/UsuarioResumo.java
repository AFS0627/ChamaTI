package br.com.chamati.repository.consulta;

import br.com.chamati.enums.Perfil;
import java.time.Instant;

/** Listagem de usuários sem senha_hash. */
public record UsuarioResumo(
        Long id,
        String nome,
        String email,
        Perfil perfil,
        String departamento,
        boolean ativo,
        Instant ultimoLoginEm) {
}
