import { InjectionToken } from '@angular/core';

/** Executa a tarefa de forma exclusiva entre todas as abas do navegador. */
export type TravaRenovacao = <T>(tarefa: () => Promise<T>) => Promise<T>;

const NOME_DA_TRAVA = 'chamati-renovacao-sessao';

/**
 * Web Locks: uma única aba renova por vez; as demais esperam e então reaproveitam o resultado.
 * Sem suporte (navegador antigo), cai para execução direta, que só protege dentro da aba.
 */
export const TRAVA_RENOVACAO = new InjectionToken<TravaRenovacao>('TRAVA_RENOVACAO', {
  providedIn: 'root',
  factory:
    () =>
    <T>(tarefa: () => Promise<T>): Promise<T> =>
      typeof navigator !== 'undefined' && navigator.locks
        ? (navigator.locks.request(NOME_DA_TRAVA, tarefa) as Promise<T>)
        : tarefa(),
});
