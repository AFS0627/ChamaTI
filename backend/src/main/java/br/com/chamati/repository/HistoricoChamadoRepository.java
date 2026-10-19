package br.com.chamati.repository;

import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoChamadoRepository extends JpaRepository<HistoricoChamado, Long> {

    @EntityGraph(attributePaths = "autor")
    Page<HistoricoChamado> findByChamadoId(Long chamadoId, Pageable pageable);

    /** Para o SOLICITANTE: passe TipoEventoHistorico.NOTA_INTERNA para ocultar as notas internas. */
    @EntityGraph(attributePaths = "autor")
    Page<HistoricoChamado> findByChamadoIdAndTipoEventoNot(Long chamadoId, TipoEventoHistorico excluido,
                                                           Pageable pageable);
}
