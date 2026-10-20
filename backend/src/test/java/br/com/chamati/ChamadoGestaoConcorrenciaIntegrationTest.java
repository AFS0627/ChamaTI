package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.dto.AlterarStatusRequest;
import br.com.chamati.dto.AtribuirTecnicoRequest;
import br.com.chamati.dto.ChamadoCriarRequest;
import br.com.chamati.dto.ResolverChamadoRequest;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ChamadoGestaoService;
import br.com.chamati.service.ChamadoService;
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
class ChamadoGestaoConcorrenciaIntegrationTest extends IntegrationTestBase {

    static final UsuarioPrincipal ADMIN = new UsuarioPrincipal(1L, "admin@chamati.local", Perfil.ADMIN);
    static final UsuarioPrincipal CARLOS = new UsuarioPrincipal(2L, "carlos.mendes@chamati.local", Perfil.TECNICO);
    static final UsuarioPrincipal ANA = new UsuarioPrincipal(4L, "ana.souza@chamati.local", Perfil.SOLICITANTE);

    @Autowired ChamadoService abertura;
    @Autowired ChamadoGestaoService gestao;
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
    void apenasUmaResolucaoVenceQuandoVariasChegamAoMesmoTempo() throws Exception {
        long id = abertura.abrir(new ChamadoCriarRequest("Concorrência de gestão", "Teste", 1L, null, null), ANA).id();
        gestao.atribuirTecnico(id, new AtribuirTecnicoRequest(2L, null), ADMIN);
        gestao.alterarStatus(id, new AlterarStatusRequest(StatusChamado.EM_ATENDIMENTO, null, null), CARLOS);

        int tentativas = 8;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();
        for (int i = 0; i < tentativas; i++) {
            int n = i;
            Callable<Object> tarefa = () -> {
                largada.await();
                try {
                    gestao.resolver(id, new ResolverChamadoRequest("Solução " + n, null), CARLOS);
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
                assertThat(resultado).isInstanceOfAny(RegraNegocioException.class, ObjectOptimisticLockingFailureException.class);
            }
        }
        pool.shutdown();

        assertThat(sucessos).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from chamados where id = ?", String.class, id)).isEqualTo("RESOLVIDO");
        assertThat(jdbc.queryForObject("select count(*) from historico_chamados where chamado_id = ? and tipo_evento = 'RESOLUCAO'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select resolvido_em is not null from chamados where id = ?", Boolean.class, id)).isTrue();
    }
}
