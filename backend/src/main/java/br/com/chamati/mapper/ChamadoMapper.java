package br.com.chamati.mapper;

import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.ReferenciaResponse;
import br.com.chamati.entity.Categoria;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Subcategoria;
import br.com.chamati.entity.Usuario;
import br.com.chamati.service.SlaCalculadora;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ChamadoMapper {

    public ChamadoResponse paraResponse(Chamado c) {
        return paraResponse(c, Instant.now());
    }

    public ChamadoResponse paraResponse(Chamado c, Instant agora) {
        return new ChamadoResponse(c.getId(), c.getProtocolo(), c.getTitulo(), c.getDescricao(),
                c.getPrioridade(), c.getStatus(),
                ref(c.getCategoria()), ref(c.getSubcategoria()), ref(c.getSolicitante()), ref(c.getTecnico()),
                c.getCriadoEm(), c.getAtualizadoEm(), c.getAtribuidoEm(), c.getPrimeiraRespostaEm(),
                c.getResolvidoEm(), c.getFechadoEm(), c.getReabertoEm(), c.getTotalReaberturas(), c.getSolucao(),
                c.getSlaPrimeiraRespostaLimite(), c.getSlaResolucaoLimite(),
                SlaCalculadora.situacao(c.getSlaPrimeiraRespostaLimite(), c.getPrimeiraRespostaEm(), agora),
                SlaCalculadora.situacao(c.getSlaResolucaoLimite(), c.getResolvidoEm(), agora),
                c.getVersao());
    }

    private static ReferenciaResponse ref(Categoria c) {
        return c == null ? null : new ReferenciaResponse(c.getId(), c.getNome());
    }

    private static ReferenciaResponse ref(Subcategoria s) {
        return s == null ? null : new ReferenciaResponse(s.getId(), s.getNome());
    }

    private static ReferenciaResponse ref(Usuario u) {
        return u == null ? null : new ReferenciaResponse(u.getId(), u.getNome());
    }
}
