package br.com.chamati;

import br.com.chamati.entity.Categoria;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Subcategoria;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.test.util.ReflectionTestUtils;

/** Objetos de domínio prontos para os testes unitários (sem banco, sem Spring). */
public final class Fixtures {

    public static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");

    private Fixtures() {
    }

    public static Clock relogio() {
        return Clock.fixed(AGORA, ZoneOffset.UTC);
    }

    public static Usuario usuario(Long id, String nome, Perfil perfil) {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", id);
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + "@chamati.local");
        u.setSenhaHash("hash-" + id);
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }

    public static UsuarioPrincipal principal(Usuario u) {
        return new UsuarioPrincipal(u.getId(), u.getEmail(), u.getPerfil());
    }

    public static Categoria categoria(Long id, boolean ativo) {
        Categoria c = new Categoria();
        ReflectionTestUtils.setField(c, "id", id);
        c.setNome("Categoria " + id);
        c.setAtivo(ativo);
        return c;
    }

    public static Subcategoria subcategoria(Long id, Categoria categoria, boolean ativo) {
        Subcategoria s = new Subcategoria();
        ReflectionTestUtils.setField(s, "id", id);
        s.setCategoria(categoria);
        s.setNome("Subcategoria " + id);
        s.setAtivo(ativo);
        return s;
    }

    public static Chamado chamado(Long id, Usuario solicitante, Usuario tecnico, StatusChamado status) {
        Chamado c = new Chamado();
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "versao", 0L);
        c.setProtocolo(String.format("CT-2026-%06d", id));
        c.setTitulo("Chamado " + id);
        c.setDescricao("Descrição do chamado " + id);
        c.setPrioridade(Prioridade.MEDIA);
        c.setStatus(status);
        c.setSolicitante(solicitante);
        c.setTecnico(tecnico);
        c.setCriadoEm(AGORA.minusSeconds(3600));
        return c;
    }
}
