package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe o contexto contra um MySQL 8.0 descartável (requer Docker), aplica V1, V2 e V100
 * e confirma que o Hibernate valida o esquema (ddl-auto=validate).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class MigracoesIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

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
