package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.dto.AlterarStatusRequest;
import br.com.chamati.dto.AtribuirTecnicoRequest;
import br.com.chamati.dto.ReabrirChamadoRequest;
import br.com.chamati.dto.ResolverChamadoRequest;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ChamadoGestaoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Cada operação carrega o chamado com os relacionamentos em uma consulta e grava com poucos comandos. */
@Transactional
class ChamadoGestaoDesempenhoIntegrationTest extends IntegrationTestBase {

    static final UsuarioPrincipal ADMIN = new UsuarioPrincipal(1L, "admin@chamati.local", Perfil.ADMIN);
    static final UsuarioPrincipal CARLOS = new UsuarioPrincipal(2L, "carlos.mendes@chamati.local", Perfil.TECNICO);
    static final UsuarioPrincipal ANA = new UsuarioPrincipal(4L, "ana.souza@chamati.local", Perfil.SOLICITANTE);

    @Autowired ChamadoGestaoService gestao;
    @Autowired EntityManagerFactory emf;
    @PersistenceContext EntityManager em;

    Statistics estatisticas;

    @BeforeEach
    void zerarContadores() {
        em.flush();
        em.clear();
        estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
    }

    @Test
    void atribuirUsaNoMaximoCincoComandos() {
        gestao.atribuirTecnico(5L, new AtribuirTecnicoRequest(2L, null), ADMIN);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    @Test
    void alterarStatusUsaNoMaximoQuatroComandos() {
        gestao.alterarStatus(3L, new AlterarStatusRequest(StatusChamado.AGUARDANDO_USUARIO, null, null), CARLOS);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void resolverUsaNoMaximoQuatroComandos() {
        gestao.resolver(3L, new ResolverChamadoRequest("Resolvido", null), CARLOS);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void fecharUsaNoMaximoQuatroComandos() {
        gestao.fechar(7L, null, ANA);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void reabrirUsaNoMaximoQuatroComandos() {
        gestao.reabrir(1L, new ReabrirChamadoRequest("Voltou", null), ANA);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }
}
