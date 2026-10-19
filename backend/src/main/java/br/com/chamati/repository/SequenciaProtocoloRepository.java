package br.com.chamati.repository;

import br.com.chamati.entity.SequenciaProtocolo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SequenciaProtocoloRepository extends JpaRepository<SequenciaProtocolo, Short> {

    /**
     * Passo 1 da geração atômica do protocolo. Deve rodar na MESMA transação/conexão
     * do {@link #ultimoIdGerado()}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO sequencias_protocolo (ano, ultimo_numero) VALUES (:ano, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE ultimo_numero = LAST_INSERT_ID(ultimo_numero + 1)
            """, nativeQuery = true)
    int proximoNumero(@Param("ano") int ano);

    /** Passo 2: número gerado no passo 1 (valor por conexão). */
    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long ultimoIdGerado();
}
