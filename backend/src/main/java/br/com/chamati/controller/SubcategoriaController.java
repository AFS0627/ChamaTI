package br.com.chamati.controller;

import br.com.chamati.dto.SubcategoriaRequest;
import br.com.chamati.dto.SubcategoriaResponse;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.SubcategoriaService;
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

@RestController
@RequestMapping("/api/categorias/{categoriaId}/subcategorias")
public class SubcategoriaController {

    private final SubcategoriaService service;

    public SubcategoriaController(SubcategoriaService service) {
        this.service = service;
    }

    @GetMapping
    public List<SubcategoriaResponse> listar(@PathVariable Long categoriaId,
                                             @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.listar(categoriaId, CategoriaController.ehAdmin(atual));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubcategoriaResponse> criar(@PathVariable Long categoriaId,
                                                      @Valid @RequestBody SubcategoriaRequest req) {
        SubcategoriaResponse criada = service.criar(categoriaId, req);
        return ResponseEntity.created(URI.create("/api/categorias/" + categoriaId + "/subcategorias/" + criada.id()))
                .body(criada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubcategoriaResponse atualizar(@PathVariable Long categoriaId, @PathVariable Long id,
                                          @Valid @RequestBody SubcategoriaRequest req) {
        return service.atualizar(categoriaId, id, req);
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    public SubcategoriaResponse ativar(@PathVariable Long categoriaId, @PathVariable Long id) {
        return service.ativar(categoriaId, id);
    }

    @PatchMapping("/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    public SubcategoriaResponse desativar(@PathVariable Long categoriaId, @PathVariable Long id) {
        return service.desativar(categoriaId, id);
    }
}
