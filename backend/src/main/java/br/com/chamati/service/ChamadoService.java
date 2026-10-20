package br.com.chamati.service;

import br.com.chamati.dto.ChamadoCriarRequest;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.entity.Categoria;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Subcategoria;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.ChamadoMapper;
import br.com.chamati.repository.CategoriaRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.SubcategoriaRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChamadoService {

    private final ChamadoRepository chamados;
    private final CategoriaRepository categorias;
    private final SubcategoriaRepository subcategorias;
    private final UsuarioRepository usuarios;
    private final HistoricoChamadoRepository historico;
    private final ProtocoloService protocolos;
    private final SlaService sla;
    private final ChamadoMapper mapper;
    private final Clock clock;

    public ChamadoService(ChamadoRepository chamados, CategoriaRepository categorias,
                          SubcategoriaRepository subcategorias, UsuarioRepository usuarios,
                          HistoricoChamadoRepository historico, ProtocoloService protocolos,
                          SlaService sla, ChamadoMapper mapper, Clock clock) {
        this.chamados = chamados;
        this.categorias = categorias;
        this.subcategorias = subcategorias;
        this.usuarios = usuarios;
        this.historico = historico;
        this.protocolos = protocolos;
        this.sla = sla;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Abre um chamado em nome do usuário autenticado. Toda validação acontece antes de reservar o
     * número do protocolo, para segurar o bloqueio da sequência pelo menor tempo possível.
     */
    @Transactional
    public ChamadoResponse abrir(ChamadoCriarRequest req, UsuarioPrincipal atual) {
        Usuario solicitante = usuarios.findById(atual.id())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AutenticacaoException("Sessão inválida ou expirada."));

        Prioridade prioridade = req.prioridade() != null ? req.prioridade() : Prioridade.MEDIA;
        validarPrioridadePermitida(solicitante.getPerfil(), prioridade);

        Categoria categoria = categorias.findById(req.categoriaId())
                .filter(Categoria::isAtivo)
                .orElseThrow(() -> new RegraNegocioException("Categoria inválida ou inativa."));
        Subcategoria subcategoria = null;
        if (req.subcategoriaId() != null) {
            subcategoria = subcategorias.findByIdAndCategoriaId(req.subcategoriaId(), categoria.getId())
                    .filter(Subcategoria::isAtivo)
                    .orElseThrow(() -> new RegraNegocioException(
                            "Subcategoria inválida, inativa ou de outra categoria."));
        }

        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Chamado chamado = new Chamado();
        chamado.setTitulo(req.titulo().trim());
        chamado.setDescricao(req.descricao().trim());
        chamado.setCategoria(categoria);
        chamado.setSubcategoria(subcategoria);
        chamado.setPrioridade(prioridade);
        chamado.setStatus(StatusChamado.ABERTO);
        chamado.setSolicitante(solicitante);
        chamado.setCriadoEm(agora);
        sla.aplicarLimites(chamado);
        chamado.setProtocolo(protocolos.gerar(agora));

        chamados.saveAndFlush(chamado);
        historico.save(new HistoricoChamado(chamado, solicitante, TipoEventoHistorico.CRIACAO,
                null, null, null, "Chamado criado"));
        return mapper.paraResponse(chamado);
    }

    /** Solicitantes escolhem BAIXA, MEDIA ou ALTA; CRITICA é definida por TECNICO ou ADMIN. */
    private static void validarPrioridadePermitida(Perfil perfil, Prioridade prioridade) {
        if (perfil == Perfil.SOLICITANTE && prioridade == Prioridade.CRITICA) {
            throw new RegraNegocioException(
                    "Apenas técnicos e administradores podem abrir chamados com prioridade CRITICA.");
        }
    }
}
