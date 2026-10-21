package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.dto.ComentarioCriarRequest;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ComentarioService;
import br.com.chamati.service.HistoricoConsultaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Autores vêm junto da consulta (sem N+1): listar = visibilidade + página + contagem. */
@Transactional
class ComentarioDesempenhoIntegrationTest extends IntegrationTestBase {

    static final UsuarioPrincipal ADMIN = new UsuarioPrincipal(1L, "admin@chamati.local", Perfil.ADMIN);
    static final UsuarioPrincipal CARLOS = new UsuarioPrincipal(2L, "carlos.mendes@chamati.local", Perfil.TECNICO);
    static final UsuarioPrincipal JULIANA = new UsuarioPrincipal(6L, "juliana.rocha@chamati.local", Perfil.SOLICITANTE);

    @Autowired ComentarioService comentarios;
    @Autowired HistoricoConsultaService historico;
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
    void listarComentariosUsaNoMaximoTresComandos() {
        var pagina = comentarios.listar(3L, null, "asc", 0, 2, ADMIN);

        assertThat(pagina.conteudo()).hasSize(2);
        assertThat(pagina.conteudo().get(0).autor().nome()).isNotBlank();
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void listarComentariosComoSolicitanteUsaNoMaximoTresComandos() {
        var pagina = comentarios.listar(3L, null, "asc", 0, 1, JULIANA);

        assertThat(pagina.totalElementos()).isEqualTo(2);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void listarHistoricoUsaNoMaximoTresComandos() {
        var pagina = historico.listar(3L, "asc", 0, 3, ADMIN);

        assertThat(pagina.conteudo()).hasSize(3);
        assertThat(pagina.conteudo().get(0).autor().nome()).isNotBlank();
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void comentarSemPrimeiraRespostaUsaNoMaximoCincoComandos() {
        comentarios.criar(3L, new ComentarioCriarRequest(TipoComentario.PUBLICO, "Atualização"), CARLOS);

        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(5);   // chamado, usuário, comentário, histórico
    }

    @Test
    void comentarComPrimeiraRespostaUsaNoMaximoSeisComandos() {
        comentarios.criar(9L, new ComentarioCriarRequest(TipoComentario.PUBLICO, "Recebido"), ADMIN);

        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(6);   // + atualização do chamado
    }
}
