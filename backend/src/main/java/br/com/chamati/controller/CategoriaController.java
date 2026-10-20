package br.com.chamati.controller;

import br.com.chamati.dto.CategoriaRequest;
import br.com.chamati.dto.CategoriaResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.enums.Perfil;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.CategoriaService;
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
    public PaginaResponse<CategoriaResponse> listar(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(ativo, q, pagina, tamanho);
    }

    @GetMapping("/ativas")
    public List<CategoriaResponse> listarAtivas() {
        return service.listarAtivas();
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.buscar(id, ehAdmin(atual));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest req) {
        CategoriaResponse criada = service.criar(req);
        return ResponseEntity.created(URI.create("/api/categorias/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaResponse atualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest req) {
        return service.atualizar(id, req);
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaResponse ativar(@PathVariable Long id) {
        return service.ativar(id);
    }

    @PatchMapping("/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaResponse desativar(@PathVariable Long id) {
        return service.desativar(id);
    }

    static boolean ehAdmin(UsuarioPrincipal atual) {
        return atual != null && atual.perfil() == Perfil.ADMIN;
    }
}
