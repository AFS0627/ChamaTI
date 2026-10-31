package br.com.chamati.controller;

import br.com.chamati.dto.ComentarioCriarRequest;
import br.com.chamati.dto.ComentarioResponse;
import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.HistoricoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ComentarioService;
import br.com.chamati.service.HistoricoConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Comentários e histórico", description = "Comentários e linha do tempo de um chamado. Solicitantes não veem notas internas nem os eventos NOTA_INTERNA do histórico.")
@RestController
@RequestMapping("/api/chamados/{chamadoId}")
@Validated
public class ChamadoAcompanhamentoController {

    private final ComentarioService comentarios;
    private final HistoricoConsultaService historico;

    public ChamadoAcompanhamentoController(ComentarioService comentarios, HistoricoConsultaService historico) {
        this.comentarios = comentarios;
        this.historico = historico;
    }

    @PostMapping("/comentarios")
    @Operation(summary = "Adiciona um comentário ou nota interna",
            description = "O tipo (PUBLICO ou NOTA_INTERNA) é obrigatório. Solicitantes só criam comentários públicos. Chamados FECHADO não recebem comentários. O primeiro comentário público de um TECNICO ou ADMIN que não seja o solicitante do chamado registra a primeira resposta (SLA). Chamado fora da visibilidade do usuário responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comentário criado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ComentarioResponse.class))),
            @ApiResponse(responseCode = "403", description = "Solicitante tentando criar nota interna.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Outro comentário registrou a primeira resposta ao mesmo tempo; tente novamente.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Chamado fechado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<ComentarioResponse> comentar(@Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
                                                       @Valid @RequestBody ComentarioCriarRequest req,
                                                       @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comentarios.criar(chamadoId, req, atual));
    }

    /** Ordem cronológica por padrão. Solicitantes recebem apenas comentários públicos. */
    @GetMapping("/comentarios")
    @Operation(summary = "Lista os comentários do chamado",
            description = "Ordem cronológica por padrão. Solicitantes recebem somente comentários públicos e não podem filtrar por NOTA_INTERNA. Chamado fora da visibilidade do usuário responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de comentários."),
            @ApiResponse(responseCode = "403", description = "Solicitante filtrando por NOTA_INTERNA.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<ComentarioResponse> listarComentarios(
            @Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
            @Parameter(description = "Filtra pelo tipo do comentário.") @RequestParam(required = false) TipoComentario tipo,
            @Parameter(description = "Direção da ordenação cronológica: asc ou desc.") @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String direcao,
            @Parameter(description = "Número da página, começando em 0.") @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página (1 a 100).") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return comentarios.listar(chamadoId, tipo, direcao, pagina, tamanho, atual);
    }

    @GetMapping("/historico")
    @Operation(summary = "Lista o histórico do chamado",
            description = "Eventos imutáveis (criação, atribuição, status, resolução, encerramento, reabertura, comentários e notas). Ordem cronológica por padrão. Solicitantes não recebem eventos NOTA_INTERNA. Chamado fora da visibilidade do usuário responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de eventos."),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<HistoricoResponse> listarHistorico(
            @Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
            @Parameter(description = "Direção da ordenação cronológica: asc ou desc.") @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String direcao,
            @Parameter(description = "Número da página, começando em 0.") @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página (1 a 100).") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return historico.listar(chamadoId, direcao, pagina, tamanho, atual);
    }
}
