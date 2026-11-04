package br.com.chamati.service;

import br.com.chamati.dto.AnexoResponse;
import br.com.chamati.dto.AutorResponse;
import br.com.chamati.entity.Anexo;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.exception.AutenticacaoException;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.repository.AnexoRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.security.UsuarioPrincipal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Quem enxerga o chamado (mesma regra da consulta) anexa e baixa arquivos. Chamados fechados não recebem anexos.
 * Aceita JPG, PNG e PDF de até 5 MB.
 */
@Service
@Transactional
public class AnexoService {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    public static final int MAXIMO_POR_CHAMADO = 20;

    private final ChamadoRepository chamados;
    private final UsuarioRepository usuarios;
    private final AnexoRepository anexos;
    private final Clock clock;

    public AnexoService(ChamadoRepository chamados, UsuarioRepository usuarios, AnexoRepository anexos, Clock clock) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.anexos = anexos;
        this.clock = clock;
    }

    public AnexoResponse criar(Long chamadoId, String nomeOriginal, byte[] conteudo, UsuarioPrincipal ator) {
        Chamado chamado = carregar(chamadoId, ator);
        if (!MaquinaEstadosChamado.podeComentar(chamado.getStatus())) {
            throw new RegraNegocioException("Chamados fechados não recebem anexos. Reabra o chamado.");
        }
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraNegocioException("O arquivo está vazio.");
        }
        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new MaxUploadSizeExceededException(TAMANHO_MAXIMO);
        }
        TipoArquivoAnexo tipo = TipoArquivoAnexo.identificar(conteudo)
                .orElseThrow(() -> new RegraNegocioException("Tipo de arquivo não permitido. Envie JPG, PNG ou PDF."));
        if (anexos.countByChamadoId(chamadoId) >= MAXIMO_POR_CHAMADO) {
            throw new RegraNegocioException("O chamado já tem o máximo de " + MAXIMO_POR_CHAMADO + " anexos.");
        }
        Usuario autor = usuarios.findById(ator.id())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AutenticacaoException("Sessão inválida ou expirada."));

        Anexo anexo = new Anexo();
        anexo.setChamado(chamado);
        anexo.setAutor(autor);
        anexo.setNomeArquivo(NomeArquivoAnexo.normalizar(nomeOriginal, tipo));
        anexo.setTipoConteudo(tipo.tipoConteudo());
        anexo.setTamanho(conteudo.length);
        anexo.setConteudo(conteudo);
        anexo.setCriadoEm(clock.instant().truncatedTo(ChronoUnit.MICROS));
        anexos.save(anexo);
        return new AnexoResponse(anexo.getId(), anexo.getNomeArquivo(), anexo.getTipoConteudo(), anexo.getTamanho(),
                new AutorResponse(autor.getId(), autor.getNome(), autor.getPerfil()), anexo.getCriadoEm());
    }

    @Transactional(readOnly = true)
    public List<AnexoResponse> listar(Long chamadoId, UsuarioPrincipal ator) {
        carregar(chamadoId, ator);
        return anexos.listarPorChamado(chamadoId).stream()
                .map(a -> new AnexoResponse(a.id(), a.nomeArquivo(), a.tipoConteudo(), a.tamanho(),
                        new AutorResponse(a.autorId(), a.autorNome(), a.autorPerfil()), a.criadoEm()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Anexo baixar(Long chamadoId, Long anexoId, UsuarioPrincipal ator) {
        carregar(chamadoId, ator);
        return anexos.findByIdAndChamadoId(anexoId, chamadoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado."));
    }

    private Chamado carregar(Long id, UsuarioPrincipal ator) {
        return chamados.findDetalheById(id)
                .filter(c -> ChamadoConsultaService.visivel(c, ator))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
    }
}
