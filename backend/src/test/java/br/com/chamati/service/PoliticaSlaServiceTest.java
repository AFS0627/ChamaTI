package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.chamati.dto.PoliticaSlaRequest;
import br.com.chamati.dto.PoliticaSlaResponse;
import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.CatalogoMapper;
import br.com.chamati.repository.PoliticaSlaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PoliticaSlaServiceTest {

    @Mock PoliticaSlaRepository politicas;
    PoliticaSlaService service;

    @BeforeEach
    void preparar() {
        service = new PoliticaSlaService(politicas, new CatalogoMapper());
    }

    private static PoliticaSla politica(Prioridade prioridade, int primeira, int resolucao, boolean ativo) {
        PoliticaSla p = new PoliticaSla();
        p.setPrioridade(prioridade);
        p.setPrazoPrimeiraRespostaMin(primeira);
        p.setPrazoResolucaoMin(resolucao);
        p.setAtivo(ativo);
        return p;
    }

    @Test
    void listaDaMaisUrgenteParaAMenosUrgente() {
        when(politicas.findAll()).thenReturn(List.of(
                politica(Prioridade.BAIXA, 480, 4320, true), politica(Prioridade.CRITICA, 15, 120, true),
                politica(Prioridade.MEDIA, 60, 1440, true), politica(Prioridade.ALTA, 30, 480, true)));

        assertThat(service.listar()).extracting(PoliticaSlaResponse::prioridade)
                .containsExactly(Prioridade.CRITICA, Prioridade.ALTA, Prioridade.MEDIA, Prioridade.BAIXA);
    }

    @Test
    void atualizaPrazosEReativaAPoliticaExistente() {
        PoliticaSla existente = politica(Prioridade.ALTA, 30, 480, false);
        when(politicas.findByPrioridade(Prioridade.ALTA)).thenReturn(Optional.of(existente));
        when(politicas.saveAndFlush(existente)).thenReturn(existente);

        PoliticaSlaResponse r = service.atualizar(Prioridade.ALTA, new PoliticaSlaRequest(20, 300));

        assertThat(r).isEqualTo(new PoliticaSlaResponse(Prioridade.ALTA, 20, 300));
        assertThat(existente.isAtivo()).isTrue();
    }

    @Test
    void criaAPoliticaQuandoAindaNaoExisteParaAPrioridade() {
        when(politicas.findByPrioridade(Prioridade.BAIXA)).thenReturn(Optional.empty());
        when(politicas.saveAndFlush(any(PoliticaSla.class))).thenAnswer(inv -> inv.getArgument(0));

        PoliticaSlaResponse r = service.atualizar(Prioridade.BAIXA, new PoliticaSlaRequest(480, 4320));

        assertThat(r.prioridade()).isEqualTo(Prioridade.BAIXA);
        assertThat(r.prazoResolucaoMin()).isEqualTo(4320);
    }

    @Test
    void recusaResolucaoMenorQueAPrimeiraResposta() {
        assertThatThrownBy(() -> service.atualizar(Prioridade.ALTA, new PoliticaSlaRequest(300, 120)))
                .isInstanceOf(RegraNegocioException.class);

        verify(politicas, never()).saveAndFlush(any());
    }

    @Test
    void aceitaResolucaoIgualAPrimeiraResposta() {
        when(politicas.findByPrioridade(Prioridade.ALTA)).thenReturn(Optional.empty());
        when(politicas.saveAndFlush(any(PoliticaSla.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.atualizar(Prioridade.ALTA, new PoliticaSlaRequest(120, 120)).prazoResolucaoMin())
                .isEqualTo(120);
    }
}
