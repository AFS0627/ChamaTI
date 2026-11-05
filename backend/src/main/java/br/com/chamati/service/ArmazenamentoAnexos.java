package br.com.chamati.service;

import br.com.chamati.config.AnexoProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Grava os bytes em um diretório, com nome gerado (UUID): o nome enviado pelo usuário nunca chega ao sistema de arquivos. */
@Component
public class ArmazenamentoAnexos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoAnexos.class);

    private final Path raiz;

    public ArmazenamentoAnexos(AnexoProperties props) {
        this.raiz = Path.of(props.diretorio()).toAbsolutePath().normalize();
    }

    public String salvar(byte[] conteudo) {
        String chave = UUID.randomUUID().toString();
        try {
            Files.createDirectories(raiz);
            Files.write(resolver(chave), conteudo, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar o anexo.", e);
        }
        return chave;
    }

    public Optional<byte[]> ler(String chave) {
        Path arquivo = resolver(chave);
        try {
            return Files.isRegularFile(arquivo) ? Optional.of(Files.readAllBytes(arquivo)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o anexo.", e);
        }
    }

    public void remover(String chave) {
        try {
            Files.deleteIfExists(resolver(chave));
        } catch (IOException e) {
            log.warn("Não foi possível remover o arquivo de anexo {}: {}", chave, e.getMessage());
        }
    }

    private Path resolver(String chave) {
        Path arquivo = raiz.resolve(chave).normalize();
        if (!raiz.equals(arquivo.getParent())) {
            throw new IllegalArgumentException("Chave de anexo inválida.");
        }
        return arquivo;
    }
}
