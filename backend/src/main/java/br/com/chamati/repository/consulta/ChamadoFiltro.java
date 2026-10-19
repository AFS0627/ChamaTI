package br.com.chamati.repository.consulta;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import java.time.Instant;

/** Filtros opcionais da listagem de chamados; campos nulos/falsos são ignorados. */
public record ChamadoFiltro(
        String termo,                 // protocolo exato ou trecho do título
        StatusChamado status,
        Prioridade prioridade,
        Long categoriaId,
        Long solicitanteId,           // o service força este filtro para o perfil SOLICITANTE
        Long tecnicoId,
        boolean somenteSemTecnico,
        boolean somenteAtrasados,     // SLA de resolução estourado
        Instant criadoDe,             // inclusivo
        Instant criadoAte) {          // exclusivo

    public static ChamadoFiltro vazio() {
        return new ChamadoFiltro(null, null, null, null, null, null, false, false, null, null);
    }
}
