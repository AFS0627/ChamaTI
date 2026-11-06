import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { ChamadosService } from '../../core/api/chamados.service';
import { Chamado, Historico } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { rotuloEvento, rotuloValorHistorico } from '../../core/util/rotulos';

@Component({
  selector: 'app-chamado-historico',
  standalone: true,
  imports: [DatePipe],
  template: `
    <section class="card">
      <h2>Histórico</h2>
      @if (erro(); as mensagem) {
        <p class="alerta erro" role="alert">{{ mensagem }}</p>
      }
      @if (eventos().length === 0) {
        <p class="muted">Sem eventos.</p>
      } @else {
        <ol class="linha-do-tempo">
          @for (e of eventos(); track e.id) {
            <li>
              <span class="muted">{{ e.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</span>
              <strong>{{ rotuloEvento(e.tipoEvento) }}</strong>
              @if (e.descricao) {
                <span>{{ e.descricao }}</span>
              }
              @if (e.valorAnterior || e.valorNovo) {
                <span class="muted">{{ rotuloValor(e.valorAnterior) }} → {{ rotuloValor(e.valorNovo) }}</span>
              }
              @if (e.autor) {
                <span class="muted">por {{ e.autor.nome }}</span>
              }
            </li>
          }
        </ol>
      }
    </section>
  `,
})
export class ChamadoHistoricoComponent {
  /** A cada nova versão do chamado (após uma ação) o histórico é recarregado. */
  readonly chamado = input.required<Chamado>();

  private readonly servico = inject(ChamadosService);

  protected readonly eventos = signal<Historico[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly rotuloEvento = rotuloEvento;
  protected readonly rotuloValor = rotuloValorHistorico;

  constructor() {
    effect(() => {
      const c = this.chamado();
      untracked(() => this.carregar(c.id));
    });
  }

  private carregar(id: number): void {
    this.servico.historico(id).subscribe({
      next: (pagina) => this.eventos.set(pagina.conteudo),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
