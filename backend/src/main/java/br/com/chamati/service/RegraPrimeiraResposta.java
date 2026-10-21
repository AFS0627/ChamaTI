package br.com.chamati.service;

import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoComentario;

/**
 * A primeira resposta é registrada uma única vez, no primeiro comentário PÚBLICO de TECNICO ou ADMIN que
 * não seja o próprio solicitante do chamado (quem abriu o chamado não responde a si mesmo).
 */
public final class RegraPrimeiraResposta {

    private RegraPrimeiraResposta() {
    }

    public static boolean registra(Perfil perfilAutor, TipoComentario tipo, boolean autorEhSolicitante,
                                   boolean jaRespondido) {
        return !jaRespondido
                && tipo == TipoComentario.PUBLICO
                && !autorEhSolicitante
                && (perfilAutor == Perfil.TECNICO || perfilAutor == Perfil.ADMIN);
    }
}
