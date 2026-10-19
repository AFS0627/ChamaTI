package br.com.chamati.repository;

import br.com.chamati.entity.Subcategoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {

    List<Subcategoria> findByCategoriaIdOrderByNomeAsc(Long categoriaId);

    List<Subcategoria> findByCategoriaIdAndAtivoTrueOrderByNomeAsc(Long categoriaId);

    /** Confirma que a subcategoria pertence à categoria informada (validação antes de gravar o chamado). */
    Optional<Subcategoria> findByIdAndCategoriaId(Long id, Long categoriaId);

    boolean existsByCategoriaIdAndNome(Long categoriaId, String nome);

    boolean existsByCategoriaIdAndNomeAndIdNot(Long categoriaId, String nome, Long id);
}
