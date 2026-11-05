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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chamati.Fixtures;
import br.com.chamati.config.AnexoProperties;
import br.com.chamati.dto.AnexoResponse;
import br.com.chamati.entity.Anexo;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.HistoricoChamado;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoEventoHistorico;
import br.com.chamati.exception.RecursoNaoEncontradoException;
import br.com.chamati.exception.RegraNegocioException;
import br.com.chamati.mapper.AcompanhamentoMapper;
import br.com.chamati.repository.AnexoRepository;
import br.com.chamati.repository.ChamadoRepository;
import br.com.chamati.repository.HistoricoChamadoRepository;
import br.com.chamati.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ExtendWith(MockitoExtension.class)
class AnexoServiceTest {

    static final Long ID = 100L;
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Mock ChamadoRepository chamados;
    @Mock UsuarioRepository usuarios;
    @Mock AnexoRepository anexos;
    @Mock HistoricoChamadoRepository historico;
    @Mock ArmazenamentoAnexos armazenamento;

    AnexoService service;

    Usuario admin = usuario(1L, "Admin", Perfil.ADMIN);
    Usuario carlos = usuario(2L, "Carlos Mendes", Perfil.TECNICO);
    Usuario ana = usuario(4L, "Ana Souza", Perfil.SOLICITANTE);
    Usuario bruno = usuario(5L, "Bruno Costa", Perfil.SOLICITANTE);

    Chamado chamado;

    @BeforeEach
    void preparar() {
        service = new AnexoService(chamados, usuarios, anexos, historico, armazenamento, new AcompanhamentoMapper(),
                new AnexoProperties("./anexos-teste", DataSize.ofBytes(1024), 2), Fixtures.relogio());
        chamado = chamado(ID, ana, carlos, StatusChamado.EM_ATENDIMENTO);
        lenient().when(chamados.findDetalheById(ID)).thenAnswer(inv -> Optional.ofNullable(chamado));
        for (Usuario u : new Usuario[]{admin, carlos, ana, bruno}) {
            lenient().when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(armazenamento.salvar(any())).thenReturn("chave-1");
    }

    private static MockMultipartFile arquivo(String nome, String tipo, byte[] bytes) {
        return new MockMultipartFile("arquivo", nome, tipo, bytes);
    }

    private void nadaFoiGravado() {
        verifyNoInteractions(armazenamento, historico);
        verify(anexos, never()).save(any());
    }

    // =====================================================================
    // criar
    // =====================================================================

    @Test
    void gravaOArquivoOsMetadadosEOEventoDeHistorico() {
        AnexoResponse r = service.criar(ID, arquivo("foto.png", "image/png", PNG), principal(ana));

        ArgumentCaptor<Anexo> anexo = ArgumentCaptor.forClass(Anexo.class);
        verify(anexos).save(anexo.capture());
        assertThat(anexo.getValue().getChamado()).isSameAs(chamado);
        assertThat(anexo.getValue().getAutor()).isSameAs(ana);
        assertThat(anexo.getValue().getNomeOriginal()).isEqualTo("foto.png");
        assertThat(anexo.getValue().getTipoConteudo()).isEqualTo("image/png");
        assertThat(anexo.getValue().getTamanho()).isEqualTo(PNG.length);
        assertThat(anexo.getValue().getChaveArmazenamento()).isEqualTo("chave-1");
        assertThat(anexo.getValue().getCriadoEm()).isEqualTo(AGORA);
        verify(armazenamento).salvar(PNG);

        ArgumentCaptor<HistoricoChamado> evento = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historico).save(evento.capture());
        assertThat(evento.getValue().getTipoEvento()).isEqualTo(TipoEventoHistorico.EDICAO);
        assertThat(evento.getValue().getCampo()).isEqualTo("anexo");
        assertThat(evento.getValue().getValorNovo()).isEqualTo("foto.png");
        assertThat(evento.getValue().getDescricao()).isEqualTo("Anexo adicionado");
        assertThat(evento.getValue().getAutor()).isSameAs(ana);

        assertThat(r.nome()).isEqualTo("foto.png");
        assertThat(r.tipoConteudo()).isEqualTo("image/png");
        assertThat(r.tamanho()).isEqualTo(PNG.length);
        assertThat(r.autor().id()).isEqualTo(4L);
        assertThat(r.criadoEm()).isEqualTo(AGORA);
    }

    @Test
    void otipoVemDoConteudoENaoDoNomeNemDoContentTypeEnviado() {
        AnexoResponse r = service.criar(ID, arquivo("documento.pdf", "application/pdf", PNG), principal(carlos));

        assertThat(r.tipoConteudo()).isEqualTo("image/png");
    }

