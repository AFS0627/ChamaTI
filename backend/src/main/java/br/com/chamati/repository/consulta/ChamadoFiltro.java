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
        Instant criadoAte,            // exclusivo
        String protocolo,             // igualdade exata
        Long escopoTecnicoId) {       // restringe a: atribuídos ao técnico, sem técnico ou abertos por ele

    public ChamadoFiltro(String termo, StatusChamado status, Prioridade prioridade, Long categoriaId,
                         Long solicitanteId, Long tecnicoId, boolean somenteSemTecnico,
                         boolean somenteAtrasados, Instant criadoDe, Instant criadoAte) {
        this(termo, status, prioridade, categoriaId, solicitanteId, tecnicoId, somenteSemTecnico,
                somenteAtrasados, criadoDe, criadoAte, null, null);
    }

    public static ChamadoFiltro vazio() {
        return new ChamadoFiltro(null, null, null, null, null, null, false, false, null, null);
    }

    public ChamadoFiltro comSolicitante(Long id) {
        return new ChamadoFiltro(termo, status, prioridade, categoriaId, id, tecnicoId, somenteSemTecnico,
                somenteAtrasados, criadoDe, criadoAte, protocolo, escopoTecnicoId);
    }

    public ChamadoFiltro comEscopoTecnico(Long id) {
        return new ChamadoFiltro(termo, status, prioridade, categoriaId, solicitanteId, tecnicoId,
                somenteSemTecnico, somenteAtrasados, criadoDe, criadoAte, protocolo, id);
    }
}
