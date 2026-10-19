package br.com.chamati.repository;

import br.com.chamati.entity.Categoria;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    boolean existsByNome(String nome);

    boolean existsByNomeAndIdNot(String nome, Long id);

    /** Para combos de abertura de chamado. */
    List<Categoria> findByAtivoTrueOrderByNomeAsc();

    @Query(value = """
            select c from Categoria c
            where (:ativo is null or c.ativo = :ativo)
              and (:termo is null or c.nome like concat('%', :termo, '%'))
            """,
            countQuery = """
            select count(c) from Categoria c
            where (:ativo is null or c.ativo = :ativo)
              and (:termo is null or c.nome like concat('%', :termo, '%'))
            """)
    Page<Categoria> buscar(@Param("ativo") Boolean ativo, @Param("termo") String termo, Pageable pageable);
}
