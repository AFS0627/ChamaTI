package br.com.chamati.service;

import static br.com.chamati.Fixtures.AGORA;
import static br.com.chamati.Fixtures.chamado;
import static br.com.chamati.Fixtures.principal;
import static br.com.chamati.Fixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.dto.AnexoResponse;
import br.com.chamati.entity.Anexo;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.repository.AnexoRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import br.com.chamati.repository.consulta.AnexoResumo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ExtendWith(MockitoExtension.class)
class AnexoServiceTest {

    static final Long ID = 100L;
    static final byte[] PDF = "%PDF-1.7 conteudo".getBytes(StandardCharsets.US_ASCII);

    @Mock ChamadoRepository chamados;
    @Mock UsuarioRepository usuarios;
    @Mock AnexoRepository anexos;

    AnexoService service;

    Usuario admin = usuario(1L, "Admin", Perfil.ADMIN);
    Usuario carlos = usuario(2L, "Carlos Mendes", Perfil.TECNICO);
    Usuario fernanda = usuario(3L, "Fernanda Lima", Perfil.TECNICO);
    Usuario ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
    Usuario bruno = usuario(5L, "Bruno Costa", Perfil.SOLICITANTE);

    Chamado chamado;

    @BeforeEach
    void preparar() {
        service = new AnexoService(chamados, usuarios, anexos, Fixtures.relogio());
        chamado = chamado(ID, ana, carlos, StatusChamado.EM_ATENDIMENTO);
        lenient().when(chamados.findDetalheById(ID)).thenAnswer(inv -> Optional.ofNullable(chamado));
        for (Usuario u : new Usuario[]{admin, carlos, fernanda, ana, bruno}) {
            lenient().when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    @Test
    void solicitanteAnexaAoProprioChamadoEOArquivoEGravadoComTipoDetectado() {
        AnexoResponse r = service.criar(ID, "erro.pdf", PDF, principal(ana));

        ArgumentCaptor<Anexo> captor = ArgumentCaptor.forClass(Anexo.class);
        verify(anexos).save(captor.capture());
        Anexo gravado = captor.getValue();
        assertThat(gravado.getChamado()).isSameAs(chamado);
        assertThat(gravado.getAutor()).isSameAs(ana);
        assertThat(gravado.getTipoConteudo()).isEqualTo("application/pdf");
        assertThat(gravado.getTamanho()).isEqualTo(PDF.length);
        assertThat(gravado.getConteudo()).isEqualTo(PDF);
        assertThat(gravado.getCriadoEm()).isEqualTo(AGORA);
        assertThat(r.nomeArquivo()).isEqualTo("erro.pdf");
        assertThat(r.autor().id()).isEqualTo(4L);
    }

    @Test
    void tecnicoResponsavelEAdminTambemAnexam() {
        service.criar(ID, "a.pdf", PDF, principal(carlos));
        service.criar(ID, "b.pdf", PDF, principal(admin));

        verify(anexos, org.mockito.Mockito.times(2)).save(any(Anexo.class));
    }

    @Test
    void usaOTipoDoConteudoEIgnoraAExtensaoDoNome() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};

        AnexoResponse r = service.criar(ID, "foto.pdf", png, principal(ana));

        assertThat(r.tipoConteudo()).isEqualTo("image/png");
    }

    @Test
    void chamadoDeOutroSolicitanteOuDeOutroTecnicoResponde404() {
        assertThatThrownBy(() -> service.criar(ID, "a.pdf", PDF, principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(ID, "a.pdf", PDF, principal(fernanda)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(999L, "a.pdf", PDF, principal(admin)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(anexos, never()).save(any());
    }

    @Test
    void chamadoFechadoNaoRecebeAnexos() {
        chamado = chamado(ID, ana, carlos, StatusChamado.FECHADO);

        assertThatThrownBy(() -> service.criar(ID, "a.pdf", PDF, principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("fechados");
        verify(anexos, never()).save(any());
    }

    @Test
    void recusaArquivoVazioETipoNaoPermitido() {
        assertThatThrownBy(() -> service.criar(ID, "a.pdf", new byte[0], principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("vazio");
        assertThatThrownBy(() -> service.criar(ID, "a.png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("JPG, PNG ou PDF");
        verify(anexos, never()).save(any());
    }

    @Test
    void recusaArquivoMaiorQueOLimite() {
        byte[] grande = new byte[(int) AnexoService.TAMANHO_MAXIMO + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);

        assertThatThrownBy(() -> service.criar(ID, "a.pdf", grande, principal(ana)))
                .isInstanceOf(MaxUploadSizeExceededException.class);
        verify(anexos, never()).save(any());
    }

    @Test
    void aceitaArquivoNoLimiteExato() {
        byte[] limite = new byte[(int) AnexoService.TAMANHO_MAXIMO];
        System.arraycopy(PDF, 0, limite, 0, PDF.length);

        assertThat(service.criar(ID, "a.pdf", limite, principal(ana)).tamanho()).isEqualTo(AnexoService.TAMANHO_MAXIMO);
    }

    @Test
    void recusaQuandoOChamadoJaTemOMaximoDeAnexos() {
        when(anexos.countByChamadoId(ID)).thenReturn((long) AnexoService.MAXIMO_POR_CHAMADO);

        assertThatThrownBy(() -> service.criar(ID, "a.pdf", PDF, principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("máximo");
        verify(anexos, never()).save(any());
    }

    @Test
    void listaOsAnexosVisiveis() {
        when(anexos.listarPorChamado(ID)).thenReturn(List.of(
                new AnexoResumo(7L, "erro.pdf", "application/pdf", 17L, 4L, "Ana Souza", Perfil.SOLICITANTE, AGORA)));

        List<AnexoResponse> lista = service.listar(ID, principal(ana));

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).id()).isEqualTo(7L);
        assertThat(lista.get(0).autor().nome()).isEqualTo("Ana Souza");
        assertThatThrownBy(() -> service.listar(ID, principal(bruno))).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void baixaOAnexoDoChamadoEResponde404ParaOutroChamadoOuSemVisibilidade() {
        Anexo anexo = new Anexo();
        when(anexos.findByIdAndChamadoId(7L, ID)).thenReturn(Optional.of(anexo));
        when(anexos.findByIdAndChamadoId(8L, ID)).thenReturn(Optional.empty());

        assertThat(service.baixar(ID, 7L, principal(carlos))).isSameAs(anexo);
        assertThatThrownBy(() -> service.baixar(ID, 8L, principal(carlos))).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.baixar(ID, 7L, principal(bruno))).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
