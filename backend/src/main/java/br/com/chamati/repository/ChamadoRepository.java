package br.com.chamati.repository;

import br.com.chamati.entity.Chamado;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.repository.consulta.ChamadoResumo;
import br.com.chamati.repository.consulta.ContagemStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

    /** Detalhe: carrega os relacionamentos to-one em uma única consulta. */
    @EntityGraph(attributePaths = {"categoria", "subcategoria", "solicitante", "tecnico"})
    Optional<Chamado> findDetalheById(Long id);

    @EntityGraph(attributePaths = {"categoria", "subcategoria", "solicitante", "tecnico"})
    Optional<Chamado> findByProtocolo(String protocolo);

    String ORIGEM_E_FILTROS = """
            from Chamado c
            join c.categoria cat
            join c.solicitante sol
            left join c.tecnico tec
            where (:solicitanteId is null or sol.id = :solicitanteId)
              and (:tecnicoId is null or tec.id = :tecnicoId)
              and (:semTecnico = false or c.tecnico is null)
              and (:status is null or c.status = :status)
              and (:prioridade is null or c.prioridade = :prioridade)
              and (:categoriaId is null or cat.id = :categoriaId)
              and (:protocolo is null or c.protocolo = :protocolo)
              and (:escopoTecnicoId is null or tec.id = :escopoTecnicoId or tec.id is null
                     or sol.id = :escopoTecnicoId)
              and (:criadoDe is null or c.criadoEm >= :criadoDe)
              and (:criadoAte is null or c.criadoEm < :criadoAte)
              and (:termo is null or c.protocolo = :termo or c.titulo like concat('%', :termo, '%'))
              and (:atrasados = false or (c.slaResolucaoLimite is not null and (
                     (c.resolvidoEm is null and c.slaResolucaoLimite < :agora)
                     or c.resolvidoEm > c.slaResolucaoLimite)))
            """;

    @Query(value = "select new br.com.chamati.repository.consulta.ChamadoResumo("
            + "c.id, c.protocolo, c.titulo, c.prioridade, c.status, cat.id, cat.nome, "
            + "sol.id, sol.nome, tec.id, tec.nome, c.criadoEm, c.atualizadoEm, "
            + "c.slaResolucaoLimite, c.resolvidoEm) " + ORIGEM_E_FILTROS,
            countQuery = "select count(c) " + ORIGEM_E_FILTROS)
    Page<ChamadoResumo> buscarResumos(@Param("solicitanteId") Long solicitanteId,
                                      @Param("tecnicoId") Long tecnicoId,
                                      @Param("semTecnico") boolean semTecnico,
                                      @Param("status") br.com.chamati.enums.StatusChamado status,
                                      @Param("prioridade") br.com.chamati.enums.Prioridade prioridade,
                                      @Param("categoriaId") Long categoriaId,
                                      @Param("criadoDe") Instant criadoDe,
                                      @Param("criadoAte") Instant criadoAte,
                                      @Param("termo") String termo,
                                      @Param("atrasados") boolean atrasados,
                                      @Param("protocolo") String protocolo,
                                      @Param("escopoTecnicoId") Long escopoTecnicoId,
                                      @Param("agora") Instant agora,
                                      Pageable pageable);

    /**
     * Listagem paginada com filtros. Ordenação segura via Pageable: use propriedades de
     * {@code c} (ex.: criadoEm, prioridade, status, protocolo) — o service deve limitar as permitidas.
     */
    default Page<ChamadoResumo> buscar(ChamadoFiltro f, Pageable pageable) {
        String termo = f.termo() == null || f.termo().isBlank() ? null : f.termo().trim();
        String protocolo = f.protocolo() == null || f.protocolo().isBlank() ? null : f.protocolo().trim();
        return buscarResumos(f.solicitanteId(), f.tecnicoId(), f.somenteSemTecnico(), f.status(),
                f.prioridade(), f.categoriaId(), f.criadoDe(), f.criadoAte(), termo,
                f.somenteAtrasados(), protocolo, f.escopoTecnicoId(), Instant.now(), pageable);
    }

    /** Totais por status para painéis; ambos os filtros são opcionais. */
    @Query("""
            select new br.com.chamati.repository.consulta.ContagemStatus(c.status, count(c))
            from Chamado c
            where (:solicitanteId is null or c.solicitante.id = :solicitanteId)
              and (:tecnicoId is null or c.tecnico.id = :tecnicoId)
            group by c.status
            """)
    List<ContagemStatus> contarPorStatus(@Param("solicitanteId") Long solicitanteId,
                                         @Param("tecnicoId") Long tecnicoId);
}
