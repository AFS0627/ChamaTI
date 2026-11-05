import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ChamadosService } from '../../core/api/chamados.service';
import { AuthService } from '../../core/auth/auth.service';
import { Chamado, Comentario, TipoComentario } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { rotuloPerfil } from '../../core/util/rotulos';

@Component({
  selector: 'app-chamado-comentarios',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    <section class="card">
      <h2>Comentários</h2>

      @if (comentarios().length === 0) {
        <p class="muted">Nenhum comentário ainda.</p>
      }
      @for (c of comentarios(); track c.id) {
        <article class="comentario" [class.interna]="c.tipo === 'NOTA_INTERNA'">
          <header>
            <strong>{{ c.autor.nome }}</strong>
            <span class="muted">{{ rotuloPerfil(c.autor.perfil) }} · {{ c.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</span>
            @if (c.tipo === 'NOTA_INTERNA') {
              <span class="badge prio-ALTA">Nota interna</span>
            }
          </header>
          <p class="texto">{{ c.conteudo }}</p>
        </article>
      }

      @if (fechado()) {
        <p class="muted">Chamados fechados não recebem novos comentários. Reabra o chamado para continuar.</p>
      } @else {
        <form class="formulario" [formGroup]="form" (ngSubmit)="enviar()">
          @if (podeNotaInterna()) {
            <label class="campo">
              <span>Tipo</span>
              <select formControlName="tipo">
                <option value="PUBLICO">Público (visível ao solicitante)</option>
                <option value="NOTA_INTERNA">Nota interna (somente equipe)</option>
              </select>
            </label>
          }
          <label class="campo">
            <span>Novo comentário</span>
            <textarea formControlName="conteudo" rows="3"></textarea>
          </label>
          @if (erro(); as mensagem) {
            <p class="alerta erro" role="alert">{{ mensagem }}</p>
          }
          <button type="submit" class="btn btn-primary" [disabled]="form.invalid || ocupado()">Comentar</button>
        </form>
      }
    </section>
  `,
})
export class ChamadoComentariosComponent {
  readonly chamado = input.required<Chamado>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servico = inject(ChamadosService);
  private readonly auth = inject(AuthService);

  protected readonly form = this.fb.group({
    tipo: ['PUBLICO' as TipoComentario],
    conteudo: ['', [Validators.required, Validators.maxLength(10000)]],
  });
  protected readonly comentarios = signal<Comentario[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly rotuloPerfil = rotuloPerfil;

  protected readonly fechado = computed(() => this.chamado().status === 'FECHADO');
  /** Solicitantes só comentam em público; a API também impõe isso. */
  protected readonly podeNotaInterna = computed(() => this.auth.usuario()?.perfil !== 'SOLICITANTE');

  constructor() {
    effect(() => {
      const id = this.chamado().id;
      untracked(() => this.carregar(id));
    });
  }

  protected enviar(): void {
    const { tipo, conteudo } = this.form.getRawValue();
    const id = this.chamado().id;
    this.ocupado.set(true);
    this.erro.set(null);
    this.servico
      .comentar(id, this.podeNotaInterna() ? tipo : 'PUBLICO', conteudo.trim())
      .pipe(finalize(() => this.ocupado.set(false)))
      .subscribe({
        next: () => {
          this.form.controls.conteudo.reset();
          this.carregar(id);
        },
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
  }

  private carregar(id: number): void {
    this.servico.comentarios(id).subscribe({
      next: (pagina) => this.comentarios.set(pagina.conteudo),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
