package br.com.chamati.controller;

import br.com.chamati.dto.AnexoResponse;
import br.com.chamati.dto.ErroResponse;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Anexos", description = "Arquivos JPG, PNG e PDF anexados a um chamado. Quem enxerga o chamado lista, baixa e anexa.")
@RestController
@RequestMapping("/api/chamados/{chamadoId}/anexos")
@Validated
public class AnexoController {

    private final AnexoService anexos;

    public AnexoController(AnexoService anexos) {
        this.anexos = anexos;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Anexa um arquivo ao chamado",
            description = "Corpo multipart/form-data com o arquivo no campo 'arquivo'. Aceita JPG, PNG e PDF (o tipo é identificado pelo conteúdo, não pela extensão), até 5 MB e 10 anexos por chamado. Chamados FECHADO não recebem anexos. Registra o evento EDICAO no histórico. Chamado fora da visibilidade do usuário responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Anexo criado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnexoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo maior que o limite permitido.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Arquivo vazio, tipo não permitido, limite de anexos atingido ou chamado fechado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<AnexoResponse> enviar(
            @Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
            @RequestPart("arquivo") MultipartFile arquivo,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexos.criar(chamadoId, arquivo, atual));
    }

    @GetMapping
    @Operation(summary = "Lista os anexos do chamado",
            description = "Ordem cronológica. Chamado fora da visibilidade do usuário responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anexos do chamado.", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AnexoResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public List<AnexoResponse> listar(
            @Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return anexos.listar(chamadoId, atual);
    }

    @GetMapping("/{anexoId}/conteudo")
    @Operation(summary = "Baixa o arquivo do anexo",
            description = "Devolve os bytes do arquivo (Content-Disposition: attachment). Exige o token Bearer, então o cliente deve buscar o arquivo com o cabeçalho Authorization e não por um link direto. Chamado ou anexo inexistente, ou fora da visibilidade do usuário, responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conteúdo do arquivo.", content = {
                    @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))}),
            @ApiResponse(responseCode = "404", description = "Chamado ou anexo não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<byte[]> baixar(
            @Parameter(description = "Identificador numérico do chamado.") @PathVariable Long chamadoId,
            @Parameter(description = "Identificador numérico do anexo.") @PathVariable Long anexoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        AnexoService.Conteudo c = anexos.baixar(chamadoId, anexoId, atual);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.tipoConteudo()))
                .contentLength(c.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(c.nome(), StandardCharsets.UTF_8).build().toString())
                .body(c.bytes());
    }
}
