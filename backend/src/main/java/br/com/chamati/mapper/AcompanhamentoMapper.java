package br.com.chamati.mapper;

import br.com.chamati.dto.AutorResponse;
import br.com.chamati.dto.ComentarioResponse;
import br.com.chamati.dto.HistoricoResponse;
import br.com.chamati.entity.Comentario;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import org.springframework.stereotype.Component;

@Component
public class AcompanhamentoMapper {

    public ComentarioResponse paraResponse(Comentario c) {
        return new ComentarioResponse(c.getId(), c.getTipo(), c.getConteudo(), autor(c.getAutor()), c.getCriadoEm());
    }

    public HistoricoResponse paraResponse(HistoricoChamado h) {
        return new HistoricoResponse(h.getId(), h.getTipoEvento(), h.getCampo(), h.getValorAnterior(),
                h.getValorNovo(), h.getDescricao(), autor(h.getAutor()), h.getCriadoEm());
    }

    private static AutorResponse autor(Usuario u) {
        return new AutorResponse(u.getId(), u.getNome(), u.getPerfil());
    }
}
