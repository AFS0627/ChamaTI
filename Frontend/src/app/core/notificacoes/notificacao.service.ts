import { Injectable, signal } from '@angular/core';

export type TipoNotificacao = 'erro' | 'sucesso' | 'info';

export interface Notificacao {
  id: number;
  tipo: TipoNotificacao;
  texto: string;
}

const MAXIMO_VISIVEL = 4;

/** Avisos globais (toasts). Mensagens iguais já visíveis não se repetem. */
@Injectable({ providedIn: 'root' })
export class NotificacaoService {
  private readonly lista = signal<Notificacao[]>([]);
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();
  private proximoId = 1;

  readonly itens = this.lista.asReadonly();

  erro(texto: string, duracaoMs = 8000): void {
    this.adicionar('erro', texto, duracaoMs);
  }

  sucesso(texto: string, duracaoMs = 5000): void {
    this.adicionar('sucesso', texto, duracaoMs);
  }

  info(texto: string, duracaoMs = 5000): void {
    this.adicionar('info', texto, duracaoMs);
  }

  remover(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this.lista.update((itens) => itens.filter((n) => n.id !== id));
  }

  private adicionar(tipo: TipoNotificacao, texto: string, duracaoMs: number): void {
    if (this.lista().some((n) => n.tipo === tipo && n.texto === texto)) {
      return;
    }
    const id = this.proximoId++;
    this.lista.update((itens) => [...itens, { id, tipo, texto }]);
    if (this.lista().length > MAXIMO_VISIVEL) {
      this.remover(this.lista()[0].id);
    }
    if (duracaoMs > 0) {
      this.timers.set(
        id,
        setTimeout(() => this.remover(id), duracaoMs),
      );
    }
  }
}
