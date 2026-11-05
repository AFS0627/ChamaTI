package br.com.chamati.repository;

import br.com.chamati.entity.Anexo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnexoRepository extends JpaRepository<Anexo, Long> {

    @EntityGraph(attributePaths = "autor")
    List<Anexo> findByChamadoIdOrderByCriadoEmAscIdAsc(Long chamadoId);

    @EntityGraph(attributePaths = "autor")
    Optional<Anexo> findByIdAndChamadoId(Long id, Long chamadoId);

    long countByChamadoId(Long chamadoId);
}
