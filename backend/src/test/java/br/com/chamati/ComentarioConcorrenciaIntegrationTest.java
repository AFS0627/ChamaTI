package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.dto.ChamadoCriarRequest;
import br.com.chamati.dto.ComentarioCriarRequest;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ChamadoService;
import br.com.chamati.service.ComentarioService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Sem @Transactional: cada thread usa a própria transação. Os dados criados são apagados ao final. */
class ComentarioConcorrenciaIntegrationTest extends IntegrationTestBase {

    static final UsuarioPrincipal ADMIN = new UsuarioPrincipal(1L, "admin@chamati.local", Perfil.ADMIN);
    static final UsuarioPrincipal ANA = new UsuarioPrincipal(4L, "ana.souza@chamati.local", Perfil.SOLICITANTE);

    @Autowired ChamadoService abertura;
    @Autowired ComentarioService comentarios;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        jdbc.update("delete from historico_chamados where chamado_id > 12");
        jdbc.update("delete from comentarios where chamado_id > 12");
        jdbc.update("delete from chamados where id > 12");
        jdbc.update("delete from sequencias_protocolo where ano <> 2026");
        jdbc.update("update sequencias_protocolo set ultimo_numero = 12 where ano = 2026");
    }

    @Test
    void primeiraRespostaEhRegistradaUmaUnicaVezMesmoComRespostasSimultaneas() throws Exception {
        long id = abertura.abrir(new ChamadoCriarRequest("Concorrência de comentários", "Teste", 1L, null, null), ANA).id();

        int tentativas = 8;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();
        for (int i = 0; i < tentativas; i++) {
            int n = i;
            Callable<Object> tarefa = () -> {
                largada.await();
                try {
                    comentarios.criar(id, new ComentarioCriarRequest(TipoComentario.PUBLICO, "Resposta " + n), ADMIN);
                    return "ok";
                } catch (RuntimeException e) {
                    return e;
                }
            };
            futuros.add(pool.submit(tarefa));
        }
        largada.countDown();

        int sucessos = 0;
        for (Future<Object> f : futuros) {
            Object resultado = f.get(60, TimeUnit.SECONDS);
            if ("ok".equals(resultado)) {
                sucessos++;
            } else {
                assertThat(resultado).isInstanceOf(ObjectOptimisticLockingFailureException.class);   // quem perdeu a corrida
            }
        }
        pool.shutdown();

        assertThat(sucessos).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from comentarios where chamado_id = ?", Integer.class, id))
                .isEqualTo(sucessos);
        assertThat(jdbc.queryForObject(
                "select count(*) from historico_chamados where chamado_id = ? and campo = 'primeira_resposta_em'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select primeira_resposta_em is not null from chamados where id = ?",
                Boolean.class, id)).isTrue();
    }
}
