package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.usuario;
import static br.com.chamati.enums.Perfil.SOLICITANTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.config.JwtProperties;
import br.com.chamati.dto.TokenResponse;
import br.com.chamati.dto.UsuarioSessaoResponse;
import br.com.chamati.entity.RefreshToken;
import br.com.chamati.entity.Usuario;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.exception.TentativasExcedidasException;
import br.com.chamati.repository.RefreshTokenRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.JwtService;
import br.com.chamati.security.TentativasLogin;
import br.com.chamati.security.TokenHash;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    static final String IP = "10.0.0.1";
    static final String EMAIL = "ana.souza@chamati.local";
    static final String CHAVE = EMAIL + "|" + IP;
    static final String HASH_FALSO = "$2a$04$hash-falso";
    static final String HASH_ANA = "$2a$04$hash-da-ana";
    static final String ACCESS_TOKEN = "access.token.jwt";

    @Mock UsuarioRepository usuarios;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwtService;
    @Mock TentativasLogin tentativas;

    final JwtProperties props = new JwtProperties("segredo-apenas-para-testes-0123456789-abcdefghij",
            "chamati", Duration.ofMinutes(15), Duration.ofDays(7));
    final Clock relogio = Fixtures.relogio();

    AuthService service;
    Usuario ana;

    @BeforeEach
    void preparar() {
        when(encoder.encode(anyString())).thenReturn(HASH_FALSO);
        service = new AuthService(usuarios, refreshTokens, encoder, jwtService, props, tentativas, relogio);
        ana = usuario(4L, "Ana Souza", SOLICITANTE);
        ana.setSenhaHash(HASH_ANA);
    }

    private RefreshToken token(Usuario dono, java.time.Instant expiraEm, java.time.Instant revogadoEm) {
        RefreshToken t = new RefreshToken();
        ReflectionTestUtils.setField(t, "id", 55L);
        t.setUsuario(dono);
        t.setTokenHash(TokenHash.sha256Hex("bruto"));
        t.setExpiraEm(expiraEm);
        t.setRevogadoEm(revogadoEm);
        return t;
    }

    private void prepararEmissao() {
        when(usuarios.getReferenceById(ana.getId())).thenReturn(ana);
        when(jwtService.gerarAccessToken(ana)).thenReturn(ACCESS_TOKEN);
    }

    // ---------- login ----------

    @Test
    void loginComCredenciaisValidasEmiteAccessERefreshToken() {
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(encoder.matches("Senha@123", HASH_ANA)).thenReturn(true);
        prepararEmissao();

        TokenResponse resposta = service.login("Ana.Souza@Chamati.local", "Senha@123", IP);

        assertThat(resposta.tipo()).isEqualTo("Bearer");
        assertThat(resposta.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(resposta.expiraEmSegundos()).isEqualTo(900);
        assertThat(resposta.refreshToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(resposta.refreshExpiraEm()).isEqualTo(AGORA.plus(Duration.ofDays(7)));
        assertThat(resposta.usuario()).isEqualTo(new UsuarioSessaoResponse(4L, "Ana Souza", ana.getEmail(), SOLICITANTE));
        assertThat(ana.getUltimoLoginEm()).isEqualTo(AGORA);
        verify(tentativas).verificar(CHAVE);
        verify(tentativas).limpar(CHAVE);
        verify(tentativas, never()).registrarFalha(anyString());
    }

    @Test
    void loginNormalizaOEmailAntesDeBuscarELimitarTentativas() {
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(encoder.matches("Senha@123", HASH_ANA)).thenReturn(true);
        prepararEmissao();

        service.login("  ANA.SOUZA@CHAMATI.LOCAL  ", "Senha@123", IP);

        verify(tentativas).verificar(CHAVE);
        verify(usuarios).findByEmail(EMAIL);
    }

    @Test
    void loginGuardaSomenteOHashDoRefreshToken() {
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(encoder.matches("Senha@123", HASH_ANA)).thenReturn(true);
        prepararEmissao();

        TokenResponse resposta = service.login(EMAIL, "Senha@123", IP);

        ArgumentCaptor<RefreshToken> salvo = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(salvo.capture());
        assertThat(salvo.getValue().getTokenHash()).isEqualTo(TokenHash.sha256Hex(resposta.refreshToken()));
        assertThat(salvo.getValue().getTokenHash()).isNotEqualTo(resposta.refreshToken());
        assertThat(salvo.getValue().getExpiraEm()).isEqualTo(resposta.refreshExpiraEm());
        assertThat(salvo.getValue().getUsuario()).isSameAs(ana);
        assertThat(salvo.getValue().estaRevogado()).isFalse();
    }

    @Test
    void cadaLoginGeraUmRefreshTokenDiferente() {
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(encoder.matches("Senha@123", HASH_ANA)).thenReturn(true);
        prepararEmissao();

        String primeiro = service.login(EMAIL, "Senha@123", IP).refreshToken();
        String segundo = service.login(EMAIL, "Senha@123", IP).refreshToken();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    void loginComSenhaIncorretaRegistraFalhaESemRevelarOMotivo() {
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(encoder.matches("errada", HASH_ANA)).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "errada", IP))
                .isInstanceOf(AutenticacaoException.class)
                .hasMessage("Credenciais inválidas.");

        verify(tentativas).registrarFalha(CHAVE);
        verify(tentativas, never()).limpar(anyString());
        verifyNoInteractions(refreshTokens, jwtService);
        assertThat(ana.getUltimoLoginEm()).isNull();
    }

    @Test
    void loginComEmailInexistenteUsaOMesmoErroEConfereContraHashFalso() {
        when(usuarios.findByEmail("fantasma@chamati.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("fantasma@chamati.local", "qualquer", IP))
                .isInstanceOf(AutenticacaoException.class)
                .hasMessage("Credenciais inválidas.");

        verify(encoder).matches("qualquer", HASH_FALSO);
        verify(tentativas).registrarFalha("fantasma@chamati.local|" + IP);
        verifyNoInteractions(refreshTokens, jwtService);
    }

    @Test
    void loginDeUsuarioInativoComSenhaCorretaFalhaComAMesmaMensagem() {
        ana.setAtivo(false);
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(encoder.matches("Senha@123", HASH_ANA)).thenReturn(true);

        assertThatThrownBy(() -> service.login(EMAIL, "Senha@123", IP))
                .isInstanceOf(AutenticacaoException.class)
                .hasMessage("Credenciais inválidas.");

        verify(tentativas).registrarFalha(CHAVE);
        verifyNoInteractions(refreshTokens, jwtService);
    }

    @Test
    void loginBloqueadoNaoConsultaOBancoNemConfereSenha() {
        doThrow(new TentativasExcedidasException(600)).when(tentativas).verificar(CHAVE);

        assertThatThrownBy(() -> service.login(EMAIL, "Senha@123", IP))
                .isInstanceOfSatisfying(TentativasExcedidasException.class,
                        e -> assertThat(e.getSegundos()).isEqualTo(600));

        verifyNoInteractions(usuarios, refreshTokens, jwtService);
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void aposCincoFalhasSeguidasOLoginFicaBloqueadoMesmoComSenhaCorreta() {
        AuthService comLimitadorReal = new AuthService(usuarios, refreshTokens, encoder, jwtService, props,
                new TentativasLogin(relogio), relogio);
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(ana));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> comLimitadorReal.login(EMAIL, "errada", IP))
                    .isInstanceOf(AutenticacaoException.class);
        }

        assertThatThrownBy(() -> comLimitadorReal.login(EMAIL, "Senha@123", IP))
                .isInstanceOf(TentativasExcedidasException.class);
        verify(encoder, never()).matches(eq("Senha@123"), anyString());
    }

    // ---------- renovar ----------

    @Test
    void renovarRevogaOTokenAtualEEmiteUmNovoPar() {
        RefreshToken atual = token(ana, AGORA.plusSeconds(3600), null);
        when(refreshTokens.findByTokenHash(TokenHash.sha256Hex("bruto"))).thenReturn(Optional.of(atual));
        when(refreshTokens.revogarSeAtivo(55L, AGORA)).thenReturn(1);
        prepararEmissao();

        TokenResponse resposta = service.renovar("bruto");

        assertThat(resposta.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(resposta.refreshToken()).isNotEqualTo("bruto").hasSize(43);
        verify(refreshTokens).revogarSeAtivo(55L, AGORA);
        verify(refreshTokens, never()).revogarTodosDoUsuario(anyLong(), any());
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void renovarComTokenDesconhecidoFalhaSemRevogarNada() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar("desconhecido"))
                .isInstanceOf(AutenticacaoException.class)
                .hasMessage("Sessão inválida ou expirada.");

        verify(refreshTokens, never()).revogarTodosDoUsuario(anyLong(), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void reusoDeTokenRevogadoRevogaTodasAsSessoesDoUsuario() {
        RefreshToken revogado = token(ana, AGORA.plusSeconds(3600), AGORA.minusSeconds(60));
        when(refreshTokens.findByTokenHash(TokenHash.sha256Hex("bruto"))).thenReturn(Optional.of(revogado));

        assertThatThrownBy(() -> service.renovar("bruto"))
                .isInstanceOf(AutenticacaoException.class)
                .hasMessage("Sessão inválida ou expirada.");

        verify(refreshTokens).revogarTodosDoUsuario(4L, AGORA);
        verify(refreshTokens, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    void renovarComTokenExpiradoFalha() {
        RefreshToken expirado = token(ana, AGORA.minusSeconds(1), null);
        when(refreshTokens.findByTokenHash(TokenHash.sha256Hex("bruto"))).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.renovar("bruto")).isInstanceOf(AutenticacaoException.class);

        verify(refreshTokens, never()).revogarSeAtivo(anyLong(), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void tokenQueExpiraExatamenteAgoraJaEstaExpirado() {
        RefreshToken noLimite = token(ana, AGORA, null);
        when(refreshTokens.findByTokenHash(TokenHash.sha256Hex("bruto"))).thenReturn(Optional.of(noLimite));

        assertThatThrownBy(() -> service.renovar("bruto")).isInstanceOf(AutenticacaoException.class);
    }

    @Test
    void renovarDeUsuarioInativoFalha() {
        ana.setAtivo(false);
        RefreshToken valido = token(ana, AGORA.plusSeconds(3600), null);
        when(refreshTokens.findByTokenHash(TokenHash.sha256Hex("bruto"))).thenReturn(Optional.of(valido));

        assertThatThrownBy(() -> service.renovar("bruto")).isInstanceOf(AutenticacaoException.class);

        verify(refreshTokens, never()).save(any());
    }

    @Test
    void corridaNaRotacaoTratadaComoReusoERevogaTodasAsSessoes() {
        RefreshToken atual = token(ana, AGORA.plusSeconds(3600), null);
        when(refreshTokens.findByTokenHash(TokenHash.sha256Hex("bruto"))).thenReturn(Optional.of(atual));
        when(refreshTokens.revogarSeAtivo(55L, AGORA)).thenReturn(0);

        assertThatThrownBy(() -> service.renovar("bruto")).isInstanceOf(AutenticacaoException.class);

        verify(refreshTokens).revogarTodosDoUsuario(4L, AGORA);
        verify(refreshTokens, never()).save(any());
    }

    // ---------- logout e sessão ----------

    @Test
    void encerrarSessaoRevogaOTokenInformado() {
        RefreshToken atual = token(ana, AGORA.plusSeconds(3600), null);
        when(refreshTokens.findByTokenHash(TokenHash.sha256Hex("bruto"))).thenReturn(Optional.of(atual));

        service.encerrarSessao("bruto");

        verify(refreshTokens).revogarSeAtivo(55L, AGORA);
    }

    @Test
    void encerrarSessaoComTokenDesconhecidoNaoFalhaENaoRevelaNada() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.encerrarSessao("qualquer");

        verify(refreshTokens, never()).revogarSeAtivo(anyLong(), any());
    }

    @Test
    void encerrarTodasSessoesRevogaOsTokensDoUsuario() {
        service.encerrarTodasSessoes(4L);

        verify(refreshTokens).revogarTodosDoUsuario(4L, AGORA);
    }

    @Test
    void sessaoAtualRetornaOsDadosDoUsuarioAtivo() {
        when(usuarios.findById(4L)).thenReturn(Optional.of(ana));

        UsuarioSessaoResponse resposta = service.sessaoAtual(4L);

        assertThat(resposta.id()).isEqualTo(4L);
        assertThat(resposta.nome()).isEqualTo("Ana Souza");
        assertThat(resposta.perfil()).isEqualTo(SOLICITANTE);
    }

    @Test
    void sessaoAtualDeUsuarioInativoOuInexistenteFalha() {
        ana.setAtivo(false);
        when(usuarios.findById(4L)).thenReturn(Optional.of(ana));
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sessaoAtual(4L)).isInstanceOf(AutenticacaoException.class);
        assertThatThrownBy(() -> service.sessaoAtual(99L)).isInstanceOf(AutenticacaoException.class);
    }
}
