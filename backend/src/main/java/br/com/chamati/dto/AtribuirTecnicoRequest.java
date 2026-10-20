package br.com.chamati.dto;

import jakarta.validation.constraints.NotNull;

/** versao (opcional): se informada e diferente da atual, a operação é recusada com 409. */
public record AtribuirTecnicoRequest(@NotNull Long tecnicoId, Long versao) {
}
