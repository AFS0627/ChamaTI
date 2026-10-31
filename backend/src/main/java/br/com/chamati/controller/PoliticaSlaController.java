package br.com.chamati.controller;

import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.PoliticaSlaRequest;
import br.com.chamati.dto.PoliticaSlaResponse;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.service.PoliticaSlaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Leitura: qualquer usuário autenticado. Alteração: ADMIN. */
@Tag(name = "Políticas de SLA", description = "Prazos de primeira resposta e de resolução por prioridade, em minutos corridos. Leitura para autenticados; alteração apenas ADMIN.")
@RestController
@RequestMapping("/api/politicas-sla")
public class PoliticaSlaController {

    private final PoliticaSlaService service;

    public PoliticaSlaController(PoliticaSlaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as políticas de SLA",
            description = "Da prioridade mais urgente (CRITICA) para a menos urgente (BAIXA).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Políticas de SLA.")
    })
    public List<PoliticaSlaResponse> listar() {
        return service.listar();
    }

    @PutMapping("/{prioridade}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Define os prazos de uma prioridade",
            description = "Cria a política se ainda não existir e a reativa se estiver inativa. Cada prazo vai de 1 a 525600 minutos e a resolução não pode ser menor que a primeira resposta. Vale para os chamados abertos a partir de agora e para recálculos futuros; os limites já gravados em chamados existentes não mudam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Política atualizada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PoliticaSlaResponse.class))),
            @ApiResponse(responseCode = "422", description = "O prazo de resolução é menor que o de primeira resposta.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public PoliticaSlaResponse atualizar(@Parameter(description = "Prioridade da política (BAIXA, MEDIA, ALTA ou CRITICA).") @PathVariable Prioridade prioridade,
                                         @Valid @RequestBody PoliticaSlaRequest req) {
        return service.atualizar(prioridade, req);
    }
}
