package br.com.chamati.service;

import br.com.chamati.config.AnexoProperties;
import br.com.chamati.dto.AnexoResponse;
import br.com.chamati.entity.Anexo;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.AnexoRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Quem enxerga o chamado (mesma regra da consulta) lista, baixa e anexa. Chamados fechados não recebem anexos.
 * Só JPG, PNG e PDF, reconhecidos pelo conteúdo do arquivo.
 */
@Service
@Transactional
public class AnexoService {

    private static final int TAMANHO_MAXIMO_NOME = 255;

    public record Conteudo(String nome, String tipoConteudo, byte[] bytes) {
    }

    private final ChamadoRepository chamados;
    private final UsuarioRepository usuarios;
    private final AnexoRepository anexos;
    private final HistoricoChamadoRepository historico;
    private final ArmazenamentoAnexos armazenamento;
    private final AcompanhamentoMapper mapper;
    private final AnexoProperties props;
    private final Clock clock;

    public AnexoService(ChamadoRepository chamados, UsuarioRepository usuarios, AnexoRepository anexos,
                        HistoricoChamadoRepository historico, ArmazenamentoAnexos armazenamento,
                        AcompanhamentoMapper mapper, AnexoProperties props, Clock clock) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.anexos = anexos;
        this.historico = historico;
        this.armazenamento = armazenamento;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    public AnexoResponse criar(Long chamadoId, MultipartFile arquivo, UsuarioPrincipal ator) {
        Chamado chamado = carregar(chamadoId, ator);
        if (!MaquinaEstadosChamado.podeAnexar(chamado.getStatus())) {
            throw new RegraNegocioException("Chamados fechados não recebem anexos. Reabra o chamado.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraNegocioException("Envie um arquivo não vazio.");
        }
        long limite = props.tamanhoMaximo().toBytes();
        if (arquivo.getSize() > limite) {
            throw new MaxUploadSizeExceededException(limite);
        }
        if (anexos.countByChamadoId(chamadoId) >= props.maximoPorChamado()) {
            throw new RegraNegocioException("O chamado já tem o máximo de " + props.maximoPorChamado() + " anexos.");
        }
        byte[] bytes = ler(arquivo);
        TipoAnexo tipo = TipoAnexo.detectar(bytes).orElseThrow(
                () -> new RegraNegocioException("Tipo de arquivo não permitido. Envie JPG, PNG ou PDF."));
        Usuario autor = usuarios.findById(ator.id())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AutenticacaoException("Sessão inválida ou expirada."));

        String nome = nomeSeguro(arquivo.getOriginalFilename(), tipo);
        String chave = armazenamento.salvar(bytes);
        try {
            Anexo anexo = new Anexo();
            anexo.setChamado(chamado);
            anexo.setAutor(autor);
            anexo.setNomeOriginal(nome);
            anexo.setTipoConteudo(tipo.tipoConteudo());
            anexo.setTamanho(bytes.length);
            anexo.setChaveArmazenamento(chave);
            Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
            anexo.setCriadoEm(agora);
            anexos.save(anexo);
            historico.save(new HistoricoChamado(chamado, autor, TipoEventoHistorico.EDICAO,
                    "anexo", null, nome, "Anexo adicionado"));
            return mapper.paraResponse(anexo);
        } catch (RuntimeException e) {
            armazenamento.remover(chave);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<AnexoResponse> listar(Long chamadoId, UsuarioPrincipal ator) {
        carregar(chamadoId, ator);
        return anexos.findByChamadoIdOrderByCriadoEmAscIdAsc(chamadoId).stream().map(mapper::paraResponse).toList();
    }

    @Transactional(readOnly = true)
    public Conteudo baixar(Long chamadoId, Long anexoId, UsuarioPrincipal ator) {
        carregar(chamadoId, ator);
        Anexo anexo = anexos.findByIdAndChamadoId(anexoId, chamadoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado."));
        byte[] bytes = armazenamento.ler(anexo.getChaveArmazenamento())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado."));
        return new Conteudo(anexo.getNomeOriginal(), anexo.getTipoConteudo(), bytes);
    }

    private Chamado carregar(Long id, UsuarioPrincipal ator) {
        return chamados.findDetalheById(id)
                .filter(c -> ChamadoConsultaService.visivel(c, ator))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
    }

    private static byte[] ler(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    /** Remove caminho, caracteres de controle e de formatação (ex.: inversão de direção) e limita o tamanho. */
    static String nomeSeguro(String original, TipoAnexo tipo) {
        String nome = original == null ? "" : original;
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        if (nome.isEmpty()) {
            return "anexo." + tipo.extensao();
        }
        if (nome.length() > TAMANHO_MAXIMO_NOME) {
            int fim = Character.isHighSurrogate(nome.charAt(TAMANHO_MAXIMO_NOME - 1))
                    ? TAMANHO_MAXIMO_NOME - 1 : TAMANHO_MAXIMO_NOME;
            nome = nome.substring(0, fim);
        }
        return nome;
    }
}
