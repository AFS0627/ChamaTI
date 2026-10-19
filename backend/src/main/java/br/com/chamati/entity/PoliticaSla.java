package br.com.chamati.entity;

import br.com.chamati.enums.Prioridade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Uma linha por prioridade; prazos em minutos corridos. */
@Entity
@Table(name = "politicas_sla")
public class PoliticaSla extends EntidadeAuditavel {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "prioridade", nullable = false, length = 10)
    private Prioridade prioridade;

    @Column(name = "prazo_primeira_resposta_min", nullable = false)
    private int prazoPrimeiraRespostaMin;

    @Column(name = "prazo_resolucao_min", nullable = false)
    private int prazoResolucaoMin;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    public Prioridade getPrioridade() { return prioridade; }
    public void setPrioridade(Prioridade prioridade) { this.prioridade = prioridade; }

    public int getPrazoPrimeiraRespostaMin() { return prazoPrimeiraRespostaMin; }
    public void setPrazoPrimeiraRespostaMin(int v) { this.prazoPrimeiraRespostaMin = v; }

    public int getPrazoResolucaoMin() { return prazoResolucaoMin; }
    public void setPrazoResolucaoMin(int v) { this.prazoResolucaoMin = v; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
