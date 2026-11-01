package br.com.chamati.controller;

import br.com.chamati.dto.DashboardResponse;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Resumo para o painel inicial. Qualquer usuário autenticado; os números respeitam a visibilidade do perfil.")
@RestController
@RequestMapping("/api/dashboard")
@Validated
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @Operation(summary = "Resumo do painel",
            description = "Totais, chamados por status, em aberto por prioridade e por categoria, fila sem técnico, atrasos de SLA e movimento do período. "
                    + "ADMIN vê todos os chamados; SOLICITANTE só os que abriu; TECNICO os atribuídos a ele, a fila sem técnico e os que abriu. "
                    + "Em atraso considera somente chamados em aberto; o filtro atrasados=true da listagem também inclui resolvidos fora do prazo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo calculado no momento da chamada.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardResponse.class)))
    })
    @GetMapping("/resumo")
    public DashboardResponse resumo(
            @Parameter(description = "Janela, em dias, usada em periodo (abertos e resolvidos). De 1 a 365.")
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.resumo(dias, atual);
    }
}
