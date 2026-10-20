package br.com.chamati.controller;

import br.com.chamati.dto.PoliticaSlaRequest;
import br.com.chamati.dto.PoliticaSlaResponse;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.service.PoliticaSlaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Leitura: qualquer usuário autenticado. Alteração: ADMIN. */
@RestController
@RequestMapping("/api/politicas-sla")
public class PoliticaSlaController {

    private final PoliticaSlaService service;

    public PoliticaSlaController(PoliticaSlaService service) {
        this.service = service;
    }

    @GetMapping
    public List<PoliticaSlaResponse> listar() {
        return service.listar();
    }

    @PutMapping("/{prioridade}")
    @PreAuthorize("hasRole('ADMIN')")
    public PoliticaSlaResponse atualizar(@PathVariable Prioridade prioridade,
                                         @Valid @RequestBody PoliticaSlaRequest req) {
        return service.atualizar(prioridade, req);
    }
}
