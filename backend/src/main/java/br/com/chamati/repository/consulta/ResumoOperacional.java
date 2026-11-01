package br.com.chamati.repository.consulta;

/** Contagens do painel calculadas em uma única consulta (todas respeitam a visibilidade do usuário). */
public record ResumoOperacional(
        long semTecnico,                // em aberto e sem técnico (fila)
        long emAtrasoResolucao,         // em aberto com o limite de resolução já vencido
        long emAtrasoPrimeiraResposta,  // em aberto, sem primeira resposta e com o limite já vencido
        long abertosNoPeriodo,          // criados desde o início do período
        long resolvidosNoPeriodo,       // com resolvido_em desde o início do período
        long atribuidosAMim) {          // em aberto e atribuídos ao usuário
}
