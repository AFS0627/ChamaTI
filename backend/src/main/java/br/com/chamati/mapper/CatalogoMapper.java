package br.com.chamati.mapper;

import br.com.chamati.dto.CategoriaResponse;
import br.com.chamati.dto.PoliticaSlaResponse;
import br.com.chamati.dto.SubcategoriaResponse;
import br.com.chamati.entity.Categoria;
import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.entity.Subcategoria;
import org.springframework.stereotype.Component;

@Component
public class CatalogoMapper {

    public CategoriaResponse paraResponse(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNome(), c.getDescricao(), c.isAtivo());
    }

    public SubcategoriaResponse paraResponse(Subcategoria s) {
        return new SubcategoriaResponse(s.getId(), s.getCategoria().getId(), s.getNome(),
                s.getDescricao(), s.isAtivo());
    }

    public PoliticaSlaResponse paraResponse(PoliticaSla p) {
        return new PoliticaSlaResponse(p.getPrioridade(), p.getPrazoPrimeiraRespostaMin(), p.getPrazoResolucaoMin());
    }
}
