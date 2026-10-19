package br.com.chamati.repository;

import br.com.chamati.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @EntityGraph(attributePaths = "usuario")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Logout geral / troca de senha: revoga todos os tokens ainda válidos do usuário. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revogadoEm = :agora where t.usuario.id = :usuarioId and t.revogadoEm is null")
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    /** Limpeza periódica de tokens expirados. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiraEm < :limite")
    int apagarExpiradosAte(@Param("limite") Instant limite);
}
