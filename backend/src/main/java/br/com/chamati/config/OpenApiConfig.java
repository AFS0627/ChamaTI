package br.com.chamati.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Metadados da documentação e respostas padrão derivadas do próprio código:
 * 400 para operações com parâmetros/corpo, 401 para as que exigem token e 403 para as restritas por @PreAuthorize.
 * Respostas já declaradas com @ApiResponse nos controllers não são sobrescritas.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "chamaTI API",
                version = "v1",
                description = """
                        API do sistema de chamados de TI.

                        **Autenticação:** faça `POST /api/auth/login`, copie o `accessToken` e informe-o em *Authorize*.
                        O access token vale 15 minutos; use `POST /api/auth/refresh` com o refresh token (rotativo, uso único) \
                        para obter outro par.

                        **Perfis:** `ADMIN`, `TECNICO` e `SOLICITANTE`. Cada operação indica quem pode executá-la.

                        **Erros:** respostas de erro de negócio e validação seguem o modelo `ErroResponse`. \
                        As respostas 401 e as 403 de acesso por perfil (restrição de ADMIN) não têm corpo.
                        """),
        security = @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER))
@SecurityScheme(
        name = OpenApiConfig.ESQUEMA_BEARER,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token JWT (HS256) obtido em /api/auth/login ou /api/auth/refresh.")
public class OpenApiConfig {

    public static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    OperationCustomizer respostasPadrao() {
        return (operation, handlerMethod) -> {
            Method metodo = handlerMethod.getMethod();
            Class<?> controller = handlerMethod.getBeanType();
            ApiResponses respostas = operation.getResponses();
            if (respostas == null) {
                respostas = new ApiResponses();
                operation.setResponses(respostas);
            }
            if (temEntrada(metodo)) {
                adicionar(respostas, "400", "Requisição inválida: corpo, parâmetro ou identificador com formato ou valor incorreto.", true);
            }
            if (!AnnotatedElementUtils.hasAnnotation(metodo, SecurityRequirements.class)) {
                adicionar(respostas, "401", "Token ausente, inválido ou expirado (sem corpo).", false);
            }
            boolean restritoAAdmin = AnnotatedElementUtils.hasAnnotation(metodo, PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(controller, PreAuthorize.class);
            if (restritoAAdmin) {
                adicionar(respostas, "403", "Perfil sem permissão: operação restrita a ADMIN (sem corpo).", false);
            }
            return operation;
        };
    }

    private static boolean temEntrada(Method metodo) {
        for (Parameter p : metodo.getParameters()) {
            if (p.isAnnotationPresent(RequestBody.class) || p.isAnnotationPresent(RequestParam.class)
                    || p.isAnnotationPresent(PathVariable.class)) {
                return true;
            }
        }
        return false;
    }

    private static void adicionar(ApiResponses respostas, String codigo, String descricao, boolean comCorpoDeErro) {
        if (respostas.containsKey(codigo)) {
            return;
        }
        ApiResponse r = new ApiResponse().description(descricao);
        if (comCorpoDeErro) {
            r.content(new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErroResponse"))));
        }
        respostas.addApiResponse(codigo, r);
    }
}
