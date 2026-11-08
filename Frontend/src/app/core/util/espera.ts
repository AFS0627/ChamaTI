import { DestroyRef, signal } from '@angular/core';
import { ApiError } from '../http/api-error';

/** Segundos de espera informados pelo backend no 429 (cabeçalho Retry-After), ou null se não houver. */
export function segundosParaNovaTentativa(erro: unknown): number | null {
  if (!(erro instanceof ApiError) || erro.status !== 429) {
    return null;
  }
  const segundos = Number(erro.original.headers.get('Retry-After'));
  return Number.isFinite(segundos) && segundos > 0 ? Math.ceil(segundos) : null;
}

/** Contagem regressiva para bloquear um botão enquanto o backend pede para aguardar. */
export class EsperaPorTentativas {
  /** Segundos que faltam; 0 quando livre. */
  readonly restante = signal(0);

  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(
    destroyRef: DestroyRef,
    private readonly passoMs = 1000,
  ) {
    destroyRef.onDestroy(() => this.parar());
  }

  iniciar(segundos: number): void {
    this.parar();
    this.restante.set(segundos);
    this.timer = setInterval(() => {
      this.restante.update((s) => s - 1);
      if (this.restante() <= 0) {
        this.parar();
      }
    }, this.passoMs);
  }

  private parar(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.restante() < 0) {
      this.restante.set(0);
    }
  }
}
