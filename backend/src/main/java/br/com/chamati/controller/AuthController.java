package br.com.chamati.controller;

import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.LoginRequest;
import br.com.chamati.dto.RefreshRequest;
import br.com.chamati.dto.TokenResponse;
import br.com.chamati.dto.TrocarSenhaRequest;
import br.com.chamati.dto.UsuarioSessaoResponse;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Login, renovação de sessão, logout e dados do usuário autenticado.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica com e-mail e senha",
            description = "Devolve um access token JWT (15 minutos) e um refresh token opaco (7 dias, uso único). O e-mail não diferencia maiúsculas. Após 5 falhas em 15 minutos para o mesmo e-mail e IP, novas tentativas recebem 429. Falhas de senha, e-mail inexistente e usuário inativo têm a mesma resposta 401, para não revelar quais e-mails existem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas de login; aguarde o tempo indicado em Retry-After.", headers = @Header(name = "Retry-After", description = "Segundos até uma nova tentativa.", schema = @Schema(type = "integer")), content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    @SecurityRequirements
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return service.login(req.email(), req.senha(), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova a sessão",
            description = "Troca um refresh token válido por um novo par de tokens (rotação: o refresh token enviado é revogado). Reutilizar um refresh token já revogado é tratado como roubo e revoga todas as sessões do usuário.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Novo par de tokens.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado, revogado ou de usuário inativo.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    @SecurityRequirements
    public TokenResponse renovar(@Valid @RequestBody RefreshRequest req) {
        return service.renovar(req.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão atual",
            description = "Revoga o refresh token informado. É idempotente: token desconhecido também responde 204. O access token já emitido continua válido até expirar.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sessão encerrada.")
    })
    @SecurityRequirements
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        service.encerrarSessao(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-todos")
    @Operation(summary = "Encerra todas as sessões do usuário",
            description = "Revoga todos os refresh tokens do usuário autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sessões encerradas.")
    })
    public ResponseEntity<Void> logoutTodos(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        service.encerrarTodasSessoes(atual.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/senha")
    @Operation(summary = "Troca a senha do usuário autenticado",
            description = "Exige a senha atual. A nova senha tem de 8 a 72 caracteres (até 72 bytes), com ao menos uma letra e um número, e deve ser diferente da atual. Todas as sessões (refresh tokens) são encerradas e a resposta traz um novo par de tokens para manter esta sessão aberta; access tokens já emitidos, inclusive de outros dispositivos, valem até expirar (15 minutos). Após 5 senhas atuais incorretas em 15 minutos a operação responde 429.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Senha alterada; novo par de tokens para a sessão atual.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "422", description = "Senha atual incorreta, nova senha igual à atual ou acima de 72 bytes.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas com a senha atual incorreta; aguarde o tempo indicado em Retry-After.", headers = @Header(name = "Retry-After", description = "Segundos até uma nova tentativa.", schema = @Schema(type = "integer")), content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public TokenResponse trocarSenha(@Valid @RequestBody TrocarSenhaRequest req,
                                    @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.trocarSenha(atual.id(), req.senhaAtual(), req.novaSenha());
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário autenticado",
            description = "Consulta o usuário no banco a cada chamada (perfil e situação atuais).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário autenticado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UsuarioSessaoResponse.class)))
    })
    public UsuarioSessaoResponse me(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return service.sessaoAtual(atual.id());
    }
}
