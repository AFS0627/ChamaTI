package br.com.chamati.controller;

import br.com.chamati.dto.ComentarioCriarRequest;
import br.com.chamati.dto.ComentarioResponse;
import br.com.chamati.dto.HistoricoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ComentarioService;
import br.com.chamati.service.HistoricoConsultaService;
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
    public ResponseEntity<ComentarioResponse> comentar(@PathVariable Long chamadoId,
                                                       @Valid @RequestBody ComentarioCriarRequest req,
                                                       @AuthenticationPrincipal UsuarioPrincipal atual) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comentarios.criar(chamadoId, req, atual));
    }

    /** Ordem cronológica por padrão. Solicitantes recebem apenas comentários públicos. */
    @GetMapping("/comentarios")
    public PaginaResponse<ComentarioResponse> listarComentarios(
            @PathVariable Long chamadoId,
            @RequestParam(required = false) TipoComentario tipo,
            @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String direcao,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @AuthenticationPrincipal UsuarioPrincipal atual) {
        return comentarios.listar(chamadoId, tipo, direcao, pagina, tamanho, atual);
    }

    @GetMapping("/historico")
    public PaginaResponse<HistoricoResponse> listarHistorico(
            @PathVariable Long chamadoId,
            @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String direcao,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @AuthenticationPrincipal UsuarioPrincipal atual) {
        return historico.listar(chamadoId, direcao, pagina, tamanho, atual);
    }
}
