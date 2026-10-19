package br.com.chamati.security;

import br.com.chamati.exception.TentativasExcedidasException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas de login por chave (e-mail + IP), em memória: 5 falhas em 15 minutos
 * bloqueiam a chave por 15 minutos. Vale por instância; com várias instâncias, mover para um
 * armazenamento compartilhado.
 */
@Component
public class TentativasLogin {

    static final int MAX_FALHAS = 5;
    static final Duration JANELA = Duration.ofMinutes(15);
    static final Duration BLOQUEIO = Duration.ofMinutes(15);
    private static final int LIMITE_DE_CHAVES = 10_000;

    private record Estado(int falhas, Instant inicio, Instant bloqueadoAte) {
    }

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();
    private final Clock clock;

    public TentativasLogin(Clock clock) {
        this.clock = clock;
    }

    /** @throws TentativasExcedidasException se a chave estiver bloqueada */
    public void verificar(String chave) {
        Estado e = estados.get(chave);
        if (e == null || e.bloqueadoAte() == null) {
            return;
        }
        Instant agora = clock.instant();
        if (agora.isBefore(e.bloqueadoAte())) {
            throw new TentativasExcedidasException(Duration.between(agora, e.bloqueadoAte()).toSeconds() + 1);
        }
        estados.remove(chave, e);
    }

    public void registrarFalha(String chave) {
        Instant agora = clock.instant();
        if (estados.size() > LIMITE_DE_CHAVES) {
            estados.values().removeIf(s -> expirou(s, agora));
        }
        estados.compute(chave, (k, e) -> {
            if (e == null || expirou(e, agora)) {
                return new Estado(1, agora, null);
            }
            int falhas = e.falhas() + 1;
            return falhas >= MAX_FALHAS
                    ? new Estado(falhas, e.inicio(), agora.plus(BLOQUEIO))
                    : new Estado(falhas, e.inicio(), null);
        });
    }

    public void limpar(String chave) {
        estados.remove(chave);
    }

    private static boolean expirou(Estado e, Instant agora) {
        return e.bloqueadoAte() != null
                ? !agora.isBefore(e.bloqueadoAte())
                : agora.isAfter(e.inicio().plus(JANELA));
    }
}
