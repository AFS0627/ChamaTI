import { Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ChamadosService } from '../../core/api/chamados.service';
import { AuthService } from '../../core/auth/auth.service';
import { Chamado } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';

/** O solicitante avalia o atendimento (1 a 5 estrelas, comentário opcional) depois de resolvido. */
@Component({
  selector: 'app-chamado-avaliacao',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    @if (podeAvaliar()) {
      <section class="card">
        <h2>Avalie o atendimento</h2>
        @if (enviada()) {
          <p class="alerta ok">Obrigado! Sua avaliação foi registrada.</p>
        } @else {
          <div class="estrelas" role="radiogroup" aria-label="Nota de 1 a 5">
            @for (n of estrelas; track n) {
              <button
                type="button"
                class="estrela"
                [class.ativa]="n <= nota()"
                [attr.aria-label]="n + ' de 5'"
                (click)="nota.set(n)"
              >
                ★
              </button>
            }
          </div>
          <label class="campo">
            <span>Comentário (opcional)</span>
            <textarea [formControl]="comentario" rows="3"></textarea>
            @if (comentario.invalid) {
              <small class="erro">Máximo de 1000 caracteres.</small>
            }
          </label>
          @if (erro(); as mensagem) {
            <p class="alerta erro" role="alert">{{ mensagem }}</p>
          }
          <button
            type="button"
            class="btn btn-primary"
            [disabled]="nota() === 0 || comentario.invalid || ocupado()"
            (click)="enviar()"
          >
            Enviar avaliação
          </button>
        }
      </section>
    }
  `,
})
export class ChamadoAvaliacaoComponent {
  readonly chamado = input.required<Chamado>();

  private readonly servico = inject(ChamadosService);
  private readonly auth = inject(AuthService);

  protected readonly estrelas = [1, 2, 3, 4, 5];
  protected readonly nota = signal(0);
  protected readonly comentario = new FormControl('', { nonNullable: true, validators: Validators.maxLength(1000) });
  protected readonly erro = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly enviada = signal(false);

  protected readonly podeAvaliar = computed(() => {
    const c = this.chamado();
    return (
      this.auth.usuario()?.id === c.solicitante.id && (c.status === 'RESOLVIDO' || c.status === 'FECHADO')
    );
  });

  protected enviar(): void {
    this.ocupado.set(true);
    this.erro.set(null);
    this.servico
      .avaliar(this.chamado().id, this.nota(), this.comentario.value.trim() || null)
      .pipe(finalize(() => this.ocupado.set(false)))
      .subscribe({
        next: () => this.enviada.set(true),
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
  }
}
