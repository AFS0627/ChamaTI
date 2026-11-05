package br.com.chamati.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.anexos")
public record AnexoProperties(
        @DefaultValue("./anexos") String diretorio,
        @DefaultValue("5MB") DataSize tamanhoMaximo,
        @DefaultValue("10") int maximoPorChamado) {

    public AnexoProperties {
        if (diretorio == null || diretorio.isBlank()) {
            throw new IllegalArgumentException("app.anexos.diretorio (ANEXOS_DIR) não pode ser vazio.");
        }
        if (tamanhoMaximo.toBytes() <= 0 || maximoPorChamado < 1) {
            throw new IllegalArgumentException("tamanho-maximo e maximo-por-chamado devem ser positivos.");
        }
    }
}
