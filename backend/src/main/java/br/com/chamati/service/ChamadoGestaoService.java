package br.com.chamati.service;

import br.com.chamati.dto.AlterarStatusRequest;
import br.com.chamati.dto.AtribuirTecnicoRequest;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.ReabrirChamadoRequest;
import br.com.chamati.dto.ResolverChamadoRequest;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.AcessoNegadoException;
import br.com.chamati.exception.ConflitoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.ChamadoMapper;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ordem das verificações: chamado visível (404) → permissão (403) → versão (409) → regras de negócio (422).
 *
 * Quem pode: atribuir — ADMIN (qualquer técnico ativo) ou TECNICO assumindo para si um chamado sem técnico;
 * alterar status e resolver — ADMIN ou o técnico responsável; fechar — ADMIN ou o solicitante do chamado;
 * reabrir — ADMIN, o solicitante ou o técnico responsável.
 */
@Service
@Transactional
public class ChamadoGestaoService {

    private final ChamadoRepository chamados;
    private final UsuarioRepository usuarios;
    private final HistoricoChamadoRepository historico;
    private final ChamadoMapper mapper;
    private final Clock clock;

    public ChamadoGestaoService(ChamadoRepository chamados, UsuarioRepository usuarios,
                                HistoricoChamadoRepository historico, ChamadoMapper mapper, Clock clock) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.historico = historico;
        this.mapper = mapper;
        this.clock = clock;
    }

    public ChamadoResponse atribuirTecnico(Long id, AtribuirTecnicoRequest req, UsuarioPrincipal ator) {
        Chamado c = carregar(id, ator);
        if (ator.perfil() == Perfil.SOLICITANTE) {
            throw negado("Solicitantes não podem atribuir técnicos.");
        }
        if (ator.perfil() == Perfil.TECNICO) {
            if (!ator.id().equals(req.tecnicoId())) {
                throw negado("Técnicos só podem atribuir o chamado a si mesmos.");
            }
            if (c.getTecnico() != null) {
                throw negado("Somente administradores podem trocar o técnico responsável.");
            }
        }
        verificarVersao(c, req.versao());
        if (!MaquinaEstadosChamado.podeAtribuir(c.getStatus())) {
            throw new RegraNegocioException("Não é possível atribuir técnico a um chamado " + c.getStatus() + ".");
        }
        Usuario novo = usuarios.findByIdAndPerfilAndAtivoTrue(req.tecnicoId(), Perfil.TECNICO)
                .orElseThrow(() -> new RegraNegocioException(
                        "O técnico informado não existe, está inativo ou não tem perfil de técnico."));
        Usuario anterior = c.getTecnico();
        if (anterior != null && anterior.getId().equals(novo.getId())) {
            throw new RegraNegocioException("O chamado já está atribuído a este técnico.");
        }

        Instant agora = agora();
        c.setTecnico(novo);
        c.setAtribuidoEm(agora);
        chamados.saveAndFlush(c);
        registrar(c, ator, TipoEventoHistorico.ATRIBUICAO, "tecnico",
                anterior == null ? null : anterior.getNome(), novo.getNome(),
                anterior == null ? "Chamado atribuído ao técnico" : "Técnico responsável alterado");
        return mapper.paraResponse(c, agora);
    }

    public ChamadoResponse alterarStatus(Long id, AlterarStatusRequest req, UsuarioPrincipal ator) {
        Chamado c = carregar(id, ator);
        exigirResponsavel(c, ator);
        verificarVersao(c, req.versao());
        StatusChamado de = c.getStatus();
        StatusChamado para = req.status();
        if (para == StatusChamado.RESOLVIDO) {
            throw new RegraNegocioException("Para resolver o chamado use a operação de resolução, informando a solução.");
        }
        if (para == StatusChamado.FECHADO) {
            throw new RegraNegocioException("Para fechar o chamado use a operação de fechamento.");
        }
        if (!MaquinaEstadosChamado.podeAlterarStatus(de, para)) {
            throw new RegraNegocioException("Transição de status inválida: " + de + " → " + para + ".");
        }
        exigirTecnico(c);

        Instant agora = agora();
        c.setStatus(para);
        chamados.saveAndFlush(c);
        registrar(c, ator, TipoEventoHistorico.ALTERACAO_STATUS, "status", de.name(), para.name(),
                vazioParaNulo(req.observacao()));
        return mapper.paraResponse(c, agora);
    }

    public ChamadoResponse resolver(Long id, ResolverChamadoRequest req, UsuarioPrincipal ator) {
        Chamado c = carregar(id, ator);
        exigirResponsavel(c, ator);
        verificarVersao(c, req.versao());
        StatusChamado de = c.getStatus();
        if (!MaquinaEstadosChamado.podeResolver(de)) {
            throw new RegraNegocioException("Só é possível resolver chamados em atendimento ou aguardando (status atual: "
                    + de + ").");
        }
        exigirTecnico(c);

        Instant agora = agora();
        c.setStatus(StatusChamado.RESOLVIDO);
        c.setResolvidoEm(agora);
        c.setSolucao(req.solucao().trim());
        chamados.saveAndFlush(c);
        registrar(c, ator, TipoEventoHistorico.RESOLUCAO, "status", de.name(), StatusChamado.RESOLVIDO.name(),
                "Solução registrada");
        return mapper.paraResponse(c, agora);
    }

    public ChamadoResponse fechar(Long id, Long versao, UsuarioPrincipal ator) {
        Chamado c = carregar(id, ator);
        if (ator.perfil() != Perfil.ADMIN && !ehSolicitante(c, ator)) {
            throw negado("Apenas o solicitante ou um administrador pode fechar o chamado.");
        }
        verificarVersao(c, versao);
        StatusChamado de = c.getStatus();
        if (!MaquinaEstadosChamado.podeFechar(de)) {
            throw new RegraNegocioException("Só é possível fechar chamados resolvidos (status atual: " + de + ").");
        }

        Instant agora = agora();
        c.setStatus(StatusChamado.FECHADO);
        c.setFechadoEm(agora);
        chamados.saveAndFlush(c);
        registrar(c, ator, TipoEventoHistorico.ENCERRAMENTO, "status", de.name(), StatusChamado.FECHADO.name(),
                "Chamado encerrado");
        return mapper.paraResponse(c, agora);
    }

    /**
     * Reabre um chamado resolvido ou fechado. resolvido_em e fechado_em voltam a nulo (os valores
     * anteriores ficam no histórico), reaberto_em recebe o instante e total_reaberturas é incrementado.
     * Os limites de SLA não mudam.
     */
    public ChamadoResponse reabrir(Long id, ReabrirChamadoRequest req, UsuarioPrincipal ator) {
        Chamado c = carregar(id, ator);
        if (ator.perfil() != Perfil.ADMIN && !ehSolicitante(c, ator) && !ehResponsavel(c, ator)) {
            throw negado("Apenas o solicitante, o técnico responsável ou um administrador pode reabrir o chamado.");
        }
        verificarVersao(c, req.versao());
        StatusChamado de = c.getStatus();
        if (!MaquinaEstadosChamado.podeReabrir(de)) {
            throw new RegraNegocioException("Só é possível reabrir chamados resolvidos ou fechados (status atual: "
                    + de + ").");
        }

        Instant resolvidoAnterior = c.getResolvidoEm();
        Instant fechadoAnterior = c.getFechadoEm();
        StatusChamado para = MaquinaEstadosChamado.statusAposReabertura(c.getTecnico() != null);
        Instant agora = agora();
        c.setStatus(para);
        c.setResolvidoEm(null);
        c.setFechadoEm(null);
        c.setReabertoEm(agora);
        c.setTotalReaberturas(c.getTotalReaberturas() + 1);
        chamados.saveAndFlush(c);
        registrar(c, ator, TipoEventoHistorico.REABERTURA, "status", de.name(), para.name(),
                DescricaoReabertura.formatar(req.motivo(), resolvidoAnterior, fechadoAnterior));
        return mapper.paraResponse(c, agora);
    }

    private Chamado carregar(Long id, UsuarioPrincipal ator) {
        return chamados.findDetalheById(id)
                .filter(c -> ChamadoConsultaService.visivel(c, ator))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
    }

    private void registrar(Chamado c, UsuarioPrincipal ator, TipoEventoHistorico tipo, String campo,
                           String anterior, String novo, String descricao) {
        historico.save(new HistoricoChamado(c, usuarios.getReferenceById(ator.id()), tipo, campo, anterior, novo,
                descricao));
    }

    private static boolean ehSolicitante(Chamado c, UsuarioPrincipal ator) {
        return c.getSolicitante().getId().equals(ator.id());
    }

    private static boolean ehResponsavel(Chamado c, UsuarioPrincipal ator) {
        return ator.perfil() == Perfil.TECNICO && c.getTecnico() != null && c.getTecnico().getId().equals(ator.id());
    }

    private static void exigirResponsavel(Chamado c, UsuarioPrincipal ator) {
        if (ator.perfil() != Perfil.ADMIN && !ehResponsavel(c, ator)) {
            throw negado("Apenas o técnico responsável ou um administrador pode executar esta ação.");
        }
    }

    private static void exigirTecnico(Chamado c) {
        if (c.getTecnico() == null) {
            throw new RegraNegocioException("Atribua um técnico ao chamado antes de continuar.");
        }
    }

    private static void verificarVersao(Chamado c, Long versaoEsperada) {
        if (versaoEsperada != null && !versaoEsperada.equals(c.getVersao())) {
            throw new ConflitoException("O chamado foi alterado por outra pessoa. Recarregue e tente novamente.");
        }
    }

    private static AcessoNegadoException negado(String mensagem) {
        return new AcessoNegadoException(mensagem);
    }

    private Instant agora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
