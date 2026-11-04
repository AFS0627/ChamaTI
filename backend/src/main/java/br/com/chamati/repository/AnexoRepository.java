package br.com.chamati.repository;

import br.com.chamati.entity.Anexo;
import br.com.chamati.repository.consulta.AnexoResumo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnexoRepository extends JpaRepository<Anexo, Long> {

    /** Não carrega o conteúdo binário. */
    @Query("""
            select new br.com.chamati.repository.consulta.AnexoResumo(
                a.id, a.nomeArquivo, a.tipoConteudo, a.tamanho, u.id, u.nome, u.perfil, a.criadoEm)
            from Anexo a join a.autor u
            where a.chamado.id = :chamadoId
            order by a.criadoEm asc, a.id asc
            """)
    List<AnexoResumo> listarPorChamado(@Param("chamadoId") Long chamadoId);

    Optional<Anexo> findByIdAndChamadoId(Long id, Long chamadoId);

    long countByChamadoId(Long chamadoId);
}
