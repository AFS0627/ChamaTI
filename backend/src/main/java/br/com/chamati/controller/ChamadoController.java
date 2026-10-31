package br.com.chamati.controller;

import br.com.chamati.dto.AlterarStatusRequest;
import br.com.chamati.dto.AtribuirTecnicoRequest;
import br.com.chamati.dto.ChamadoCriarRequest;
import br.com.chamati.dto.ChamadoResponse;
import br.com.chamati.dto.ErroResponse;
import br.com.chamati.dto.FecharChamadoRequest;
import br.com.chamati.dto.PaginaResponse;
import br.com.chamati.dto.ReabrirChamadoRequest;
import br.com.chamati.dto.ResolverChamadoRequest;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.repository.consulta.ChamadoFiltro;
import br.com.chamati.repository.consulta.ChamadoResumo;
import br.com.chamati.security.UsuarioPrincipal;
import br.com.chamati.service.ChamadoConsultaService;
import br.com.chamati.service.ChamadoGestaoService;
import br.com.chamati.service.ChamadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chamados", description = "Abertura, consulta e gestão do ciclo de vida dos chamados. Visibilidade: ADMIN vê todos; SOLICITANTE só os que abriu; TECNICO vê os atribuídos a ele, a fila sem técnico e os que abriu.")
@RestController
@RequestMapping("/api/chamados")
@Validated
public class ChamadoController {

    private final ChamadoService service;
    private final ChamadoConsultaService consulta;
    private final ChamadoGestaoService gestao;

    public ChamadoController(ChamadoService service, ChamadoConsultaService consulta, ChamadoGestaoService gestao) {
        this.service = service;
        this.consulta = consulta;
        this.gestao = gestao;
    }

    /** O solicitante é sempre o usuário autenticado. */
    @PostMapping
    @Operation(summary = "Abre um chamado",
            description = "O solicitante é sempre o usuário autenticado; solicitante, status, protocolo, técnico e prazos enviados no corpo são ignorados. Prioridade omitida vira MEDIA; CRITICA só pode ser usada por TECNICO e ADMIN. Gera o protocolo (CT-AAAA-NNNNNN), os limites de SLA e o evento CRIACAO no histórico.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chamado aberto, com status ABERTO.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChamadoResponse.class))),
            @ApiResponse(responseCode = "422", description = "Prioridade CRITICA por solicitante, categoria inválida ou inativa, subcategoria inválida, inativa ou de outra categoria, ou política de SLA ausente.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<ChamadoResponse> abrir(@Valid @RequestBody ChamadoCriarRequest req,
                                                 @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.abrir(req, atual));
    }

