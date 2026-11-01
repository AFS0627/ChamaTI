package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardTotais(
        @Schema(description = "Todos os chamados visíveis ao usuário, em qualquer status.") long total,
        @Schema(description = "Chamados que não estão RESOLVIDO nem FECHADO.") long emAberto,
        @Schema(description = "Chamados em aberto sem técnico responsável (fila).") long semTecnico,
        @Schema(description = "Chamados em aberto atribuídos ao próprio usuário.") long atribuidosAMim,
        @Schema(description = "Chamados em aberto com o limite de resolução do SLA já vencido.") long emAtrasoResolucao,
        @Schema(description = "Chamados em aberto, ainda sem primeira resposta, com o limite de primeira resposta já vencido.") long emAtrasoPrimeiraResposta) {
}
