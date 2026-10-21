package br.com.chamati.dto;

import br.com.chamati.enums.TipoEventoHistorico;
import java.time.Instant;

public record HistoricoResponse(
        Long id,
        TipoEventoHistorico tipoEvento,
        String campo,
        String valorAnterior,
        String valorNovo,
        String descricao,
        AutorResponse autor,
        Instant criadoEm) {
}
