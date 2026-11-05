package br.com.chamati.service;

import br.com.chamati.dto.AvaliacaoCriarRequest;
import br.com.chamati.dto.AvaliacaoResponse;
import br.com.chamati.dto.MediaTecnicoResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.entity.Avaliacao;
import br.com.chamati.entity.Chamado;
import br.com.chamati.exception.AcessoNegadoException;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.AvaliacaoRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Só o solicitante avalia, uma única vez, quando o chamado está resolvido. A consulta é restrita a ADMIN no controller. */
@Service
@Transactional
public class AvaliacaoService {

    private final ChamadoRepository chamados;
    private final AvaliacaoRepository avaliacoes;
    private final AcompanhamentoMapper mapper;
    private final Clock clock;

    public AvaliacaoService(ChamadoRepository chamados, AvaliacaoRepository avaliacoes, AcompanhamentoMapper mapper,
                            Clock clock) {
        this.chamados = chamados;
        this.avaliacoes = avaliacoes;
        this.mapper = mapper;
        this.clock = clock;
    }

    public AvaliacaoResponse avaliar(Long chamadoId, AvaliacaoCriarRequest req, UsuarioPrincipal ator) {
        Chamado chamado = chamados.findDetalheById(chamadoId)
                .filter(c -> ChamadoConsultaService.visivel(c, ator))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
        if (!chamado.getSolicitante().getId().equals(ator.id())) {
            throw new AcessoNegadoException("Somente o solicitante do chamado pode avaliá-lo.");
        }
        if (avaliacoes.existsByChamadoId(chamadoId)) {
            throw new ConflitoException("Este chamado já foi avaliado.");
        }
        if (!MaquinaEstadosChamado.podeAvaliar(chamado.getStatus())) {
            throw new RegraNegocioException("Só é possível avaliar chamados resolvidos.");
        }
        if (chamado.getTecnico() == null) {
            throw new RegraNegocioException("O chamado não tem técnico responsável.");
        }

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setChamado(chamado);
        avaliacao.setTecnico(chamado.getTecnico());
        avaliacao.setNota(req.nota());
        String comentario = req.comentario() == null ? "" : req.comentario().trim();
        avaliacao.setComentario(comentario.isEmpty() ? null : comentario);
        avaliacao.setCriadoEm(clock.instant().truncatedTo(ChronoUnit.MICROS));
        try {
            avaliacoes.saveAndFlush(avaliacao);
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Este chamado já foi avaliado.");
        }
        return mapper.paraResponse(avaliacao);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AvaliacaoResponse> listar(Long tecnicoId, int pagina, int tamanho) {
        PageRequest pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm", "id"));
        Page<Avaliacao> resultado = tecnicoId == null
                ? avaliacoes.findAllBy(pageable)
                : avaliacoes.findByTecnicoId(tecnicoId, pageable);
        return PaginaResponse.de(resultado.map(mapper::paraResponse));
    }

    @Transactional(readOnly = true)
    public List<MediaTecnicoResponse> mediasPorTecnico() {
        return avaliacoes.mediasPorTecnico().stream()
                .map(m -> new MediaTecnicoResponse(m.getTecnicoId(), m.getTecnicoNome(), arredondar(m.getMedia()),
                        m.getQuantidade()))
                .toList();
    }

    private static double arredondar(Double media) {
        return BigDecimal.valueOf(media).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
