package br.com.chamati.dto;

import br.com.chamati.enums.Prioridade;

public record PoliticaSlaResponse(Prioridade prioridade, int prazoPrimeiraRespostaMin, int prazoResolucaoMin) {
}