    /** Período: criadoDe inclusivo e criadoAte exclusivo, em ISO-8601 com Z (ex.: 2026-10-19T00:00:00Z). */
    @GetMapping
    @Operation(summary = "Lista chamados",
            description = "Aplica sempre a regra de visibilidade do perfil, mesmo que os filtros peçam outros chamados. Paginado; a ordenação sempre desempata por id decrescente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de chamados."),
            @ApiResponse(responseCode = "422", description = "criadoDe é posterior a criadoAte.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<ChamadoResumo> listar(
            @Parameter(description = "Protocolo exato (ex.: CT-2026-000012).") @RequestParam(required = false) @Size(max = 20) String protocolo,
            @Parameter(description = "Texto buscado no título e no protocolo.") @RequestParam(required = false) @Size(max = 100) String q,
            @Parameter(description = "Filtra pelo status.") @RequestParam(required = false) StatusChamado status,
            @Parameter(description = "Filtra pela prioridade.") @RequestParam(required = false) Prioridade prioridade,
            @Parameter(description = "Filtra pela categoria.") @RequestParam(required = false) Long categoriaId,
            @Parameter(description = "Filtra pelo técnico responsável.") @RequestParam(required = false) Long tecnicoId,
            @Parameter(description = "true = somente chamados sem técnico (fila).") @RequestParam(defaultValue = "false") boolean semTecnico,
            @Parameter(description = "true = somente chamados com SLA de resolução atrasado.") @RequestParam(defaultValue = "false") boolean atrasados,
            @Parameter(description = "Início do período de abertura (inclusivo), ISO-8601 com Z. Ex.: 2026-10-19T00:00:00Z.") @RequestParam(required = false) Instant criadoDe,
            @Parameter(description = "Fim do período de abertura (exclusivo), ISO-8601 com Z.") @RequestParam(required = false) Instant criadoAte,
            @Parameter(description = "Campo de ordenação: criadoEm, atualizadoEm, protocolo ou titulo.") @RequestParam(defaultValue = "criadoEm")
            @Pattern(regexp = "criadoEm|atualizadoEm|protocolo|titulo") String ordenarPor,
            @Parameter(description = "Direção da ordenação: asc ou desc.") @RequestParam(defaultValue = "desc") @Pattern(regexp = "(?i)asc|desc") String direcao,
            @Parameter(description = "Número da página, começando em 0.") @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página (1 a 100).") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        ChamadoFiltro filtro = new ChamadoFiltro(q, status, prioridade, categoriaId, null, tecnicoId,
                semTecnico, atrasados, criadoDe, criadoAte, protocolo, null);
        return consulta.listar(filtro, ordenarPor, direcao, pagina, tamanho, atual);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um chamado",
            description = "Detalhe completo, com a situação do SLA calculada no momento da consulta. Fora da visibilidade do usuário o chamado responde 404 (igual a inexistente).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChamadoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ChamadoResponse buscar(@Parameter(description = "Identificador numérico do chamado (não é o protocolo).") @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return consulta.buscar(id, atual);
    }

    @PutMapping("/{id}/tecnico")
    @Operation(summary = "Atribui ou troca o técnico responsável",
            description = "ADMIN atribui qualquer técnico ativo; TECNICO só pode assumir para si um chamado que ainda não tem técnico. Não muda o status. Registra o evento ATRIBUICAO. Chamados RESOLVIDO ou FECHADO não aceitam atribuição. Fora da visibilidade do usuário o chamado responde 404 (igual a inexistente).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Técnico atribuído.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChamadoResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil ou usuário sem permissão para esta atribuição.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Versão informada desatualizada ou chamado alterado ao mesmo tempo por outra pessoa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Técnico inexistente, inativo ou sem perfil TECNICO; técnico já atribuído; ou chamado resolvido/fechado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ChamadoResponse atribuirTecnico(@Parameter(description = "Identificador numérico do chamado (não é o protocolo).") @PathVariable Long id, @Valid @RequestBody AtribuirTecnicoRequest req,
                                           @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return gestao.atribuirTecnico(id, req, atual);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Altera o status em andamento",
            description = "ADMIN ou o técnico responsável. Transições: ABERTO para EM_ATENDIMENTO (exige técnico atribuído); EM_ATENDIMENTO, AGUARDANDO_USUARIO e AGUARDANDO_TERCEIRO circulam entre si. Resolver e fechar têm operações próprias. A observação fica no histórico e é visível ao solicitante. Fora da visibilidade do usuário o chamado responde 404 (igual a inexistente).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status alterado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChamadoResponse.class))),
            @ApiResponse(responseCode = "403", description = "Somente ADMIN ou o técnico responsável.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Versão informada desatualizada ou chamado alterado ao mesmo tempo por outra pessoa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Transição inválida, destino RESOLVIDO ou FECHADO, ou início de atendimento sem técnico.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ChamadoResponse alterarStatus(@Parameter(description = "Identificador numérico do chamado (não é o protocolo).") @PathVariable Long id, @Valid @RequestBody AlterarStatusRequest req,
                                         @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return gestao.alterarStatus(id, req, atual);
    }

    @PostMapping("/{id}/resolver")
    @Operation(summary = "Resolve o chamado",
            description = "ADMIN ou o técnico responsável, a partir de EM_ATENDIMENTO, AGUARDANDO_USUARIO ou AGUARDANDO_TERCEIRO, e com técnico atribuído. Grava a solução e a data de resolução. Fora da visibilidade do usuário o chamado responde 404 (igual a inexistente).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado resolvido.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChamadoResponse.class))),
            @ApiResponse(responseCode = "403", description = "Somente ADMIN ou o técnico responsável.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Versão informada desatualizada ou chamado alterado ao mesmo tempo por outra pessoa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Status atual não permite resolver, ou chamado sem técnico.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ChamadoResponse resolver(@Parameter(description = "Identificador numérico do chamado (não é o protocolo).") @PathVariable Long id, @Valid @RequestBody ResolverChamadoRequest req,
                                    @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return gestao.resolver(id, req, atual);
    }

    @PostMapping("/{id}/fechar")
    @Operation(summary = "Fecha o chamado",
            description = "ADMIN ou o solicitante do chamado, somente a partir de RESOLVIDO. A data de resolução é preservada. O corpo é opcional e serve apenas para informar a versão. Fora da visibilidade do usuário o chamado responde 404 (igual a inexistente).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado fechado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChamadoResponse.class))),
            @ApiResponse(responseCode = "403", description = "Somente ADMIN ou o solicitante do chamado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Versão informada desatualizada ou chamado alterado ao mesmo tempo por outra pessoa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "O chamado não está RESOLVIDO.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ChamadoResponse fechar(@Parameter(description = "Identificador numérico do chamado (não é o protocolo).") @PathVariable Long id, @RequestBody(required = false) FecharChamadoRequest req,
                                  @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return gestao.fechar(id, req == null ? null : req.versao(), atual);
    }

    @PostMapping("/{id}/reabrir")
    @Operation(summary = "Reabre o chamado",
            description = "ADMIN, solicitante ou técnico responsável, a partir de RESOLVIDO ou FECHADO. Volta para EM_ATENDIMENTO (se houver técnico) ou ABERTO. Zera as datas de resolução e fechamento, incrementa o total de reaberturas e não altera os limites de SLA; as datas anteriores ficam no evento REABERTURA. Fora da visibilidade do usuário o chamado responde 404 (igual a inexistente).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado reaberto.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChamadoResponse.class))),
            @ApiResponse(responseCode = "403", description = "Somente ADMIN, o solicitante ou o técnico responsável.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Versão informada desatualizada ou chamado alterado ao mesmo tempo por outra pessoa.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "O chamado não está RESOLVIDO nem FECHADO.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ChamadoResponse reabrir(@Parameter(description = "Identificador numérico do chamado (não é o protocolo).") @PathVariable Long id, @Valid @RequestBody ReabrirChamadoRequest req,
                                   @Parameter(hidden = true) @AuthenticationPrincipal UsuarioPrincipal atual) {
        return gestao.reabrir(id, req, atual);
    }
}
