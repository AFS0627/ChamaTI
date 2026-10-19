package br.com.chamati.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHashTest {

    @Test
    void sha256ConfereComVetorConhecido() {
        assertThat(TokenHash.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void temSempre64CaracteresHexadecimais() {
        assertThat(TokenHash.sha256Hex("qualquer-token")).hasSize(64).matches("[0-9a-f]{64}");
    }
}
