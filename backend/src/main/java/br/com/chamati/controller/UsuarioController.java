package br.com.chamati.controller;

import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.dto.UsuarioAtualizarRequest;
import br.com.chamati.dto.UsuarioCriarRequest;
import br.com.chamati.dto.UsuarioResponse;
import br.com.chamati.enums.Perfil;
import br.com.chamati.repository.consulta.UsuarioResumo;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.UsuarioService;
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
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioCriarRequest req) {
        UsuarioResponse criado = service.criar(req);
        return ResponseEntity.created(URI.create("/api/usuarios/" + criado.id())).body(criado);
    }

    @GetMapping
    public PaginaResponse<UsuarioResumo> listar(
            @RequestParam(required = false) Perfil perfil,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(perfil, ativo, q, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioAtualizarRequest req,
                                     @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.atualizar(id, req, idDe(atual));
    }

    @PatchMapping("/{id}/ativar")
    public UsuarioResponse ativar(@PathVariable Long id) {
        return service.ativar(id);
    }

    @PatchMapping("/{id}/desativar")
    public UsuarioResponse desativar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.desativar(id, idDe(atual));
    }

    private static Long idDe(UsuarioPrincipal atual) {
        return atual == null ? null : atual.id();
    }
}
