package br.com.chamati.repository;

import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.enums.Prioridade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoliticaSlaRepository extends JpaRepository<PoliticaSla, Long> {

    Optional<PoliticaSla> findByPrioridade(Prioridade prioridade);

    Optional<PoliticaSla> findByPrioridadeAndAtivoTrue(Prioridade prioridade);

    List<PoliticaSla> findByAtivoTrue();
}
