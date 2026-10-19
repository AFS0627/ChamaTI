package br.com.chamati.repository.consulta;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import java.time.Instant;

/** Linha da listagem: sem descrição/solução (TEXT) e sem carregar entidades. */
public record ChamadoResumo(
        Long id,
        String protocolo,
        String titulo,
        Prioridade prioridade,
        StatusChamado status,
        Long categoriaId,
        String categoriaNome,
        Long solicitanteId,
        String solicitanteNome,
        Long tecnicoId,
        String tecnicoNome,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant slaResolucaoLimite,
        Instant resolvidoEm) {
}
