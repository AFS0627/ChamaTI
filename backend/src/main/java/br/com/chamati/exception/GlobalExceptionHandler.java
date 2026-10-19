package br.com.chamati.exception;

import br.com.chamati.dto.ErroResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Mensagens de erro nunca incluem valores enviados (ex.: senhas) nem detalhes internos. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    ResponseEntity<ErroResponse> conflito(ConflitoException e) {
        return resposta(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResponse> corpoInvalido(MethodArgumentNotValidException e) {
        List<ErroResponse.Campo> campos = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErroResponse.Campo(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErroResponse(
                java.time.Instant.now(), 400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Dados inválidos.", campos));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErroResponse> parametroInvalido(ConstraintViolationException e) {
        List<ErroResponse.Campo> campos = e.getConstraintViolations().stream()
                .map(v -> new ErroResponse.Campo(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErroResponse(
                java.time.Instant.now(), 400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Parâmetros inválidos.", campos));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErroResponse> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + e.getName() + "'.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErroResponse> corpoIlegivel(HttpMessageNotReadableException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido.");
    }

    /** Última defesa contra corrida em restrições únicas (ex.: e-mail duplicado simultâneo). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException e) {
        log.warn("Violação de integridade: {}", e.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "A operação conflita com dados já existentes.");
    }

    @ExceptionHandler(AutenticacaoException.class)
    ResponseEntity<ErroResponse> naoAutenticado(AutenticacaoException e) {
        return resposta(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TentativasExcedidasException.class)
    ResponseEntity<ErroResponse> muitasTentativas(TentativasExcedidasException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(e.getSegundos()))
                .body(ErroResponse.de(429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), e.getMessage()));
    }

    private static ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status)
                .body(ErroResponse.de(status.value(), status.getReasonPhrase(), mensagem));
    }
}
