package br.com.chamati.exception;

/** 429: login temporariamente bloqueado por excesso de falhas. */
public class TentativasExcedidasException extends RuntimeException {

    private final long segundos;

    public TentativasExcedidasException(long segundos) {
        super("Muitas tentativas. Tente novamente mais tarde.");
        this.segundos = segundos;
    }

    public long getSegundos() {
        return segundos;
    }
}
