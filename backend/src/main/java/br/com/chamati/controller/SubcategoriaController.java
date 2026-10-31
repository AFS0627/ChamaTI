package br.com.chamati.controller;

import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.SubcategoriaRequest;
import br.com.chamati.dto.SubcategoriaResponse;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.SubcategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Subcategorias", description = "Subcategorias de uma categoria. Leitura para qualquer usuário autenticado; administração apenas ADMIN.")
@RestController
@RequestMapping("/api/categorias/{categoriaId}/subcategorias")
public class SubcategoriaController {

    private final SubcategoriaService service;

    public SubcategoriaController(SubcategoriaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as subcategorias de uma categoria",
            description = "ADMIN vê todas (inclusive inativas). Os demais perfis veem somente as ativas e recebem 404 se a categoria estiver inativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subcategorias da categoria."),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public List<SubcategoriaResponse> listar(@Parameter(description = "Identificador da categoria.") @PathVariable Long categoriaId,
                                             @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.listar(categoriaId, CategoriaController.ehAdmin(atual));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria uma subcategoria",
            description = "O nome deve ser único dentro da categoria, que precisa estar ativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subcategoria criada. O cabeçalho Location aponta para o novo recurso.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubcategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe uma subcategoria com este nome nesta categoria.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Categoria inativa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<SubcategoriaResponse> criar(@Parameter(description = "Identificador da categoria.") @PathVariable Long categoriaId,
                                                      @Valid @RequestBody SubcategoriaRequest req) {
        SubcategoriaResponse criada = service.criar(categoriaId, req);
        return ResponseEntity.created(URI.create("/api/categorias/" + categoriaId + "/subcategorias/" + criada.id()))
                .body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza nome e descrição",
            description = "A subcategoria nunca muda de categoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subcategoria atualizada.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubcategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Subcategoria não encontrada nesta categoria.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe uma subcategoria com este nome nesta categoria.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public SubcategoriaResponse atualizar(@Parameter(description = "Identificador da categoria.") @PathVariable Long categoriaId, @Parameter(description = "Identificador da subcategoria.") @PathVariable Long id,
                                          @Valid @RequestBody SubcategoriaRequest req) {
        return service.atualizar(categoriaId, id, req);
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ativa uma subcategoria",
            description = "A categoria pai precisa estar ativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subcategoria ativa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubcategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Subcategoria não encontrada nesta categoria.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Categoria inativa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public SubcategoriaResponse ativar(@Parameter(description = "Identificador da categoria.") @PathVariable Long categoriaId, @Parameter(description = "Identificador da subcategoria.") @PathVariable Long id) {
        return service.ativar(categoriaId, id);
    }

    @PatchMapping("/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desativa uma subcategoria",
            description = "Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subcategoria inativa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubcategoriaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Subcategoria não encontrada nesta categoria.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public SubcategoriaResponse desativar(@Parameter(description = "Identificador da categoria.") @PathVariable Long categoriaId, @Parameter(description = "Identificador da subcategoria.") @PathVariable Long id) {
        return service.desativar(categoriaId, id);
    }
}
