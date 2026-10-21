package br.com.chamati.service;

import static br.com.chamati.enums.StatusChamado.ABERTO;
import static br.com.chamati.enums.StatusChamado.AGUARDANDO_TERCEIRO;
import static br.com.chamati.enums.StatusChamado.AGUARDANDO_USUARIO;
import static br.com.chamati.enums.StatusChamado.EM_ATENDIMENTO;
import static br.com.chamati.enums.StatusChamado.FECHADO;
import static br.com.chamati.enums.StatusChamado.RESOLVIDO;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.enums.StatusChamado;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MaquinaEstadosChamadoTest {

    record Par(StatusChamado de, StatusChamado para) {
    }

    static final Set<Par> ALTERACOES_PERMITIDAS = Set.of(
            new Par(ABERTO, EM_ATENDIMENTO),
            new Par(EM_ATENDIMENTO, AGUARDANDO_USUARIO),
            new Par(EM_ATENDIMENTO, AGUARDANDO_TERCEIRO),
            new Par(AGUARDANDO_USUARIO, EM_ATENDIMENTO),
            new Par(AGUARDANDO_USUARIO, AGUARDANDO_TERCEIRO),
            new Par(AGUARDANDO_TERCEIRO, EM_ATENDIMENTO),
            new Par(AGUARDANDO_TERCEIRO, AGUARDANDO_USUARIO));

    @Test
    void alteracaoSimplesDeStatusSegueExatamenteATabelaDePermitidas() {
        for (StatusChamado de : StatusChamado.values()) {
            for (StatusChamado para : StatusChamado.values()) {
                assertThat(MaquinaEstadosChamado.podeAlterarStatus(de, para))
                        .as("%s -> %s", de, para)
                        .isEqualTo(ALTERACOES_PERMITIDAS.contains(new Par(de, para)));
            }
        }
    }

    @Test
    void alteracaoSimplesNuncaLevaAResolvidoFechadoOuAbertoNemSaiDeEstadosFinais() {
        for (StatusChamado de : StatusChamado.values()) {
            assertThat(MaquinaEstadosChamado.podeAlterarStatus(de, RESOLVIDO)).isFalse();
            assertThat(MaquinaEstadosChamado.podeAlterarStatus(de, FECHADO)).isFalse();
            assertThat(MaquinaEstadosChamado.podeAlterarStatus(de, ABERTO)).isFalse();
        }
        for (StatusChamado para : StatusChamado.values()) {
            assertThat(MaquinaEstadosChamado.podeAlterarStatus(RESOLVIDO, para)).isFalse();
            assertThat(MaquinaEstadosChamado.podeAlterarStatus(FECHADO, para)).isFalse();
        }
    }

    @Test
    void resolverSoAPartirDeAtendimentoOuEspera() {
        Set<StatusChamado> permitidos = EnumSet.of(EM_ATENDIMENTO, AGUARDANDO_USUARIO, AGUARDANDO_TERCEIRO);
        for (StatusChamado s : StatusChamado.values()) {
            assertThat(MaquinaEstadosChamado.podeResolver(s)).as(s.name()).isEqualTo(permitidos.contains(s));
        }
    }

    @Test
    void fecharSoAPartirDeResolvido() {
        for (StatusChamado s : StatusChamado.values()) {
            assertThat(MaquinaEstadosChamado.podeFechar(s)).as(s.name()).isEqualTo(s == RESOLVIDO);
        }
    }

    @Test
    void reabrirSoAPartirDeResolvidoOuFechado() {
        for (StatusChamado s : StatusChamado.values()) {
            assertThat(MaquinaEstadosChamado.podeReabrir(s)).as(s.name())
                    .isEqualTo(List.of(RESOLVIDO, FECHADO).contains(s));
        }
    }

    @Test
    void atribuirSoEmChamadosNaoResolvidos() {
        for (StatusChamado s : StatusChamado.values()) {
            assertThat(MaquinaEstadosChamado.podeAtribuir(s)).as(s.name())
                    .isEqualTo(s != RESOLVIDO && s != FECHADO);
        }
    }

    @Test
    void reaberturaVoltaParaAtendimentoOuAbertoSemTecnico() {
        assertThat(MaquinaEstadosChamado.statusAposReabertura(true)).isEqualTo(EM_ATENDIMENTO);
        assertThat(MaquinaEstadosChamado.statusAposReabertura(false)).isEqualTo(ABERTO);
    }

    @Test
    void soChamadosFechadosNaoRecebemComentarios() {
        for (StatusChamado s : StatusChamado.values()) {
            assertThat(MaquinaEstadosChamado.podeComentar(s)).as(s.name()).isEqualTo(s != FECHADO);
        }
    }
}
