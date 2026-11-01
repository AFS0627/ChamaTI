package br.com.chamati.service;

import br.com.chamati.dto.DashboardCategoria;
import br.com.chamati.dto.DashboardPeriodo;
import br.com.chamati.dto.DashboardResponse;
import br.com.chamati.dto.DashboardTotais;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.repository.consulta.ContagemStatusPrioridade;
import br.com.chamati.repository.consulta.ResumoOperacional;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resumo para o painel. A visibilidade é a mesma da listagem de chamados (ChamadoConsultaService.restringir). */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final List<StatusChamado> FINAIS = List.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);
    static final int MAX_CATEGORIAS = 10;

    private final ChamadoRepository chamados;
    private final Clock clock;

    public DashboardService(ChamadoRepository chamados, Clock clock) {
        this.chamados = chamados;
        this.clock = clock;
    }

    public DashboardResponse resumo(int dias, UsuarioPrincipal ator) {
        ChamadoFiltro escopo = ChamadoConsultaService.restringir(ChamadoFiltro.vazio(), ator);
        Long solicitanteId = escopo.solicitanteId();
        Long escopoTecnicoId = escopo.escopoTecnicoId();
        Instant agora = clock.instant();
        Instant desde = agora.minus(Duration.ofDays(dias));

        Map<StatusChamado, Long> porStatus = new EnumMap<>(StatusChamado.class);
        for (StatusChamado s : StatusChamado.values()) {
            porStatus.put(s, 0L);
        }
        Map<Prioridade, Long> emAbertoPorPrioridade = new EnumMap<>(Prioridade.class);
        for (Prioridade p : Prioridade.values()) {
            emAbertoPorPrioridade.put(p, 0L);
        }
        long total = 0;
        long emAberto = 0;
        for (ContagemStatusPrioridade c : chamados.contarPorStatusEPrioridade(solicitanteId, escopoTecnicoId)) {
            total += c.total();
            porStatus.merge(c.status(), c.total(), Long::sum);
            if (!FINAIS.contains(c.status())) {
                emAberto += c.total();
                emAbertoPorPrioridade.merge(c.prioridade(), c.total(), Long::sum);
            }
        }

        ResumoOperacional op = chamados.contarOperacional(solicitanteId, escopoTecnicoId, ator.id(), FINAIS, agora, desde);
        List<DashboardCategoria> categorias = chamados
                .contarEmAbertoPorCategoria(solicitanteId, escopoTecnicoId, FINAIS, PageRequest.of(0, MAX_CATEGORIAS))
                .stream().map(c -> new DashboardCategoria(c.categoriaId(), c.nome(), c.total())).toList();

        return new DashboardResponse(agora, dias,
                new DashboardTotais(total, emAberto, op.semTecnico(), op.atribuidosAMim(), op.emAtrasoResolucao(),
                        op.emAtrasoPrimeiraResposta()),
                porStatus, emAbertoPorPrioridade, categorias,
                new DashboardPeriodo(desde, op.abertosNoPeriodo(), op.resolvidosNoPeriodo()));
    }
}
