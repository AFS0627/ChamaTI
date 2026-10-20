package br.com.chamati.exception;

/** 403: usuário autenticado e com acesso ao recurso, mas sem permissão para a ação. */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
