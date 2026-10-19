package br.com.chamati.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

/**
 * criado_em / atualizado_em (DATETIME(6), UTC). O banco tem DEFAULT e ON UPDATE, mas o JPA envia
 * as colunas no INSERT, então os valores são preenchidos aqui quando ainda estiverem nulos.
 */
@MappedSuperclass
public abstract class EntidadeAuditavel extends EntidadeBase {

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void aoPersistir() {
        Instant agora = Instant.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = criadoEm;
        }
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    /** Permite ao service fixar o instante de criação antes de calcular os limites de SLA. */
    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
