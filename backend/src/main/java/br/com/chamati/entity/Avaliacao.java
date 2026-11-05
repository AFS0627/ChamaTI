package br.com.chamati.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** Avaliação do atendimento (uma por chamado). O técnico é o responsável no momento da avaliação. */
@Entity
@Table(name = "avaliacoes")
public class Avaliacao extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false, updatable = false)
    private Chamado chamado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tecnico_id", nullable = false, updatable = false)
    private Usuario tecnico;

    @Column(name = "nota", nullable = false, updatable = false)
    private int nota;

    @Column(name = "comentario", length = 1000, updatable = false)
    private String comentario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public Chamado getChamado() { return chamado; }
    public void setChamado(Chamado chamado) { this.chamado = chamado; }

    public Usuario getTecnico() { return tecnico; }
    public void setTecnico(Usuario tecnico) { this.tecnico = tecnico; }

    public int getNota() { return nota; }
    public void setNota(int nota) { this.nota = nota; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
}
