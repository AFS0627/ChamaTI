package br.com.chamati.entity;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chamados")
public class Chamado extends EntidadeAuditavel {

    /** CT-AAAA-NNNNNN, único (uk_chamados_protocolo). */
    @Column(name = "protocolo", nullable = false, length = 20)
    private String protocolo;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descricao", nullable = false, columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /**
     * Opcional. O banco garante que a subcategoria pertence à categoria do chamado por meio da FK
     * composta (subcategoria_id, categoria_id) -> subcategorias (id, categoria_id); por isso o JPA
     * mapeia só subcategoria_id. O service deve validar antes para devolver um erro claro.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategoria_id")
    private Subcategoria subcategoria;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "prioridade", nullable = false, length = 10)
    private Prioridade prioridade = Prioridade.MEDIA;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 25)
    private StatusChamado status = StatusChamado.ABERTO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    /** Deve ser usuário ativo com perfil TECNICO: regra do service, o banco não impede. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private Usuario tecnico;

    @Column(name = "solucao", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String solucao;

    @Column(name = "atribuido_em")
    private Instant atribuidoEm;

    @Column(name = "primeira_resposta_em")
    private Instant primeiraRespostaEm;

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    @Column(name = "fechado_em")
    private Instant fechadoEm;

    @Column(name = "reaberto_em")
    private Instant reabertoEm;

    @Column(name = "total_reaberturas", nullable = false)
    private int totalReaberturas = 0;

    @Column(name = "sla_primeira_resposta_limite")
    private Instant slaPrimeiraRespostaLimite;

    @Column(name = "sla_resolucao_limite")
    private Instant slaResolucaoLimite;

    /** Bloqueio otimista (coluna versao, DEFAULT 0). */
    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    /** Lados inversos, somente leitura: persista Comentario / HistoricoChamado diretamente. */
    @OneToMany(mappedBy = "chamado", fetch = FetchType.LAZY)
    @OrderBy("criadoEm ASC, id ASC")
    private List<Comentario> comentarios = new ArrayList<>();

    @OneToMany(mappedBy = "chamado", fetch = FetchType.LAZY)
    @OrderBy("criadoEm ASC, id ASC")
    private List<HistoricoChamado> historico = new ArrayList<>();

    public String getProtocolo() { return protocolo; }
    public void setProtocolo(String protocolo) { this.protocolo = protocolo; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public Subcategoria getSubcategoria() { return subcategoria; }
    public void setSubcategoria(Subcategoria subcategoria) { this.subcategoria = subcategoria; }

    public Prioridade getPrioridade() { return prioridade; }
    public void setPrioridade(Prioridade prioridade) { this.prioridade = prioridade; }

    public StatusChamado getStatus() { return status; }
    public void setStatus(StatusChamado status) { this.status = status; }

    public Usuario getSolicitante() { return solicitante; }
    public void setSolicitante(Usuario solicitante) { this.solicitante = solicitante; }

    public Usuario getTecnico() { return tecnico; }
    public void setTecnico(Usuario tecnico) { this.tecnico = tecnico; }

    public String getSolucao() { return solucao; }
    public void setSolucao(String solucao) { this.solucao = solucao; }

    public Instant getAtribuidoEm() { return atribuidoEm; }
    public void setAtribuidoEm(Instant atribuidoEm) { this.atribuidoEm = atribuidoEm; }

    public Instant getPrimeiraRespostaEm() { return primeiraRespostaEm; }
    public void setPrimeiraRespostaEm(Instant v) { this.primeiraRespostaEm = v; }

    public Instant getResolvidoEm() { return resolvidoEm; }
    public void setResolvidoEm(Instant resolvidoEm) { this.resolvidoEm = resolvidoEm; }

    public Instant getFechadoEm() { return fechadoEm; }
    public void setFechadoEm(Instant fechadoEm) { this.fechadoEm = fechadoEm; }

    public Instant getReabertoEm() { return reabertoEm; }
    public void setReabertoEm(Instant reabertoEm) { this.reabertoEm = reabertoEm; }

    public int getTotalReaberturas() { return totalReaberturas; }
    public void setTotalReaberturas(int totalReaberturas) { this.totalReaberturas = totalReaberturas; }

    public Instant getSlaPrimeiraRespostaLimite() { return slaPrimeiraRespostaLimite; }
    public void setSlaPrimeiraRespostaLimite(Instant v) { this.slaPrimeiraRespostaLimite = v; }

    public Instant getSlaResolucaoLimite() { return slaResolucaoLimite; }
    public void setSlaResolucaoLimite(Instant v) { this.slaResolucaoLimite = v; }

    public Long getVersao() { return versao; }

    public List<Comentario> getComentarios() { return Collections.unmodifiableList(comentarios); }
    public List<HistoricoChamado> getHistorico() { return Collections.unmodifiableList(historico); }
}
