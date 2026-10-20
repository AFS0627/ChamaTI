package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.dto.ChamadoCriarRequest;
import br.com.chamati.enums.Perfil;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ChamadoService;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sem @Transactional de propósito: cada thread usa a sua própria transação, como em produção.
 * Os dados criados são apagados ao final para não afetar os demais testes (demonstração: 12 chamados).
 */
class ChamadoConcorrenciaIntegrationTest extends IntegrationTestBase {

    @Autowired ChamadoService service;
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
    void geraProtocolosUnicosESemLacunasEmAcessoSimultaneo() throws Exception {
        int ano = Year.now(ZoneOffset.UTC).getValue();
        int inicio = jdbc.queryForObject(
                "select coalesce(max(ultimo_numero), 0) from sequencias_protocolo where ano = ?", Integer.class, ano);
        int tarefas = 24;
        UsuarioPrincipal ana = new UsuarioPrincipal(4L, "ana.souza@chamati.local", Perfil.SOLICITANTE);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<String>> futuros = new ArrayList<>();
        for (int i = 0; i < tarefas; i++) {
            int n = i;
            futuros.add(pool.submit(() -> {
                largada.await();
                return service.abrir(new ChamadoCriarRequest("Concorrência " + n, "Teste simultâneo", 1L, null, null), ana)
                        .protocolo();
            }));
        }
        largada.countDown();

        Set<String> protocolos = new TreeSet<>();
        for (Future<String> f : futuros) {
            protocolos.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(protocolos).hasSize(tarefas);
        Set<Integer> numeros = protocolos.stream()
                .map(p -> Integer.parseInt(p.substring(p.lastIndexOf('-') + 1)))
                .collect(Collectors.toSet());
        assertThat(numeros).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(inicio + 1, inicio + tarefas).boxed().toList());
        assertThat(jdbc.queryForObject("select count(*) from chamados where id > 12", Integer.class)).isEqualTo(tarefas);
        assertThat(jdbc.queryForObject("select count(*) from historico_chamados where chamado_id > 12", Integer.class))
                .isEqualTo(tarefas);
    }
}
