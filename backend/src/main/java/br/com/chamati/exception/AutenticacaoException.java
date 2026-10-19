package br.com.chamati.exception;

/** 401: credenciais ou sessão inválidas. A mensagem nunca revela o motivo exato. */
public class AutenticacaoException extends RuntimeException {
    public AutenticacaoException(String mensagem) {
        super(mensagem);
    }
}
