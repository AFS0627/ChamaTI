package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FecharChamadoRequest(@Schema(description = "Opcional. Versão do chamado lida pelo cliente; se diferente da atual, a operação responde 409.") Long versao) {
}
