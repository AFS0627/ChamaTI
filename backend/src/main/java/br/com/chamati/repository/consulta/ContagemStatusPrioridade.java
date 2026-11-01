package br.com.chamati.repository.consulta;

import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;

public record ContagemStatusPrioridade(StatusChamado status, Prioridade prioridade, long total) {
}
