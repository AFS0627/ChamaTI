package br.com.chamati.service;

import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.SituacaoSla;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.repository.PoliticaSlaRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaService {

    public record Limites(Instant primeiraResposta, Instant resolucao) {
    }

    private final PoliticaSlaRepository politicas;

    public SlaService(PoliticaSlaRepository politicas) {
        this.politicas = politicas;
    }

    /** Limites a partir de criado_em com os prazos da prioridade informada. */
    @Transactional(readOnly = true)
    public Limites calcularLimites(Prioridade prioridade, Instant criadoEm) {
        PoliticaSla p = politicas.findByPrioridadeAndAtivoTrue(prioridade)
                .orElseThrow(() -> new RegraNegocioException(
                        "Não há política de SLA ativa para a prioridade " + prioridade + "."));
        return new Limites(SlaCalculadora.limite(criadoEm, p.getPrazoPrimeiraRespostaMin()),
                SlaCalculadora.limite(criadoEm, p.getPrazoResolucaoMin()));
    }

    /**
     * Define os limites do chamado. Usar na criação e a cada mudança de prioridade: o cálculo
     * sempre parte de criado_em (o prazo não reinicia no momento da mudança). Exige criadoEm definido.
     */
    @Transactional
    public void aplicarLimites(Chamado chamado) {
        if (chamado.getCriadoEm() == null) {
            throw new IllegalStateException("criadoEm deve ser definido antes de calcular o SLA.");
        }
        Limites l = calcularLimites(chamado.getPrioridade(), chamado.getCriadoEm());
        chamado.setSlaPrimeiraRespostaLimite(l.primeiraResposta());
        chamado.setSlaResolucaoLimite(l.resolucao());
    }

    public SituacaoSla situacaoPrimeiraResposta(Chamado c, Instant agora) {
        return SlaCalculadora.situacao(c.getSlaPrimeiraRespostaLimite(), c.getPrimeiraRespostaEm(), agora);
    }

    public SituacaoSla situacaoResolucao(Chamado c, Instant agora) {
        return SlaCalculadora.situacao(c.getSlaResolucaoLimite(), c.getResolvidoEm(), agora);
    }
}
