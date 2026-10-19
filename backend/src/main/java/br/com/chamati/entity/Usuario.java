package br.com.chamati.entity;

import br.com.chamati.enums.Perfil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "usuarios")
public class Usuario extends EntidadeAuditavel {

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /** Único no banco (uk_usuarios_email), sem diferenciar maiúsculas/acentos (collation _ai_ci). */
    @Column(name = "email", nullable = false, length = 160)
    private String email;

    /** Hash BCrypt. Nunca expor em DTOs. */
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "perfil", nullable = false, length = 20)
    private Perfil perfil;

    @Column(name = "departamento", length = 80)
    private String departamento;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_login_em")
    private Instant ultimoLoginEm;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }

    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public Instant getUltimoLoginEm() { return ultimoLoginEm; }
    public void setUltimoLoginEm(Instant ultimoLoginEm) { this.ultimoLoginEm = ultimoLoginEm; }
}
