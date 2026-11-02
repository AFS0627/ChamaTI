package br.com.chamati.config;

import br.com.chamati.dto.UsuarioCriarRequest;
import br.com.chamati.enums.Perfil;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.service.UsuarioService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro ADMIN quando o sistema sobe sem nenhum administrador ativo (por exemplo, produção com banco vazio).
 * Só age se ADMIN_INICIAL_EMAIL e ADMIN_INICIAL_SENHA estiverem definidos; havendo um administrador ativo, é um
 * no-op e nunca altera usuários existentes. Configuração incompleta ou inválida interrompe a subida, para que a
 * falta de administrador não passe despercebida. A senha vem só do ambiente (nunca de migration ou do repositório),
 * não é registrada em log e deve ser trocada no primeiro acesso (PUT /api/auth/senha).
 */
@Component
public class AdminInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicialRunner.class);
    private static final int LIMITE_BCRYPT_BYTES = 72;

    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;
    private final Validator validator;
    private final String nome;
    private final String email;
    private final String senha;

    public AdminInicialRunner(UsuarioRepository usuarios, UsuarioService usuarioService, Validator validator,
                              @Value("${app.admin-inicial.nome:Administrador}") String nome,
                              @Value("${app.admin-inicial.email:}") String email,
                              @Value("${app.admin-inicial.senha:}") String senha) {
        this.usuarios = usuarios;
        this.usuarioService = usuarioService;
        this.validator = validator;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        executar();
    }

    void executar() {
        boolean temEmail = !email.isBlank();
        boolean temSenha = !senha.isBlank();
        if (!temEmail && !temSenha) {
            return;
        }
        if (temEmail != temSenha) {
            throw new IllegalStateException(
                    "Administrador inicial: defina ADMIN_INICIAL_EMAIL e ADMIN_INICIAL_SENHA juntos, ou nenhum dos dois.");
        }
        if (usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)) {
            log.info("Já existe um administrador ativo: ADMIN_INICIAL_* foi ignorada. Remova essas variáveis do ambiente.");
            return;
        }

        UsuarioCriarRequest pedido = new UsuarioCriarRequest(nome.isBlank() ? "Administrador" : nome.trim(),
                email.trim(), senha, Perfil.ADMIN, null, null);
        validar(pedido);

        try {
            usuarioService.criar(pedido);
        } catch (ConflitoException | DataIntegrityViolationException e) {
            if (usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMIN)) {
                log.info("O administrador inicial foi criado por outra instância.");
                return;
            }
            throw new IllegalStateException("Administrador inicial: o e-mail informado já pertence a um usuário que não é "
                    + "administrador ativo. Reative esse usuário ou use outro e-mail.", e);
        }
        log.warn("Administrador inicial criado ({}). Troque a senha no primeiro acesso (PUT /api/auth/senha) "
                + "e remova ADMIN_INICIAL_* do ambiente.", pedido.email().toLowerCase());
    }

    /** Valida pelas mesmas regras da criação de usuário, sem repetir valores nas mensagens. */
    private void validar(UsuarioCriarRequest pedido) {
        Set<ConstraintViolation<UsuarioCriarRequest>> violacoes = validator.validate(pedido);
        String problemas = violacoes.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        if (pedido.senha().getBytes(StandardCharsets.UTF_8).length > LIMITE_BCRYPT_BYTES) {
            problemas = (problemas.isEmpty() ? "" : problemas + "; ")
                    + "senha não pode ter mais de " + LIMITE_BCRYPT_BYTES + " bytes";
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Administrador inicial inválido: " + problemas + ".");
        }
    }
}
