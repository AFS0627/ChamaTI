package br.com.chamati.dto;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.SituacaoSla;
import br.com.chamati.enums.StatusChamado;
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
        int totalReaberturas,
        String solucao,
        Instant slaPrimeiraRespostaLimite,
        Instant slaResolucaoLimite,
        SituacaoSla situacaoSlaPrimeiraResposta,
        SituacaoSla situacaoSlaResolucao,
        Long versao) {
}
