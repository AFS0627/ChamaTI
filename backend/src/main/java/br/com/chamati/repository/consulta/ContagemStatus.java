package br.com.chamati.repository.consulta;

import br.com.chamati.enums.StatusChamado;

public record ContagemStatus(StatusChamado status, long total) {
}
