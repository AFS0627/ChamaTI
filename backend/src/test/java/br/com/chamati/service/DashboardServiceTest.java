package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.DashboardCategoria;
import br.com.chamati.dto.DashboardResponse;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.consulta.ContagemCategoria;
import br.com.chamati.repository.consulta.ContagemStatusPrioridade;
import br.com.chamati.repository.consulta.ResumoOperacional;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    static final List<StatusChamado> FINAIS = List.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);

    @Mock ChamadoRepository chamados;
    DashboardService service;

    @BeforeEach
    void preparar() {
        service = new DashboardService(chamados, Fixtures.relogio());
    }

    private static Instant desde(int dias) {
        return AGORA.minus(Duration.ofDays(dias));
    }

    private static ResumoOperacional operacional() {
        return new ResumoOperacional(2, 3, 1, 9, 4, 5);
    }

    @Test
    void somaTotaisPreencheTodasAsChavesEUsaAJanelaDeDias() {
        when(chamados.contarPorStatusEPrioridade(isNull(), isNull())).thenReturn(List.of(
                new ContagemStatusPrioridade(StatusChamado.ABERTO, Prioridade.ALTA, 2),
                new ContagemStatusPrioridade(StatusChamado.ABERTO, Prioridade.MEDIA, 1),
                new ContagemStatusPrioridade(StatusChamado.EM_ATENDIMENTO, Prioridade.CRITICA, 3),
                new ContagemStatusPrioridade(StatusChamado.EM_ATENDIMENTO, Prioridade.ALTA, 1),
                new ContagemStatusPrioridade(StatusChamado.RESOLVIDO, Prioridade.BAIXA, 4),
                new ContagemStatusPrioridade(StatusChamado.FECHADO, Prioridade.ALTA, 5)));
        when(chamados.contarOperacional(isNull(), isNull(), eq(1L), eq(FINAIS), eq(AGORA), eq(desde(30))))
                .thenReturn(operacional());
        when(chamados.contarEmAbertoPorCategoria(isNull(), isNull(), eq(FINAIS), any(Pageable.class)))
                .thenReturn(List.of(new ContagemCategoria(3L, "Rede", 4), new ContagemCategoria(1L, "Hardware", 3)));

        DashboardResponse r = service.resumo(30, principal(usuario(1L, "Admin", Perfil.ADMIN)));

        assertThat(r.geradoEm()).isEqualTo(AGORA);
        assertThat(r.periodoDias()).isEqualTo(30);
        assertThat(r.totais().total()).isEqualTo(16);
        assertThat(r.totais().emAberto()).isEqualTo(7);
        assertThat(r.totais().semTecnico()).isEqualTo(2);
        assertThat(r.totais().emAtrasoResolucao()).isEqualTo(3);
        assertThat(r.totais().emAtrasoPrimeiraResposta()).isEqualTo(1);
        assertThat(r.totais().atribuidosAMim()).isEqualTo(5);

        assertThat(r.porStatus()).containsOnlyKeys(StatusChamado.values());
        assertThat(r.porStatus()).containsEntry(StatusChamado.ABERTO, 3L)
                .containsEntry(StatusChamado.EM_ATENDIMENTO, 4L)
                .containsEntry(StatusChamado.AGUARDANDO_USUARIO, 0L)
                .containsEntry(StatusChamado.AGUARDANDO_TERCEIRO, 0L)
                .containsEntry(StatusChamado.RESOLVIDO, 4L)
                .containsEntry(StatusChamado.FECHADO, 5L);

        assertThat(r.emAbertoPorPrioridade()).containsOnlyKeys(Prioridade.values());
        assertThat(r.emAbertoPorPrioridade()).containsEntry(Prioridade.CRITICA, 3L)
                .containsEntry(Prioridade.ALTA, 3L)
                .containsEntry(Prioridade.MEDIA, 1L)
                .containsEntry(Prioridade.BAIXA, 0L);

        assertThat(r.emAbertoPorCategoria()).extracting(DashboardCategoria::nome).containsExactly("Rede", "Hardware");
        assertThat(r.emAbertoPorCategoria()).extracting(DashboardCategoria::total).containsExactly(4L, 3L);
        assertThat(r.periodo().desde()).isEqualTo(desde(30));
        assertThat(r.periodo().abertos()).isEqualTo(9);
        assertThat(r.periodo().resolvidos()).isEqualTo(4);
    }

    @Test
    void solicitanteEConsultadoSomenteComOProprioId() {
        UsuarioPrincipal ana = principal(usuario(4L, "Ana Souza", Perfil.SOLICITANTE));
        when(chamados.contarPorStatusEPrioridade(eq(4L), isNull())).thenReturn(List.of());
        when(chamados.contarOperacional(eq(4L), isNull(), eq(4L), eq(FINAIS), eq(AGORA), eq(desde(30))))
                .thenReturn(new ResumoOperacional(0, 0, 0, 0, 0, 0));
        when(chamados.contarEmAbertoPorCategoria(eq(4L), isNull(), eq(FINAIS), any(Pageable.class))).thenReturn(List.of());

        service.resumo(30, ana);

        verify(chamados).contarPorStatusEPrioridade(4L, null);
    }

    @Test
    void tecnicoRecebeOEscopoDeFilaAtribuidosEProprios() {
        UsuarioPrincipal carlos = principal(usuario(2L, "Carlos Mendes", Perfil.TECNICO));
        when(chamados.contarPorStatusEPrioridade(isNull(), eq(2L))).thenReturn(List.of());
        when(chamados.contarOperacional(isNull(), eq(2L), eq(2L), eq(FINAIS), eq(AGORA), eq(desde(30))))
                .thenReturn(new ResumoOperacional(0, 0, 0, 0, 0, 0));
        when(chamados.contarEmAbertoPorCategoria(isNull(), eq(2L), eq(FINAIS), any(Pageable.class))).thenReturn(List.of());

        service.resumo(30, carlos);

        verify(chamados).contarPorStatusEPrioridade(null, 2L);
    }

    @Test
    void semChamadosTodasAsContagensSaoZeroEAsChavesContinuamPresentes() {
        when(chamados.contarPorStatusEPrioridade(isNull(), isNull())).thenReturn(List.of());
        when(chamados.contarOperacional(isNull(), isNull(), eq(1L), eq(FINAIS), eq(AGORA), eq(desde(7))))
                .thenReturn(new ResumoOperacional(0, 0, 0, 0, 0, 0));
        when(chamados.contarEmAbertoPorCategoria(isNull(), isNull(), eq(FINAIS), any(Pageable.class))).thenReturn(List.of());

        DashboardResponse r = service.resumo(7, principal(usuario(1L, "Admin", Perfil.ADMIN)));

        assertThat(r.periodoDias()).isEqualTo(7);
        assertThat(r.periodo().desde()).isEqualTo(desde(7));
        assertThat(r.totais().total()).isZero();
        assertThat(r.totais().emAberto()).isZero();
        assertThat(r.porStatus()).hasSize(StatusChamado.values().length).containsValue(0L).doesNotContainValue(1L);
        assertThat(r.emAbertoPorPrioridade()).hasSize(Prioridade.values().length);
        assertThat(r.emAbertoPorCategoria()).isEmpty();
    }

    @Test
    void limitaAsCategoriasADez() {
        when(chamados.contarPorStatusEPrioridade(isNull(), isNull())).thenReturn(List.of());
        when(chamados.contarOperacional(isNull(), isNull(), eq(1L), eq(FINAIS), eq(AGORA), eq(desde(30))))
                .thenReturn(new ResumoOperacional(0, 0, 0, 0, 0, 0));
        when(chamados.contarEmAbertoPorCategoria(isNull(), isNull(), eq(FINAIS), any(Pageable.class))).thenReturn(List.of());

        service.resumo(30, principal(usuario(1L, "Admin", Perfil.ADMIN)));

        ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);
        verify(chamados).contarEmAbertoPorCategoria(isNull(), isNull(), eq(FINAIS), limite.capture());
        assertThat(limite.getValue().getPageNumber()).isZero();
        assertThat(limite.getValue().getPageSize()).isEqualTo(10);
    }
}
