package br.com.chamati.service;

import br.com.chamati.dto.CategoriaRequest;
import br.com.chamati.dto.CategoriaResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.entity.Categoria;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.mapper.CatalogoMapper;
import br.com.chamati.repository.CategoriaRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoriaService {

    private final CategoriaRepository categorias;
    private final CatalogoMapper mapper;

    public CategoriaService(CategoriaRepository categorias, CatalogoMapper mapper) {
        this.categorias = categorias;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CategoriaResponse> listar(Boolean ativo, String termo, int pagina, int tamanho) {
        var pageable = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id")));
        return PaginaResponse.de(categorias.buscar(ativo, vazioParaNulo(termo), pageable).map(mapper::paraResponse));
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarAtivas() {
        return categorias.findByAtivoTrueOrderByNomeAsc().stream().map(mapper::paraResponse).toList();
    }

    /** Categorias inativas só são visíveis para ADMIN (para os demais, "não encontrada"). */
    @Transactional(readOnly = true)
    public CategoriaResponse buscar(Long id, boolean admin) {
        return mapper.paraResponse(obterVisivel(id, admin));
    }

    public CategoriaResponse criar(CategoriaRequest req) {
        String nome = req.nome().trim();
        if (categorias.existsByNome(nome)) {
            throw new ConflitoException("Já existe uma categoria com este nome.");
        }
        Categoria c = new Categoria();
        c.setNome(nome);
        c.setDescricao(vazioParaNulo(req.descricao()));
        c.setAtivo(true);
        return mapper.paraResponse(categorias.saveAndFlush(c));
    }

    public CategoriaResponse atualizar(Long id, CategoriaRequest req) {
        Categoria c = obter(id);
        String nome = req.nome().trim();
        if (categorias.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Já existe uma categoria com este nome.");
        }
        c.setNome(nome);
        c.setDescricao(vazioParaNulo(req.descricao()));
        return mapper.paraResponse(categorias.saveAndFlush(c));
    }

    public CategoriaResponse ativar(Long id) {
        return alterarSituacao(id, true);
    }

    /** As subcategorias permanecem como estão, mas ficam indisponíveis enquanto a categoria estiver inativa. */
    public CategoriaResponse desativar(Long id) {
        return alterarSituacao(id, false);
    }

    private CategoriaResponse alterarSituacao(Long id, boolean ativo) {
        Categoria c = obter(id);
        c.setAtivo(ativo);
        return mapper.paraResponse(categorias.saveAndFlush(c));
    }

    private Categoria obter(Long id) {
        return categorias.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
    }

    private Categoria obterVisivel(Long id, boolean admin) {
        return categorias.findById(id)
                .filter(c -> admin || c.isAtivo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
    }

    private static String vazioParaNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
