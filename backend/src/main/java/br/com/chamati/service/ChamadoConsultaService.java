package br.com.chamati.service;

import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.entity.Chamado;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.ChamadoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.repository.consulta.ChamadoResumo;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Clock;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visibilidade: ADMIN vê todos; SOLICITANTE, só os que abriu; TECNICO, os atribuídos a ele,
 * os ainda sem técnico (fila) e os que abriu. Fora da visibilidade, o detalhe responde 404.
 * A listagem e o detalhe usam a mesma regra.
 */
@Service
@Transactional(readOnly = true)
public class ChamadoConsultaService {

    private static final Set<String> CAMPOS_ORDENAVEIS = Set.of("criadoEm", "atualizadoEm", "protocolo", "titulo");

    private final ChamadoRepository chamados;
    private final ChamadoMapper mapper;
    private final Clock clock;

    public ChamadoConsultaService(ChamadoRepository chamados, ChamadoMapper mapper, Clock clock) {
        this.chamados = chamados;
        this.mapper = mapper;
        this.clock = clock;
    }

    public PaginaResponse<ChamadoResumo> listar(ChamadoFiltro filtro, String ordenarPor, String direcao,
                                                int pagina, int tamanho, UsuarioPrincipal atual) {
        if (filtro.criadoDe() != null && filtro.criadoAte() != null && filtro.criadoDe().isAfter(filtro.criadoAte())) {
            throw new RegraNegocioException("O início do período não pode ser posterior ao fim.");
        }
        String campo = ordenarPor == null ? "criadoEm" : ordenarPor;
        if (!CAMPOS_ORDENAVEIS.contains(campo)) {
            throw new RegraNegocioException("Campo de ordenação inválido.");
        }
        Sort.Direction sentido = direcao == null ? Sort.Direction.DESC : Sort.Direction.fromString(direcao);
        Sort sort = Sort.by(sentido, campo).and(Sort.by(Sort.Direction.DESC, "id"));
        return PaginaResponse.de(chamados.buscar(restringir(filtro, atual), PageRequest.of(pagina, tamanho, sort)));
    }

    public ChamadoResponse buscar(Long id, UsuarioPrincipal atual) {
        Chamado chamado = chamados.findDetalheById(id)
                .filter(c -> visivel(c, atual))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
        return mapper.paraResponse(chamado, clock.instant());
    }

    private static ChamadoFiltro restringir(ChamadoFiltro filtro, UsuarioPrincipal atual) {
        return switch (atual.perfil()) {
            case ADMIN -> filtro;
            case TECNICO -> filtro.comEscopoTecnico(atual.id());
            case SOLICITANTE -> filtro.comSolicitante(atual.id());
        };
    }

    private static boolean visivel(Chamado c, UsuarioPrincipal atual) {
        return switch (atual.perfil()) {
            case ADMIN -> true;
            case SOLICITANTE -> c.getSolicitante().getId().equals(atual.id());
            case TECNICO -> c.getTecnico() == null
                    || c.getTecnico().getId().equals(atual.id())
                    || c.getSolicitante().getId().equals(atual.id());
        };
    }
}
