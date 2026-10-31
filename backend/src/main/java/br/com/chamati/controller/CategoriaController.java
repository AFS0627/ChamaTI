package br.com.chamati.controller;

import br.com.chamati.dto.CategoriaRequest;
import br.com.chamati.dto.CategoriaResponse;
import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.enums.Perfil;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.CategoriaService;
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
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leitura: qualquer usuário autenticado (somente ativas). Administração: ADMIN. */
@Tag(name = "Categorias", description = "Catálogo de categorias de chamado. Leitura para qualquer usuário autenticado (somente ativas); administração apenas ADMIN.")
@RestController
@RequestMapping("/api/categorias")
@Validated
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista categorias (ADMIN)",
            description = "Paginado, ordenado por nome, inclui inativas. Para o catálogo de abertura use /api/categorias/ativas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de categorias.")
    })
    public PaginaResponse<CategoriaResponse> listar(
            @Parameter(description = "Filtra pela situação.") @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "Texto buscado no nome.") @RequestParam(required = false) @Size(max = 100) String q,
            @Parameter(description = "Número da página, começando em 0.") @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página (1 a 100).") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(ativo, q, pagina, tamanho);
    }

    @GetMapping("/ativas")
    @Operation(summary = "Lista categorias ativas",
            description = "Todas as categorias ativas, ordenadas por nome. Qualquer usuário autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categorias ativas.")
    })
    public List<CategoriaResponse> listarAtivas() {
        return service.listarAtivas();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma categoria",
            description = "ADMIN vê qualquer categoria; os demais perfis recebem 404 para categorias inativas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada (ou inativa, para não administradores).", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public CategoriaResponse buscar(@Parameter(description = "Identificador da categoria.") @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.buscar(id, ehAdmin(atual));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria uma categoria",
            description = "O nome deve ser único. A categoria nasce ativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada. O cabeçalho Location aponta para o novo recurso.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe uma categoria com este nome.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest req) {
        CategoriaResponse criada = service.criar(req);
        return ResponseEntity.created(URI.create("/api/categorias/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza nome e descrição",
            description = "O nome deve continuar único.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe uma categoria com este nome.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public CategoriaResponse atualizar(@Parameter(description = "Identificador da categoria.") @PathVariable Long id, @Valid @RequestBody CategoriaRequest req) {
        return service.atualizar(id, req);
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ativa uma categoria",
            description = "Idempotente. Não reativa as subcategorias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria ativa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public CategoriaResponse ativar(@Parameter(description = "Identificador da categoria.") @PathVariable Long id) {
        return service.ativar(id);
    }

    @PatchMapping("/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desativa uma categoria",
            description = "As subcategorias permanecem como estão, mas ficam indisponíveis enquanto a categoria estiver inativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria inativa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public CategoriaResponse desativar(@Parameter(description = "Identificador da categoria.") @PathVariable Long id) {
        return service.desativar(id);
    }

    static boolean ehAdmin(UsuarioPrincipal atual) {
        return atual != null && atual.perfil() == Perfil.ADMIN;
    }
}
