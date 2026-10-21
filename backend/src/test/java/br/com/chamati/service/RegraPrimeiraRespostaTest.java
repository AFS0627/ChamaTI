package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoComentario;
import org.junit.jupiter.api.Test;

class RegraPrimeiraRespostaTest {

    @Test
    void tabelaVerdadeCompleta() {
        int validas = 0;
        for (Perfil perfil : Perfil.values()) {
            for (TipoComentario tipo : TipoComentario.values()) {
                for (boolean autorEhSolicitante : new boolean[]{false, true}) {
                    for (boolean jaRespondido : new boolean[]{false, true}) {
                        boolean esperado = (perfil == Perfil.TECNICO || perfil == Perfil.ADMIN)
                                && tipo == TipoComentario.PUBLICO && !autorEhSolicitante && !jaRespondido;
                        assertThat(RegraPrimeiraResposta.registra(perfil, tipo, autorEhSolicitante, jaRespondido))
                                .as("%s %s solicitante=%s respondido=%s", perfil, tipo, autorEhSolicitante, jaRespondido)
                                .isEqualTo(esperado);
                        if (esperado) {
                            validas++;
                        }
                    }
                }
            }
        }
        assertThat(validas).isEqualTo(2);   // só TECNICO e ADMIN, público, não solicitante, ainda sem resposta
    }

    @Test
    void solicitanteNuncaRegistraPrimeiraResposta() {
        for (TipoComentario tipo : TipoComentario.values()) {
            assertThat(RegraPrimeiraResposta.registra(Perfil.SOLICITANTE, tipo, true, false)).isFalse();
            assertThat(RegraPrimeiraResposta.registra(Perfil.SOLICITANTE, tipo, false, false)).isFalse();
        }
    }

    @Test
    void notaInternaNuncaContaComoResposta() {
        assertThat(RegraPrimeiraResposta.registra(Perfil.TECNICO, TipoComentario.NOTA_INTERNA, false, false)).isFalse();
        assertThat(RegraPrimeiraResposta.registra(Perfil.ADMIN, TipoComentario.NOTA_INTERNA, false, false)).isFalse();
    }
}
