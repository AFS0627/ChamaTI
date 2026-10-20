package br.com.chamati.service;

import br.com.chamati.repository.SequenciaProtocoloRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera protocolos únicos e sem lacunas usando a linha do ano em sequencias_protocolo:
 * o INSERT ... ON DUPLICATE KEY UPDATE trava a linha até o fim da transação, serializando
 * acessos simultâneos, e LAST_INSERT_ID devolve o número da própria conexão.
 * Exige transação em andamento (mesma conexão); se ela for desfeita, o número não é consumido.
 */
@Service
public class ProtocoloService {

    private final SequenciaProtocoloRepository sequencias;

    public ProtocoloService(SequenciaProtocoloRepository sequencias) {
        this.sequencias = sequencias;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String gerar(Instant agora) {
        int ano = agora.atZone(ZoneOffset.UTC).getYear();
        sequencias.proximoNumero(ano);
        Long numero = sequencias.ultimoIdGerado();
        if (numero == null || numero < 1) {
            throw new IllegalStateException("Falha ao gerar o número do protocolo.");
        }
        return ProtocoloFormato.formatar(ano, numero);
    }
}
