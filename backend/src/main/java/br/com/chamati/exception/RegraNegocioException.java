package br.com.chamati.exception;

/** 422: requisição válida que viola uma regra de negócio. */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
