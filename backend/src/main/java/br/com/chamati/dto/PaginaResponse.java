package br.com.chamati.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record PaginaResponse<T>(@Schema(description = "Itens da página.") List<T> conteudo, @Schema(description = "Número da página, começando em 0.") int pagina, @Schema(description = "Itens por página solicitados.") int tamanho, @Schema(description = "Total de itens que atendem aos filtros.") long totalElementos, @Schema(description = "Total de páginas.") int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
