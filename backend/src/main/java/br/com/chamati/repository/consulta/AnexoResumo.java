package br.com.chamati.repository.consulta;

import br.com.chamati.enums.Perfil;
import java.time.Instant;

/** Anexo sem o conteúdo binário, para listagens. */
public record AnexoResumo(Long id, String nomeArquivo, String tipoConteudo, long tamanho,
                          Long autorId, String autorNome, Perfil autorPerfil, Instant criadoEm) {
}
