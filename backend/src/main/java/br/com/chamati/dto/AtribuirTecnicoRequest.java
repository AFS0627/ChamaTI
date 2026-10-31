package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** versao (opcional): se informada e diferente da atual, a operação é recusada com 409. */
public record AtribuirTecnicoRequest(@NotNull @Schema(description = "Usuário ativo com perfil TECNICO.", example = "2") Long tecnicoId, @Schema(description = "Opcional. Versão do chamado lida pelo cliente; se diferente da atual, a operação responde 409.") Long versao) {
}
