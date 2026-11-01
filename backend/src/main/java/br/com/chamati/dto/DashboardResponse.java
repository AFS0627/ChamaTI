package br.com.chamati.dto;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Resumo do painel. Todas as contagens respeitam a visibilidade do perfil: ADMIN vê todos os chamados; "
        + "SOLICITANTE só os que abriu; TECNICO os atribuídos a ele, a fila sem técnico e os que abriu.")
public record DashboardResponse(
        @Schema(description = "Momento em que o resumo foi calculado.") Instant geradoEm,
        @Schema(description = "Janela, em dias, usada em periodo.") int periodoDias,
        DashboardTotais totais,
        @Schema(description = "Total de chamados por status, com todos os status presentes (zero quando não há).") Map<StatusChamado, Long> porStatus,
        @Schema(description = "Chamados em aberto por prioridade, com todas as prioridades presentes (zero quando não há).") Map<Prioridade, Long> emAbertoPorPrioridade,
        @Schema(description = "As 10 categorias com mais chamados em aberto, da maior para a menor.") List<DashboardCategoria> emAbertoPorCategoria,
        DashboardPeriodo periodo) {
}
