import { Injectable } from '@angular/core';
import { Sessao, lerSessao } from './sessao';

const CHAVE = 'chamati.sessao';

/**
 * Guarda a sessão no localStorage, que é compartilhado entre as abas. Isso importa: o refresh token é de
 * uso único, e uma aba com o token antigo na memória não pode renová-lo depois de outra aba já ter feito isso.
 */
@Injectable({ providedIn: 'root' })
export class ArmazenamentoSessao {
  ler(): Sessao | null {
    try {
      return lerSessao(localStorage.getItem(CHAVE));
    } catch {
      return null;
    }
  }

  gravar(sessao: Sessao): void {
    try {
      localStorage.setItem(CHAVE, JSON.stringify(sessao));
    } catch {
      // armazenamento indisponível: a sessão vale só enquanto a página estiver aberta
    }
  }

  limpar(): void {
    try {
      localStorage.removeItem(CHAVE);
    } catch {
      // idem
    }
  }

  /** Avisa quando OUTRA aba muda a sessão (o evento `storage` não dispara na aba que gravou). */
  aoMudarEmOutraAba(ouvinte: (sessao: Sessao | null) => void): void {
    window.addEventListener('storage', (evento: StorageEvent) => {
      // key === null: outra aba chamou localStorage.clear()
      if (evento.key === CHAVE || evento.key === null) {
        ouvinte(lerSessao(evento.newValue));
      }
    });
  }
}
