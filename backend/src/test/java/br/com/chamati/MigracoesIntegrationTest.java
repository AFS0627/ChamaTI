package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Aplica V1, V2 e V100 e confirma que o Hibernate valida o esquema (ddl-auto=validate),
 * o que também valida o mapeamento de todas as entidades.
 */
class MigracoesIntegrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaMigracoesEDadosDeDemonstracao() {
        Integer migracoes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        Integer chamados = jdbc.queryForObject("SELECT COUNT(*) FROM chamados", Integer.class);
        Integer categorias = jdbc.queryForObject("SELECT COUNT(*) FROM categorias", Integer.class);

        assertThat(migracoes).isEqualTo(3);
        assertThat(categorias).isEqualTo(8);
        assertThat(chamados).isEqualTo(12);
    }
}
