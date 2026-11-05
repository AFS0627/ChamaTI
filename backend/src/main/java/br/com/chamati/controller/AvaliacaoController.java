package br.com.chamati.controller;

import br.com.chamati.dto.AvaliacaoCriarRequest;
import br.com.chamati.dto.AvaliacaoResponse;
import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.MediaTecnicoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.AvaliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Avaliações", description = "Avaliação do atendimento (1 a 5 estrelas e comentário opcional). O solicitante avalia; a consulta é restrita a ADMIN.")
@RestController
@RequestMapping("/api")
@Validated
public class AvaliacaoController {

    private final AvaliacaoService service;

    public AvaliacaoController(AvaliacaoService service) {
        this.service = service;
    }

    @PostMapping("/chamados/{chamadoId}/avaliacao")
    @Operation(summary = "Avalia o atendimento do chamado",
            description = "Somente o solicitante do chamado, uma única vez, com o chamado RESOLVIDO ou FECHADO. A avaliação fica vinculada ao chamado e ao técnico responsável. Ordem das verificações: 404 (chamado fora da visibilidade) → 403 (não é o solicitante) → 409 (já avaliado) → 422 (chamado não resolvido).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Avaliação registrada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AvaliacaoResponse.class))),
            @ApiResponse(responseCode = "403", description = "Quem enxerga o chamado, mas não é o solicitante.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "O chamado já foi avaliado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Chamado ainda não resolvido ou sem técnico responsável.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<AvaliacaoResponse> avaliar(
            @Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
            @Valid @RequestBody AvaliacaoCriarRequest req,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.avaliar(chamadoId, req, atual));
    }

    @GetMapping("/avaliacoes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista as avaliações",
            description = "Somente ADMIN. Paginado, da mais recente para a mais antiga. Filtro opcional por técnico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de avaliações.")
    })
    public PaginaResponse<AvaliacaoResponse> listar(
            @Parameter(description = "Filtra pelo técnico responsável.") @RequestParam(required = false) Long tecnicoId,
            @Parameter(description = "Número da página, começando em 0.") @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página (1 a 100).") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(tecnicoId, pagina, tamanho);
    }

    @GetMapping("/avaliacoes/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Média e quantidade de avaliações por técnico",
            description = "Somente ADMIN. Lista os técnicos que já receberam avaliações, em ordem de nome, com a média das notas (duas casas decimais) e a quantidade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Média e quantidade por técnico.", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MediaTecnicoResponse.class))))
    })
    public List<MediaTecnicoResponse> mediasPorTecnico() {
        return service.mediasPorTecnico();
    }
}
