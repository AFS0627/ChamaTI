package br.com.chamati.controller;

import br.com.chamati.dto.LoginRequest;
import br.com.chamati.dto.RefreshRequest;
import br.com.chamati.dto.TokenResponse;
import br.com.chamati.dto.UsuarioSessaoResponse;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return service.login(req.email(), req.senha(), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public TokenResponse renovar(@Valid @RequestBody RefreshRequest req) {
        return service.renovar(req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        service.encerrarSessao(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-todos")
    public ResponseEntity<Void> logoutTodos(@AuthenticationPrincipal UsuarioPrincipal atual) {
        service.encerrarTodasSessoes(atual.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UsuarioSessaoResponse me(@AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.sessaoAtual(atual.id());
    }
}
