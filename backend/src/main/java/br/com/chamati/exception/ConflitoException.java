package br.com.chamati.exception;

/** 409: duplicidade ou conflito com o estado atual. */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
