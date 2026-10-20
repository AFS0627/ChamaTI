package br.com.chamati.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A categoria vem da URL e não pode ser alterada depois de criada. */
public record SubcategoriaRequest(
        @NotBlank @Size(max = 80) String nome,
        @Size(max = 255) String descricao) {
}
