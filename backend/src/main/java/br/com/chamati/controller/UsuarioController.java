package br.com.chamati.controller;

import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.dto.UsuarioAtualizarRequest;
import br.com.chamati.dto.UsuarioCriarRequest;
import br.com.chamati.dto.UsuarioResponse;
import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.consulta.UsuarioResumo;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.UsuarioService;
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

/** Administração de usuários: somente ADMIN. */
@Tag(name = "Usuários", description = "Administração de usuários. Todas as operações exigem o perfil ADMIN.")
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria um usuário",
            description = "O e-mail é normalizado (minúsculas) e deve ser único. A senha é gravada com BCrypt e o usuário nasce ativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado. O cabeçalho Location aponta para o novo recurso.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe um usuário com este e-mail.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioCriarRequest req) {
        UsuarioResponse criado = service.criar(req);
        return ResponseEntity.created(URI.create("/api/usuarios/" + criado.id())).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista usuários",
            description = "Paginado, ordenado por nome. Nunca devolve o hash da senha. Filtros combináveis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuários.")
    })
    public PaginaResponse<UsuarioResumo> listar(
            @Parameter(description = "Filtra pelo perfil.") @RequestParam(required = false) Perfil perfil,
            @Parameter(description = "Filtra pela situação (true = ativos, false = inativos).") @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "Texto buscado no nome e no e-mail.") @RequestParam(required = false) @Size(max = 100) String q,
            @Parameter(description = "Número da página, começando em 0.") @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página (1 a 100).") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(perfil, ativo, q, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um usuário",
            description = "Busca por identificador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public UsuarioResponse buscar(@Parameter(description = "Identificador do usuário.") @PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza nome, e-mail, perfil, departamento e telefone",
            description = "Não altera senha nem situação (ativo). Ninguém pode alterar o próprio perfil.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário atualizado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail já usado por outro usuário.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Tentativa de alterar o próprio perfil.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public UsuarioResponse atualizar(@Parameter(description = "Identificador do usuário.") @PathVariable Long id, @Valid @RequestBody UsuarioAtualizarRequest req,
                                     @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.atualizar(id, req, idDe(atual));
    }

    @PatchMapping("/{id}/ativar")
    @Operation(summary = "Ativa um usuário",
            description = "Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário ativo.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public UsuarioResponse ativar(@Parameter(description = "Identificador do usuário.") @PathVariable Long id) {
        return service.ativar(id);
    }

    @PatchMapping("/{id}/desativar")
    @Operation(summary = "Desativa um usuário",
            description = "Revoga todos os refresh tokens do usuário; o access token deixa de valer na próxima requisição. Ninguém desativa a própria conta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário inativo.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Tentativa de desativar a própria conta.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public UsuarioResponse desativar(@Parameter(description = "Identificador do usuário.") @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.desativar(id, idDe(atual));
    }

    private static Long idDe(UsuarioPrincipal atual) {
        return atual == null ? null : atual.id();
    }
}
