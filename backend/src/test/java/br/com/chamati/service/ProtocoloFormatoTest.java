package br.com.chamati.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProtocoloFormatoTest {

    @Test
    void formataAnoENumeroComSeisDigitos() {
        assertThat(ProtocoloFormato.formatar(2026, 1)).isEqualTo("CT-2026-000001");
        assertThat(ProtocoloFormato.formatar(2026, 13)).isEqualTo("CT-2026-000013");
        assertThat(ProtocoloFormato.formatar(2027, 123456)).isEqualTo("CT-2027-123456");
    }

    @Test
    void numeroMaiorQueSeisDigitosAindaCabeNaColuna() {
        String p = ProtocoloFormato.formatar(2026, 1_000_000);
        assertThat(p).isEqualTo("CT-2026-1000000").hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void rejeitaValoresInvalidos() {
        assertThatThrownBy(() -> ProtocoloFormato.formatar(2026, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProtocoloFormato.formatar(2026, -5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProtocoloFormato.formatar(99, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
