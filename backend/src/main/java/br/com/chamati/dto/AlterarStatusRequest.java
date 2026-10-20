package br.com.chamati.dto;

import br.com.chamati.enums.StatusChamado;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlterarStatusRequest(
        @NotNull StatusChamado status,
        @Size(max = 400) String observacao,
        Long versao) {
}
