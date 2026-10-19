package br.com.chamati.service;

import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.dto.UsuarioAtualizarRequest;
import br.com.chamati.dto.UsuarioCriarRequest;
import br.com.chamati.dto.UsuarioResponse;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.UsuarioMapper;
import br.com.chamati.repository.RefreshTokenRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.repository.consulta.UsuarioResumo;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper mapper;

    public UsuarioService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
                          PasswordEncoder passwordEncoder, UsuarioMapper mapper) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    public UsuarioResponse criar(UsuarioCriarRequest req) {
        String email = normalizarEmail(req.email());
        if (usuarios.existsByEmail(email)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        Usuario u = new Usuario();
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setSenhaHash(passwordEncoder.encode(req.senha()));
        u.setPerfil(req.perfil());
        u.setDepartamento(vazioParaNulo(req.departamento()));
        u.setTelefone(vazioParaNulo(req.telefone()));
        u.setAtivo(true);
        return mapper.paraResponse(usuarios.saveAndFlush(u));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return mapper.paraResponse(obter(id));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResumo> listar(Perfil perfil, Boolean ativo, String termo, int pagina, int tamanho) {
        PageRequest pageable = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id")));
        return PaginaResponse.de(usuarios.buscar(perfil, ativo, vazioParaNulo(termo), pageable));
    }

    public UsuarioResponse atualizar(Long id, UsuarioAtualizarRequest req, Long atorId) {
        Usuario u = obter(id);
        String email = normalizarEmail(req.email());
        if (usuarios.existsByEmailAndIdNot(email, id)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        if (id.equals(atorId) && req.perfil() != u.getPerfil()) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setPerfil(req.perfil());
        u.setDepartamento(vazioParaNulo(req.departamento()));
        u.setTelefone(vazioParaNulo(req.telefone()));
        return mapper.paraResponse(usuarios.saveAndFlush(u));
    }

    public UsuarioResponse ativar(Long id) {
        Usuario u = obter(id);
        u.setAtivo(true);
        return mapper.paraResponse(usuarios.saveAndFlush(u));
    }

    /** Desativa o usuário e revoga todas as suas sessões (refresh tokens). */
    public UsuarioResponse desativar(Long id, Long atorId) {
        if (id.equals(atorId)) {
            throw new RegraNegocioException("Você não pode desativar a sua própria conta.");
        }
        Usuario u = obter(id);
        u.setAtivo(false);
        Usuario salvo = usuarios.saveAndFlush(u);
        refreshTokens.revogarTodosDoUsuario(id, Instant.now());
        return mapper.paraResponse(salvo);
    }

    private Usuario obter(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
