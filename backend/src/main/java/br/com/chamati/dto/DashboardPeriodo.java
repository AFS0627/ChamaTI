package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DashboardPeriodo(
        @Schema(description = "Início da janela (agora menos o número de dias pedido).") Instant desde,
        @Schema(description = "Chamados criados desde o início da janela.") long abertos,
        @Schema(description = "Chamados resolvidos ou fechados desde o início da janela (considera a data de resolução atual; chamados reabertos não entram).") long resolvidos) {
}
