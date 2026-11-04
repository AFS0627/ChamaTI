package br.com.chamati.controller;

import br.com.chamati.dto.AnexoResponse;
import br.com.chamati.dto.ErroResponse;
import br.com.chamati.entity.Anexo;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.AnexoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Anexos", description = "Arquivos JPG, PNG e PDF (até 5 MB cada, no máximo 20 por chamado) anexados a um chamado. Quem vê o chamado vê e baixa os anexos.")
@RestController
@RequestMapping("/api/chamados/{chamadoId}/anexos")
public class AnexoController {

    private final AnexoService anexos;

    public AnexoController(AnexoService anexos) {
        this.anexos = anexos;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Anexa um arquivo ao chamado",
            description = "Envio multipart/form-data com a parte 'arquivo'. Aceita JPG, PNG e PDF de até 5 MB; o tipo é conferido pelo conteúdo, não pelo nome. Chamados FECHADO não recebem anexos. Chamado fora da visibilidade do usuário responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Anexo criado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnexoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parte 'arquivo' ausente.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo maior que 5 MB.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Chamado fechado, arquivo vazio, tipo não permitido ou limite de anexos atingido.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<AnexoResponse> anexar(@Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
                                                @RequestPart("arquivo") MultipartFile arquivo,
                                                @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(anexos.criar(chamadoId, arquivo.getOriginalFilename(), arquivo.getBytes(), atual));
    }

    @GetMapping
    @Operation(summary = "Lista os anexos do chamado",
            description = "Ordem cronológica, sem o conteúdo dos arquivos. Chamado fora da visibilidade do usuário responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anexos do chamado.", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AnexoResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public List<AnexoResponse> listar(@Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
                                      @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return anexos.listar(chamadoId, atual);
    }

    @GetMapping("/{anexoId}")
    @Operation(summary = "Baixa um anexo",
            description = "Devolve o arquivo com Content-Disposition attachment. Chamado ou anexo inexistente, ou chamado fora da visibilidade do usuário, responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conteúdo do arquivo (image/jpeg, image/png ou application/pdf).", content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Chamado ou anexo não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<byte[]> baixar(@Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
                                         @Parameter(description = "Identificador numérico do anexo.") @PathVariable Long anexoId,
                                         @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        Anexo anexo = anexos.baixar(chamadoId, anexoId, atual);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(anexo.getTipoConteudo()))
                .contentLength(anexo.getTamanho())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(anexo.getNomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .body(anexo.getConteudo());
    }
}
