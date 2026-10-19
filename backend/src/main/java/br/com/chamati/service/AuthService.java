package br.com.chamati.service;

import br.com.chamati.config.JwtProperties;
import br.com.chamati.dto.TokenResponse;
import br.com.chamati.dto.UsuarioSessaoResponse;
import br.com.chamati.entity.RefreshToken;
import br.com.chamati.entity.Usuario;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.repository.RefreshTokenRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.JwtService;
import br.com.chamati.security.TentativasLogin;
import br.com.chamati.security.TokenHash;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessão = access token JWT curto + refresh token opaco (aleatório, 256 bits) guardado só como
 * hash SHA-256. O refresh token é rotativo: cada uso o revoga e emite outro. Reapresentar um
 * refresh token já revogado indica vazamento e revoga todas as sessões do usuário.
 */
@Service
public class AuthService {

    private static final String CREDENCIAIS_INVALIDAS = "Credenciais inválidas.";
    private static final String SESSAO_INVALIDA = "Sessão inválida ou expirada.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties props;
    private final TentativasLogin tentativas;
    private final Clock clock;
    /** Hash descartável: iguala o tempo de resposta quando o e-mail não existe. */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties props,
                       TentativasLogin tentativas, Clock clock) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
        this.tentativas = tentativas;
        this.clock = clock;
        this.hashFalso = passwordEncoder.encode("senha-falsa-para-igualar-o-tempo");
    }

    @Transactional(noRollbackFor = AutenticacaoException.class)
    public TokenResponse login(String email, String senha, String ip) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        String chave = emailNormalizado + "|" + ip;
        tentativas.verificar(chave);

        Usuario usuario = usuarios.findByEmail(emailNormalizado).orElse(null);
        boolean senhaConfere = passwordEncoder.matches(senha, usuario != null ? usuario.getSenhaHash() : hashFalso);
        if (usuario == null || !senhaConfere || !usuario.isAtivo()) {
            tentativas.registrarFalha(chave);
            throw new AutenticacaoException(CREDENCIAIS_INVALIDAS);
        }

        tentativas.limpar(chave);
        usuario.setUltimoLoginEm(clock.instant());
        return emitir(usuario);
    }

    /** Troca um refresh token válido por um novo par de tokens (rotação). */
    @Transactional(noRollbackFor = AutenticacaoException.class)
    public TokenResponse renovar(String refreshBruto) {
        Instant agora = clock.instant();
        RefreshToken atual = refreshTokens.findByTokenHash(TokenHash.sha256Hex(refreshBruto))
                .orElseThrow(() -> new AutenticacaoException(SESSAO_INVALIDA));
        Usuario usuario = atual.getUsuario();

        if (atual.estaRevogado()) {
            refreshTokens.revogarTodosDoUsuario(usuario.getId(), agora);
            throw new AutenticacaoException(SESSAO_INVALIDA);
        }
        if (atual.estaExpirado(agora) || !usuario.isAtivo()) {
            throw new AutenticacaoException(SESSAO_INVALIDA);
        }
        if (refreshTokens.revogarSeAtivo(atual.getId(), agora) == 0) {
            refreshTokens.revogarTodosDoUsuario(usuario.getId(), agora);
            throw new AutenticacaoException(SESSAO_INVALIDA);
        }
        return emitir(usuario);
    }

    /** Encerra a sessão do refresh token informado. Idempotente e sem revelar se o token existia. */
    @Transactional
    public void encerrarSessao(String refreshBruto) {
        refreshTokens.findByTokenHash(TokenHash.sha256Hex(refreshBruto))
                .ifPresent(t -> refreshTokens.revogarSeAtivo(t.getId(), clock.instant()));
    }

    @Transactional
    public void encerrarTodasSessoes(Long usuarioId) {
        refreshTokens.revogarTodosDoUsuario(usuarioId, clock.instant());
    }

    @Transactional(readOnly = true)
    public UsuarioSessaoResponse sessaoAtual(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .filter(Usuario::isAtivo)
                .map(AuthService::paraSessao)
                .orElseThrow(() -> new AutenticacaoException(SESSAO_INVALIDA));
    }

    private TokenResponse emitir(Usuario usuario) {
        String bruto = gerarTokenOpaco();
        RefreshToken novo = new RefreshToken();
        novo.setUsuario(usuarios.getReferenceById(usuario.getId()));
        novo.setTokenHash(TokenHash.sha256Hex(bruto));
        novo.setExpiraEm(clock.instant().plus(props.refreshTtl()));
        refreshTokens.save(novo);

        return new TokenResponse("Bearer", jwtService.gerarAccessToken(usuario),
                props.accessTtl().toSeconds(), bruto, novo.getExpiraEm(), paraSessao(usuario));
    }

    private static String gerarTokenOpaco() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static UsuarioSessaoResponse paraSessao(Usuario u) {
        return new UsuarioSessaoResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil());
    }
}
