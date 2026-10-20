package br.com.chamati.service;

import br.com.chamati.dto.PoliticaSlaRequest;
import br.com.chamati.dto.PoliticaSlaResponse;
import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.CatalogoMapper;
import br.com.chamati.repository.PoliticaSlaRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uma política por prioridade, sem histórico de versões. Alterar prazos vale para chamados novos
 * e para recálculos futuros; os limites já gravados em chamados existentes não mudam.
 */
@Service
@Transactional
public class PoliticaSlaService {

    private final PoliticaSlaRepository politicas;
    private final CatalogoMapper mapper;

    public PoliticaSlaService(PoliticaSlaRepository politicas, CatalogoMapper mapper) {
        this.politicas = politicas;
        this.mapper = mapper;
    }

    /** Da mais urgente (CRITICA) para a menos urgente (BAIXA). */
    @Transactional(readOnly = true)
    public List<PoliticaSlaResponse> listar() {
        return politicas.findAll().stream()
                .sorted(Comparator.comparing(PoliticaSla::getPrioridade).reversed())
                .map(mapper::paraResponse)
                .toList();
    }

    public PoliticaSlaResponse atualizar(Prioridade prioridade, PoliticaSlaRequest req) {
        if (req.prazoResolucaoMin() < req.prazoPrimeiraRespostaMin()) {
            throw new RegraNegocioException(
                    "O prazo de resolução não pode ser menor que o prazo de primeira resposta.");
        }
        PoliticaSla p = politicas.findByPrioridade(prioridade).orElseGet(() -> {
            PoliticaSla nova = new PoliticaSla();
            nova.setPrioridade(prioridade);
            return nova;
        });
        p.setPrazoPrimeiraRespostaMin(req.prazoPrimeiraRespostaMin());
        p.setPrazoResolucaoMin(req.prazoResolucaoMin());
        p.setAtivo(true);
        return mapper.paraResponse(politicas.saveAndFlush(p));
    }
}
