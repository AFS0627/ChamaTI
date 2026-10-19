package br.com.chamati.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Contador por ano do protocolo CT-AAAA-NNNNNN.
 * <p>
 * Mapeada apenas para leitura/consulta. NÃO incremente por aqui (ler, somar e salvar gera condição
 * de corrida): a geração deve usar o INSERT ... ON DUPLICATE KEY UPDATE com LAST_INSERT_ID descrito
 * em modelo-de-dados.md, via query nativa no repository.
 */
@Entity
@Table(name = "sequencias_protocolo")
public class SequenciaProtocolo {

    @Id
    @Column(name = "ano")
    private Short ano;

    @Column(name = "ultimo_numero", nullable = false)
    private int ultimoNumero;

    public Short getAno() { return ano; }
    public int getUltimoNumero() { return ultimoNumero; }
}
