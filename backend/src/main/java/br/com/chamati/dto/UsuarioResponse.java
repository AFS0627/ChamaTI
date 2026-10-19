package br.com.chamati.dto;

import br.com.chamati.enums.Perfil;
import java.time.Instant;

/** Sem senha, hash ou tokens. */
public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Perfil perfil,
        String departamento,
        String telefone,
        boolean ativo,
        Instant ultimoLoginEm,
        Instant criadoEm) {
}
