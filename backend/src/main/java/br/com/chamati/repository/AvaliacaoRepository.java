package br.com.chamati.repository;

import br.com.chamati.entity.Avaliacao;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    interface MediaTecnico {
        Long getTecnicoId();

        String getTecnicoNome();

        Double getMedia();

        Long getQuantidade();
    }

    boolean existsByChamadoId(Long chamadoId);

    @EntityGraph(attributePaths = {"chamado", "chamado.solicitante", "tecnico"})
    Page<Avaliacao> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"chamado", "chamado.solicitante", "tecnico"})
    Page<Avaliacao> findByTecnicoId(Long tecnicoId, Pageable pageable);

    @Query("select a.tecnico.id as tecnicoId, a.tecnico.nome as tecnicoNome, avg(a.nota) as media, count(a) as quantidade "
            + "from Avaliacao a group by a.tecnico.id, a.tecnico.nome order by a.tecnico.nome, a.tecnico.id")
    List<MediaTecnico> mediasPorTecnico();
}
