package br.com.chamati.service;

import br.com.chamati.dto.ComentarioCriarRequest;
import br.com.chamati.dto.ComentarioResponse;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Comentario;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.TipoComentario;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.AcessoNegadoException;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.ComentarioRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem enxerga o chamado (mesma regra da consulta) pode comentar e ler. Notas internas: só TECNICO e ADMIN
 * criam e leem; o SOLICITANTE nunca as recebe, nem filtrando por tipo.
 */
@Service
@Transactional
public class ComentarioService {

    private final ChamadoRepository chamados;
    private final UsuarioRepository usuarios;
    private final ComentarioRepository comentarios;
    private final HistoricoChamadoRepository historico;
    private final AcompanhamentoMapper mapper;
    private final Clock clock;

    public ComentarioService(ChamadoRepository chamados, UsuarioRepository usuarios, ComentarioRepository comentarios,
                             HistoricoChamadoRepository historico, AcompanhamentoMapper mapper, Clock clock) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.comentarios = comentarios;
        this.historico = historico;
        this.mapper = mapper;
        this.clock = clock;
    }

    public ComentarioResponse criar(Long chamadoId, ComentarioCriarRequest req, UsuarioPrincipal ator) {
        Chamado chamado = carregar(chamadoId, ator);
        if (ator.perfil() == Perfil.SOLICITANTE && req.tipo() == TipoComentario.NOTA_INTERNA) {
            throw new AcessoNegadoException("Solicitantes não podem criar notas internas.");
        }
        if (!MaquinaEstadosChamado.podeComentar(chamado.getStatus())) {
            throw new RegraNegocioException("Chamados fechados não recebem comentários. Reabra o chamado.");
        }
        Usuario autor = usuarios.findById(ator.id())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AutenticacaoException("Sessão inválida ou expirada."));

        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean primeiraResposta = RegraPrimeiraResposta.registra(autor.getPerfil(), req.tipo(),
                chamado.getSolicitante().getId().equals(autor.getId()), chamado.getPrimeiraRespostaEm() != null);
        if (primeiraResposta) {
            // Grava o chamado antes do comentário: assim o bloqueio da linha é obtido primeiro e duas respostas
            // simultâneas não entram em deadlock (a segunda recebe 409 por versão e basta repetir).
            chamado.setPrimeiraRespostaEm(agora);
            chamados.saveAndFlush(chamado);
        }

        Comentario comentario = new Comentario();
        comentario.setChamado(chamado);
        comentario.setAutor(autor);
        comentario.setTipo(req.tipo());
        comentario.setConteudo(req.conteudo().trim());
        comentario.setCriadoEm(agora);
        comentarios.save(comentario);

        boolean publico = req.tipo() == TipoComentario.PUBLICO;
        String descricao = publico ? "Comentário adicionado" : "Nota interna adicionada";
        historico.save(new HistoricoChamado(chamado, autor,
                publico ? TipoEventoHistorico.COMENTARIO : TipoEventoHistorico.NOTA_INTERNA,
                primeiraResposta ? "primeira_resposta_em" : null, null,
                primeiraResposta ? DescricaoReabertura.data(agora) : null,
                primeiraResposta ? descricao + " (primeira resposta)" : descricao));
        return mapper.paraResponse(comentario);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ComentarioResponse> listar(Long chamadoId, TipoComentario tipo, String direcao,
                                                     int pagina, int tamanho, UsuarioPrincipal ator) {
        carregar(chamadoId, ator);
        TipoComentario filtro = tipo;
        if (ator.perfil() == Perfil.SOLICITANTE) {
            if (tipo == TipoComentario.NOTA_INTERNA) {
                throw new AcessoNegadoException("Solicitantes não têm acesso a notas internas.");
            }
            filtro = TipoComentario.PUBLICO;
        }
        PageRequest pageable = PageRequest.of(pagina, tamanho, ordem(direcao));
        var resultado = filtro == null
                ? comentarios.findByChamadoId(chamadoId, pageable)
                : comentarios.findByChamadoIdAndTipo(chamadoId, filtro, pageable);
        return PaginaResponse.de(resultado.map(mapper::paraResponse));
    }

    private Chamado carregar(Long id, UsuarioPrincipal ator) {
        return chamados.findDetalheById(id)
                .filter(c -> ChamadoConsultaService.visivel(c, ator))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
    }

    private static Sort ordem(String direcao) {
        Sort.Direction sentido = direcao == null ? Sort.Direction.ASC : Sort.Direction.fromString(direcao);
        return Sort.by(sentido, "criadoEm").and(Sort.by(sentido, "id"));
    }
}
