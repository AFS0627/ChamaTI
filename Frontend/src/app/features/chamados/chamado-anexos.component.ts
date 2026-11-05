import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { finalize } from 'rxjs';
import { ChamadosService } from '../../core/api/chamados.service';
import { Anexo, Chamado } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { formatarTamanho } from '../../core/util/rotulos';

/** Anexos JPG, PNG e PDF (até 5 MB, 10 por chamado). O download usa o token, então passa por HttpClient. */
@Component({
  selector: 'app-chamado-anexos',
  standalone: true,
  imports: [DatePipe],
  template: `
    <section class="card">
      <h2>Anexos</h2>

      @if (anexos().length === 0) {
        <p class="muted">Nenhum anexo.</p>
      } @else {
        <ul class="lista">
          @for (a of anexos(); track a.id) {
            <li>
              <button type="button" class="link" (click)="baixar(a)">{{ a.nome }}</button>
              <span class="muted">
                {{ tamanho(a.tamanho) }} · {{ a.autor.nome }} · {{ a.criadoEm | date: 'dd/MM/yyyy HH:mm' }}
              </span>
            </li>
          }
        </ul>
      }

      @if (erro(); as mensagem) {
        <p class="alerta erro" role="alert">{{ mensagem }}</p>
      }

      @if (!fechado()) {
        <label class="campo">
          <span>Anexar arquivo (JPG, PNG ou PDF, até 5 MB)</span>
          <input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            [disabled]="enviando()"
            (change)="selecionar($event)"
          />
        </label>
      }
    </section>
  `,
})
export class ChamadoAnexosComponent {
  readonly chamado = input.required<Chamado>();

  private readonly servico = inject(ChamadosService);

  protected readonly anexos = signal<Anexo[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly fechado = computed(() => this.chamado().status === 'FECHADO');
  protected readonly tamanho = formatarTamanho;

  constructor() {
    effect(() => {
      const id = this.chamado().id;
      untracked(() => this.carregar(id));
    });
  }

  protected selecionar(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.item(0);
    if (!arquivo) {
      return;
    }
    const id = this.chamado().id;
    this.enviando.set(true);
    this.erro.set(null);
    this.servico
      .enviarAnexo(id, arquivo)
      .pipe(finalize(() => this.enviando.set(false)))
      .subscribe({
        next: () => {
          campo.value = '';
          this.carregar(id);
        },
        error: (e: unknown) => {
          campo.value = '';
          this.erro.set(mensagemDeErro(e));
        },
      });
  }

  protected baixar(anexo: Anexo): void {
    this.erro.set(null);
    this.servico.baixarAnexo(this.chamado().id, anexo.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = anexo.nome;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }

  private carregar(id: number): void {
    this.servico.anexos(id).subscribe({
      next: (lista) => this.anexos.set(lista),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
