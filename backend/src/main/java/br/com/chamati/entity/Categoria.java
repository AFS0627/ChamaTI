package br.com.chamati.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "categorias")
public class Categoria extends EntidadeAuditavel {

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    /** Lado inverso (somente leitura). Para criar subcategorias, persista a Subcategoria. */
    @OneToMany(mappedBy = "categoria", fetch = FetchType.LAZY)
    @OrderBy("nome ASC")
    private List<Subcategoria> subcategorias = new ArrayList<>();

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public List<Subcategoria> getSubcategorias() { return Collections.unmodifiableList(subcategorias); }
}
