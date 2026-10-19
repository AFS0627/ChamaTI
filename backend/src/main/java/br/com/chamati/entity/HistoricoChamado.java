package br.com.chamati.entity;

import br.com.chamati.enums.TipoEventoHistorico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Trilha de auditoria somente de inserção: @Immutable impede UPDATE pelo Hibernate.
 * Eventos NOTA_INTERNA não podem ser exibidos ao SOLICITANTE.
 */
@Entity
@Immutable
@Table(name = "historico_chamados")
public class HistoricoChamado extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false, updatable = false)
    private Chamado chamado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false, updatable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tipo_evento", nullable = false, length = 25, updatable = false)
    private TipoEventoHistorico tipoEvento;

    @Column(name = "campo", length = 50, updatable = false)
    private String campo;

    @Column(name = "valor_anterior", length = 255, updatable = false)
    private String valorAnterior;

    @Column(name = "valor_novo", length = 255, updatable = false)
    private String valorNovo;

    @Column(name = "descricao", length = 500, updatable = false)
    private String descricao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected HistoricoChamado() {
        // exigido pelo JPA
    }

    public HistoricoChamado(Chamado chamado, Usuario autor, TipoEventoHistorico tipoEvento,
                            String campo, String valorAnterior, String valorNovo, String descricao) {
        this.chamado = chamado;
        this.autor = autor;
        this.tipoEvento = tipoEvento;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
        this.descricao = descricao;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public Chamado getChamado() { return chamado; }
    public Usuario getAutor() { return autor; }
    public TipoEventoHistorico getTipoEvento() { return tipoEvento; }
    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNovo() { return valorNovo; }
    public String getDescricao() { return descricao; }
    public Instant getCriadoEm() { return criadoEm; }
}
