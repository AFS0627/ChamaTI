package br.com.chamati.dto;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.SituacaoSla;
import br.com.chamati.enums.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ChamadoResponse(
        Long id,
        String protocolo,
        String titulo,
        String descricao,
        Prioridade prioridade,
        StatusChamado status,
        ReferenciaResponse categoria,
        ReferenciaResponse subcategoria,
        ReferenciaResponse solicitante,
        ReferenciaResponse tecnico,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant atribuidoEm,
        Instant primeiraRespostaEm,
        Instant resolvidoEm,
        Instant fechadoEm,
        Instant reabertoEm,
        @Schema(description = "Quantas vezes o chamado foi reaberto.") int totalReaberturas,
        String solucao,
        @Schema(description = "Data limite da primeira resposta (criação + prazo da política).") Instant slaPrimeiraRespostaLimite,
        @Schema(description = "Data limite da resolução (criação + prazo da política).") Instant slaResolucaoLimite,
        @Schema(description = "Calculada no momento da resposta.") SituacaoSla situacaoSlaPrimeiraResposta,
        @Schema(description = "Calculada no momento da resposta.") SituacaoSla situacaoSlaResolucao,
        @Schema(description = "Versão para controle de concorrência; pode ser devolvida no campo versao das operações de gestão.") Long versao) {
}