    @Test
    void tecnicoEAdminTambemAnexam() {
        service.criar(ID, arquivo("a.png", "image/png", PNG), principal(carlos));
        service.criar(ID, arquivo("b.png", "image/png", PNG), principal(admin));

        verify(anexos, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void chamadoForaDaVisibilidadeResponde404SemGravarNada() {
        assertThatThrownBy(() -> service.criar(ID, arquivo("a.png", "image/png", PNG), principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        when(chamados.findDetalheById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.criar(999L, arquivo("a.png", "image/png", PNG), principal(admin)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        nadaFoiGravado();
    }

    @Test
    void chamadoFechadoNaoRecebeAnexos() {
        chamado.setStatus(StatusChamado.FECHADO);

        assertThatThrownBy(() -> service.criar(ID, arquivo("a.png", "image/png", PNG), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("fechados");

        nadaFoiGravado();
    }

    @Test
    void chamadoResolvidoAindaRecebeAnexos() {
        chamado.setStatus(StatusChamado.RESOLVIDO);

        service.criar(ID, arquivo("a.png", "image/png", PNG), principal(ana));

        verify(anexos).save(any());
    }

    @Test
    void rejeitaArquivoVazioOuAusente() {
        assertThatThrownBy(() -> service.criar(ID, arquivo("a.png", "image/png", new byte[0]), principal(ana)))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criar(ID, null, principal(ana)))
                .isInstanceOf(RegraNegocioException.class);

        nadaFoiGravado();
    }

    @Test
    void rejeitaArquivoMaiorQueOLimite() {
        byte[] grande = new byte[1025];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        assertThatThrownBy(() -> service.criar(ID, arquivo("a.png", "image/png", grande), principal(ana)))
                .isInstanceOf(MaxUploadSizeExceededException.class);
        service.criar(ID, arquivo("limite.png", "image/png", java.util.Arrays.copyOf(PNG, 1024)), principal(ana));
    }

    @Test
    void rejeitaTiposNaoPermitidosMesmoComContentTypeEExtensaoValidos() {
        byte[] texto = "<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.criar(ID, arquivo("a.png", "image/png", texto), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("JPG, PNG ou PDF");

        nadaFoiGravado();
    }

    @Test
    void respeitaOLimiteDeAnexosPorChamado() {
        when(anexos.countByChamadoId(ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.criar(ID, arquivo("a.png", "image/png", PNG), principal(ana)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("máximo de 2");

        nadaFoiGravado();
    }

    @Test
    void removeOArquivoQuandoOBancoFalha() {
        when(anexos.save(any())).thenThrow(new IllegalStateException("falha"));

        assertThatThrownBy(() -> service.criar(ID, arquivo("a.png", "image/png", PNG), principal(ana)))
                .isInstanceOf(IllegalStateException.class);

        verify(armazenamento).remover("chave-1");
        verifyNoInteractions(historico);
    }

    // =====================================================================
    // nome do arquivo
    // =====================================================================

    @Test
    void nomeSeguroRemoveCaminhoControleEFormatacao() {
        assertThat(AnexoService.nomeSeguro("C:\\fakepath\\foto.png", TipoAnexo.PNG)).isEqualTo("foto.png");
        assertThat(AnexoService.nomeSeguro("../../etc/passwd.png", TipoAnexo.PNG)).isEqualTo("passwd.png");
        assertThat(AnexoService.nomeSeguro("a\u0000b\r\n.png", TipoAnexo.PNG)).isEqualTo("ab.png");
        assertThat(AnexoService.nomeSeguro("\u202Egnp.exe", TipoAnexo.PNG)).isEqualTo("gnp.exe");
        assertThat(AnexoService.nomeSeguro("  relatório mensal.pdf ", TipoAnexo.PDF)).isEqualTo("relatório mensal.pdf");
    }

    @Test
    void nomeSeguroUsaNomePadraoQuandoVazioELimitaOTamanho() {
        assertThat(AnexoService.nomeSeguro(null, TipoAnexo.JPEG)).isEqualTo("anexo.jpg");
        assertThat(AnexoService.nomeSeguro("   ", TipoAnexo.PDF)).isEqualTo("anexo.pdf");
        assertThat(AnexoService.nomeSeguro("pasta/", TipoAnexo.PNG)).isEqualTo("anexo.png");
        assertThat(AnexoService.nomeSeguro("x".repeat(300), TipoAnexo.PNG)).hasSize(255);
    }

    // =====================================================================
    // listar e baixar
    // =====================================================================

    private Anexo anexo(long id, String chave) {
        Anexo a = new Anexo();
        org.springframework.test.util.ReflectionTestUtils.setField(a, "id", id);
        a.setChamado(chamado);
        a.setAutor(ana);
        a.setNomeOriginal("foto.png");
        a.setTipoConteudo("image/png");
        a.setTamanho(PNG.length);
        a.setChaveArmazenamento(chave);
        a.setCriadoEm(AGORA);
        return a;
    }

    @Test
    void listaOsAnexosDoChamadoVisivel() {
        when(anexos.findByChamadoIdOrderByCriadoEmAscIdAsc(ID)).thenReturn(List.of(anexo(1L, "k1"), anexo(2L, "k2")));

        List<AnexoResponse> r = service.listar(ID, principal(ana));

        assertThat(r).extracting(AnexoResponse::id).containsExactly(1L, 2L);
        assertThatThrownBy(() -> service.listar(ID, principal(bruno))).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void baixaOConteudoDoAnexo() {
        when(anexos.findByIdAndChamadoId(7L, ID)).thenReturn(Optional.of(anexo(7L, "k7")));
        when(armazenamento.ler("k7")).thenReturn(Optional.of(PNG));

        AnexoService.Conteudo c = service.baixar(ID, 7L, principal(carlos));

        assertThat(c.nome()).isEqualTo("foto.png");
        assertThat(c.tipoConteudo()).isEqualTo("image/png");
        assertThat(c.bytes()).isEqualTo(PNG);
    }

    @Test
    void baixarResponde404ParaChamadoInvisivelAnexoDeOutroChamadoOuArquivoAusente() {
        when(anexos.findByIdAndChamadoId(8L, ID)).thenReturn(Optional.empty());
        when(anexos.findByIdAndChamadoId(9L, ID)).thenReturn(Optional.of(anexo(9L, "k9")));
        when(armazenamento.ler("k9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.baixar(ID, 7L, principal(bruno)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.baixar(ID, 8L, principal(ana)))
                .isInstanceOf(RecursoNaoEncontradoException.class).hasMessage("Anexo não encontrado.");
        assertThatThrownBy(() -> service.baixar(ID, 9L, principal(ana)))
                .isInstanceOf(RecursoNaoEncontradoException.class).hasMessage("Anexo não encontrado.");
    }
}
