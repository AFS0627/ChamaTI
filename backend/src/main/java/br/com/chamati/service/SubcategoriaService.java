package br.com.chamati.service;

import br.com.chamati.dto.SubcategoriaRequest;
import br.com.chamati.dto.SubcategoriaResponse;
import br.com.chamati.entity.Categoria;
import br.com.chamati.entity.Subcategoria;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.CatalogoMapper;
import br.com.chamati.repository.CategoriaRepository;
import br.com.chamati.repository.SubcategoriaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toda operação recebe a categoria pai e a subcategoria é sempre buscada dentro dela, o que impede
 * associações inconsistentes. Uma subcategoria nunca muda de categoria (o chamado referencia o par
 * subcategoria+categoria por chave estrangeira composta).
 */
@Service
@Transactional
public class SubcategoriaService {

    private final SubcategoriaRepository subcategorias;
    private final CategoriaRepository categorias;
    private final CatalogoMapper mapper;

    public SubcategoriaService(SubcategoriaRepository subcategorias, CategoriaRepository categorias,
                               CatalogoMapper mapper) {
        this.subcategorias = subcategorias;
        this.categorias = categorias;
        this.mapper = mapper;
    }

    /** ADMIN vê todas; os demais veem só as ativas, e apenas de categorias ativas. */
    @Transactional(readOnly = true)
    public List<SubcategoriaResponse> listar(Long categoriaId, boolean admin) {
        Categoria categoria = obterCategoria(categoriaId);
        if (!admin && !categoria.isAtivo()) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        List<Subcategoria> lista = admin
                ? subcategorias.findByCategoriaIdOrderByNomeAsc(categoriaId)
                : subcategorias.findByCategoriaIdAndAtivoTrueOrderByNomeAsc(categoriaId);
        return lista.stream().map(mapper::paraResponse).toList();
    }

    public SubcategoriaResponse criar(Long categoriaId, SubcategoriaRequest req) {
        Categoria categoria = obterCategoria(categoriaId);
        if (!categoria.isAtivo()) {
            throw new RegraNegocioException("Não é possível criar subcategoria em uma categoria inativa.");
        }
        String nome = req.nome().trim();
        if (subcategorias.existsByCategoriaIdAndNome(categoriaId, nome)) {
            throw new ConflitoException("Já existe uma subcategoria com este nome nesta categoria.");
        }
        Subcategoria s = new Subcategoria();
        s.setCategoria(categoria);
        s.setNome(nome);
        s.setDescricao(vazioParaNulo(req.descricao()));
        s.setAtivo(true);
        return mapper.paraResponse(subcategorias.saveAndFlush(s));
    }

    public SubcategoriaResponse atualizar(Long categoriaId, Long id, SubcategoriaRequest req) {
        Subcategoria s = obter(categoriaId, id);
        String nome = req.nome().trim();
        if (subcategorias.existsByCategoriaIdAndNomeAndIdNot(categoriaId, nome, id)) {
            throw new ConflitoException("Já existe uma subcategoria com este nome nesta categoria.");
        }
        s.setNome(nome);
        s.setDescricao(vazioParaNulo(req.descricao()));
        return mapper.paraResponse(subcategorias.saveAndFlush(s));
    }

    public SubcategoriaResponse ativar(Long categoriaId, Long id) {
        Subcategoria s = obter(categoriaId, id);
        if (!s.getCategoria().isAtivo()) {
            throw new RegraNegocioException("Ative a categoria antes de ativar suas subcategorias.");
        }
        s.setAtivo(true);
        return mapper.paraResponse(subcategorias.saveAndFlush(s));
    }

    public SubcategoriaResponse desativar(Long categoriaId, Long id) {
        Subcategoria s = obter(categoriaId, id);
        s.setAtivo(false);
        return mapper.paraResponse(subcategorias.saveAndFlush(s));
    }

    private Categoria obterCategoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
    }

    private Subcategoria obter(Long categoriaId, Long id) {
        return subcategorias.findByIdAndCategoriaId(id, categoriaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Subcategoria não encontrada nesta categoria."));
    }

    private static String vazioParaNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
