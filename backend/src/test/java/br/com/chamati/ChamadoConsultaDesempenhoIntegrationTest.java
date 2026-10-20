package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ChamadoConsultaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Conta os comandos SQL preparados pelo Hibernate para garantir que não há consultas N+1. */
@Transactional
class ChamadoConsultaDesempenhoIntegrationTest extends IntegrationTestBase {

    static final UsuarioPrincipal ADMIN = new UsuarioPrincipal(1L, "admin@chamati.local", Perfil.ADMIN);
    static final UsuarioPrincipal CARLOS = new UsuarioPrincipal(2L, "carlos.mendes@chamati.local", Perfil.TECNICO);
    static final UsuarioPrincipal ANA = new UsuarioPrincipal(4L, "ana.souza@chamati.local", Perfil.SOLICITANTE);

    @Autowired ChamadoConsultaService service;
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
    void listagemDoAdminUsaUmaConsultaMaisAContagem() {
        var pagina = service.listar(ChamadoFiltro.vazio(), null, null, 0, 5, ADMIN);

        assertThat(pagina.conteudo()).hasSize(5);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void listagemDoTecnicoUsaUmaConsultaMaisAContagem() {
        var pagina = service.listar(ChamadoFiltro.vazio(), null, null, 0, 3, CARLOS);

        assertThat(pagina.conteudo()).hasSize(3);
        assertThat(pagina.totalElementos()).isEqualTo(7);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void listagemDoSolicitanteUsaUmaConsultaMaisAContagem() {
        var pagina = service.listar(ChamadoFiltro.vazio(), null, null, 0, 2, ANA);

        assertThat(pagina.conteudo()).hasSize(2);
        assertThat(pagina.totalElementos()).isEqualTo(5);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void tamanhoDaPaginaNaoAlteraAQuantidadeDeConsultas() {
        service.listar(ChamadoFiltro.vazio(), null, null, 0, 2, ADMIN);
        long comDuas = estatisticas.getPrepareStatementCount();
        estatisticas.clear();
        service.listar(ChamadoFiltro.vazio(), null, null, 0, 10, ADMIN);
        long comDez = estatisticas.getPrepareStatementCount();

        assertThat(comDez).isLessThanOrEqualTo(comDuas);
    }

    @Test
    void detalheUsaUmaUnicaConsultaComOsRelacionamentos() {
        var detalhe = service.buscar(3L, ADMIN);

        assertThat(detalhe.categoria().nome()).isNotBlank();
        assertThat(detalhe.subcategoria().nome()).isEqualTo("VPN");
        assertThat(detalhe.solicitante().nome()).isNotBlank();
        assertThat(detalhe.tecnico().nome()).isNotBlank();
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void detalheDeChamadoSemTecnicoNemSubcategoriaTambemUsaUmaConsulta() {
        var detalhe = service.buscar(11L, ADMIN);

        assertThat(detalhe.subcategoria()).isNull();
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }
}
