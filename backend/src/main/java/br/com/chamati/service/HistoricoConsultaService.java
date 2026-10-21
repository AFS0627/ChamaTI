package br.com.chamati.service;

import br.com.chamati.dto.HistoricoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.security.UsuarioPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Histórico do chamado visível ao usuário. O SOLICITANTE não recebe os eventos de nota interna. */
@Service
@Transactional(readOnly = true)
public class HistoricoConsultaService {

    private final ChamadoRepository chamados;
    private final HistoricoChamadoRepository historico;
    private final AcompanhamentoMapper mapper;

    public HistoricoConsultaService(ChamadoRepository chamados, HistoricoChamadoRepository historico,
                                    AcompanhamentoMapper mapper) {
        this.chamados = chamados;
        this.historico = historico;
        this.mapper = mapper;
    }

    public PaginaResponse<HistoricoResponse> listar(Long chamadoId, String direcao, int pagina, int tamanho,
                                                    UsuarioPrincipal ator) {
        chamados.findDetalheById(chamadoId)
                .filter(c -> ChamadoConsultaService.visivel(c, ator))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));

        Sort.Direction sentido = direcao == null ? Sort.Direction.ASC : Sort.Direction.fromString(direcao);
        PageRequest pageable = PageRequest.of(pagina, tamanho,
                Sort.by(sentido, "criadoEm").and(Sort.by(sentido, "id")));
        var resultado = ator.perfil() == Perfil.SOLICITANTE
                ? historico.findByChamadoIdAndTipoEventoNot(chamadoId, TipoEventoHistorico.NOTA_INTERNA, pageable)
                : historico.findByChamadoId(chamadoId, pageable);
        return PaginaResponse.de(resultado.map(mapper::paraResponse));
    }
}
