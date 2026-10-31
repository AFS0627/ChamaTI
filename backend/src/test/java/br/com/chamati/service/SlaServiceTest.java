package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.chamado;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.SituacaoSla;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.repository.PoliticaSlaRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @Mock PoliticaSlaRepository politicas;
    SlaService service;

    @BeforeEach
    void preparar() {
        service = new SlaService(politicas);
    }

    private static PoliticaSla politica(Prioridade prioridade, int primeiraResposta, int resolucao) {
        PoliticaSla p = new PoliticaSla();
        p.setPrioridade(prioridade);
        p.setPrazoPrimeiraRespostaMin(primeiraResposta);
        p.setPrazoResolucaoMin(resolucao);
        return p;
    }

    private static Chamado novoChamado(Prioridade prioridade) {
        Chamado c = chamado(1L, usuario(4L, "Ana Souza", Perfil.SOLICITANTE), null, StatusChamado.ABERTO);
        c.setPrioridade(prioridade);
        c.setCriadoEm(AGORA);
        return c;
    }

    @Test
    void calculaLimitesSomandoOsPrazosEmMinutosCorridosAoCriadoEm() {
        when(politicas.findByPrioridadeAndAtivoTrue(Prioridade.ALTA)).thenReturn(Optional.of(politica(Prioridade.ALTA, 30, 480)));

        SlaService.Limites l = service.calcularLimites(Prioridade.ALTA, AGORA);

        assertThat(l.primeiraResposta()).isEqualTo(AGORA.plus(Duration.ofMinutes(30)));
        assertThat(l.resolucao()).isEqualTo(AGORA.plus(Duration.ofMinutes(480)));
    }

    @Test
    void semPoliticaAtivaParaAPrioridadeLancaRegraDeNegocio() {
        when(politicas.findByPrioridadeAndAtivoTrue(Prioridade.CRITICA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularLimites(Prioridade.CRITICA, AGORA))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CRITICA");
    }

    @Test
    void aplicarLimitesPreencheOsDoisLimitesDoChamado() {
        when(politicas.findByPrioridadeAndAtivoTrue(Prioridade.MEDIA)).thenReturn(Optional.of(politica(Prioridade.MEDIA, 60, 1440)));
        Chamado c = novoChamado(Prioridade.MEDIA);

        service.aplicarLimites(c);

        assertThat(c.getSlaPrimeiraRespostaLimite()).isEqualTo(AGORA.plusSeconds(60 * 60));
        assertThat(c.getSlaResolucaoLimite()).isEqualTo(AGORA.plusSeconds(1440L * 60));
    }

    @Test
    void mudarAPrioridadeRecalculaSempreAPartirDoCriadoEm() {
        when(politicas.findByPrioridadeAndAtivoTrue(Prioridade.MEDIA)).thenReturn(Optional.of(politica(Prioridade.MEDIA, 60, 1440)));
        when(politicas.findByPrioridadeAndAtivoTrue(Prioridade.CRITICA)).thenReturn(Optional.of(politica(Prioridade.CRITICA, 15, 120)));
        Chamado c = novoChamado(Prioridade.MEDIA);
        service.aplicarLimites(c);

        c.setPrioridade(Prioridade.CRITICA);
        service.aplicarLimites(c);

        assertThat(c.getSlaPrimeiraRespostaLimite()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
        assertThat(c.getSlaResolucaoLimite()).isEqualTo(AGORA.plus(Duration.ofMinutes(120)));
    }

    @Test
    void aplicarLimitesExigeCriadoEmDefinido() {
        Chamado c = novoChamado(Prioridade.MEDIA);
        c.setCriadoEm(null);

        assertThatThrownBy(() -> service.aplicarLimites(c)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void situacoesDeSlaUsamOsLimitesEAsDatasRealizadasDoChamado() {
        Chamado c = novoChamado(Prioridade.MEDIA);
        c.setSlaPrimeiraRespostaLimite(AGORA.plusSeconds(600));
        c.setSlaResolucaoLimite(AGORA.minusSeconds(600));

        assertThat(service.situacaoPrimeiraResposta(c, AGORA)).isEqualTo(SituacaoSla.EM_ANDAMENTO);
        assertThat(service.situacaoResolucao(c, AGORA)).isEqualTo(SituacaoSla.ATRASADO);

        c.setPrimeiraRespostaEm(AGORA.plusSeconds(300));
        c.setResolvidoEm(AGORA.minusSeconds(900));
        assertThat(service.situacaoPrimeiraResposta(c, AGORA)).isEqualTo(SituacaoSla.DENTRO_DO_PRAZO);
        assertThat(service.situacaoResolucao(c, AGORA)).isEqualTo(SituacaoSla.DENTRO_DO_PRAZO);
    }

    @Test
    void chamadoSemLimitesFicaSemSla() {
        Chamado c = novoChamado(Prioridade.BAIXA);

        assertThat(service.situacaoResolucao(c, AGORA)).isEqualTo(SituacaoSla.SEM_SLA);
    }
}
