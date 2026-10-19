package br.com.chamati.repository;

import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.consulta.UsuarioResumo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** E-mail comparado pela collation do banco (sem diferenciar maiúsculas/acentos). */
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Valida atribuição: usuário ativo com o perfil exigido (TECNICO). */
    Optional<Usuario> findByIdAndPerfilAndAtivoTrue(Long id, Perfil perfil);

    List<Usuario> findByPerfilAndAtivoTrueOrderByNomeAsc(Perfil perfil);

    String FILTRO = """
            from Usuario u
            where (:perfil is null or u.perfil = :perfil)
              and (:ativo is null or u.ativo = :ativo)
              and (:termo is null or u.nome like concat('%', :termo, '%')
                                  or u.email like concat('%', :termo, '%'))
            """;

    @Query(value = "select new br.com.chamati.repository.consulta.UsuarioResumo("
            + "u.id, u.nome, u.email, u.perfil, u.departamento, u.ativo, u.ultimoLoginEm) " + FILTRO,
            countQuery = "select count(u) " + FILTRO)
    Page<UsuarioResumo> buscar(@Param("perfil") Perfil perfil,
                               @Param("ativo") Boolean ativo,
                               @Param("termo") String termo,
                               Pageable pageable);
}
