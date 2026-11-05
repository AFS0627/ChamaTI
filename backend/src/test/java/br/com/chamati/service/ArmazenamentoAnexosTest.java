package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chamati.config.AnexoProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class ArmazenamentoAnexosTest {

    @TempDir
    Path dir;

    ArmazenamentoAnexos armazenamento;

    @BeforeEach
    void preparar() {
        armazenamento = new ArmazenamentoAnexos(new AnexoProperties(dir.resolve("anexos").toString(),
                DataSize.ofMegabytes(5), 10));
    }

    @Test
    void salvaELeOsMesmosBytesCriandoODiretorio() throws Exception {
        byte[] bytes = {1, 2, 3, 4};

        String chave = armazenamento.salvar(bytes);

        assertThat(dir.resolve("anexos")).isDirectory();
        assertThat(Files.readAllBytes(dir.resolve("anexos").resolve(chave))).isEqualTo(bytes);
        assertThat(armazenamento.ler(chave)).contains(bytes);
    }

    @Test
    void cadaArquivoRecebeUmaChaveDiferente() {
        assertThat(armazenamento.salvar(new byte[]{1})).isNotEqualTo(armazenamento.salvar(new byte[]{1}));
    }

    @Test
    void chaveInexistenteNaoTemConteudoERemoverEIdempotente() {
        assertThat(armazenamento.ler("nao-existe")).isEmpty();
        armazenamento.remover("nao-existe");
    }

    @Test
    void removeOArquivo() {
        String chave = armazenamento.salvar(new byte[]{9});

        armazenamento.remover(chave);

        assertThat(armazenamento.ler(chave)).isEmpty();
    }

    @Test
    void recusaChavesQueSaemDoDiretorio() {
        assertThatThrownBy(() -> armazenamento.ler("../segredo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> armazenamento.ler("sub/arquivo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> armazenamento.ler("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> armazenamento.remover("../segredo")).isInstanceOf(IllegalArgumentException.class);
    }
}
