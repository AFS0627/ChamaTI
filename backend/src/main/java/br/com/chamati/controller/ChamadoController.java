package br.com.chamati.controller;

import br.com.chamati.dto.ChamadoCriarRequest;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.repository.consulta.ChamadoResumo;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ChamadoConsultaService;
import br.com.chamati.service.ChamadoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
@RequestMapping("/api/chamados")
@Validated
public class ChamadoController {

    private final ChamadoService service;
    private final ChamadoConsultaService consulta;

    public ChamadoController(ChamadoService service, ChamadoConsultaService consulta) {
        this.service = service;
        this.consulta = consulta;
    }

    /** O solicitante é sempre o usuário autenticado. */
    @PostMapping
    public ResponseEntity<ChamadoResponse> abrir(@Valid @RequestBody ChamadoCriarRequest req,
                                                 @AuthenticationPrincipal UsuarioPrincipal atual) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.abrir(req, atual));
    }

    /** Período: criadoDe inclusivo e criadoAte exclusivo, em ISO-8601 com Z (ex.: 2026-10-19T00:00:00Z). */
    @GetMapping
    public PaginaResponse<ChamadoResumo> listar(
            @RequestParam(required = false) @Size(max = 20) String protocolo,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) StatusChamado status,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long tecnicoId,
            @RequestParam(defaultValue = "false") boolean semTecnico,
            @RequestParam(defaultValue = "false") boolean atrasados,
            @RequestParam(required = false) Instant criadoDe,
            @RequestParam(required = false) Instant criadoAte,
            @RequestParam(defaultValue = "criadoEm")
            @Pattern(regexp = "criadoEm|atualizadoEm|protocolo|titulo") String ordenarPor,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "(?i)asc|desc") String direcao,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @AuthenticationPrincipal UsuarioPrincipal atual) {
        ChamadoFiltro filtro = new ChamadoFiltro(q, status, prioridade, categoriaId, null, tecnicoId,
                semTecnico, atrasados, criadoDe, criadoAte, protocolo, null);
        return consulta.listar(filtro, ordenarPor, direcao, pagina, tamanho, atual);
    }

    @GetMapping("/{id}")
    public ChamadoResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal atual) {
        return consulta.buscar(id, atual);
    }
}
