package br.com.chamati.repository;

import br.com.chamati.entity.Comentario;
import br.com.chamati.enums.TipoComentario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {

    /** Todos os comentários (equipe: TECNICO/ADMIN). */
    @EntityGraph(attributePaths = "autor")
    Page<Comentario> findByChamadoId(Long chamadoId, Pageable pageable);

    /** Filtrado por tipo; o SOLICITANTE só pode usar TipoComentario.PUBLICO. */
    @EntityGraph(attributePaths = "autor")
    Page<Comentario> findByChamadoIdAndTipo(Long chamadoId, TipoComentario tipo, Pageable pageable);

    long countByChamadoIdAndTipo(Long chamadoId, TipoComentario tipo);
}
