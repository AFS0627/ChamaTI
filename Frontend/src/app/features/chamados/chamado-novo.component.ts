import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { CategoriasService } from '../../core/api/categorias.service';
import { ChamadosService } from '../../core/api/chamados.service';
import { AuthService } from '../../core/auth/auth.service';
import { Categoria, Prioridade, Subcategoria } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { PRIORIDADES, rotuloPrioridade } from '../../core/util/rotulos';

@Component({
  selector: 'app-chamado-novo',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="barra">
      <h1>Novo chamado</h1>
      <a class="btn" routerLink="/chamados">Voltar</a>
    </div>

    <form class="card formulario" [formGroup]="form" (ngSubmit)="enviar()" novalidate>
      <label class="campo">
        <span>Título</span>
        <input type="text" formControlName="titulo" maxlength="150" />
        @if (form.controls.titulo.touched && form.controls.titulo.invalid) {
          <small class="erro">Informe o título (até 150 caracteres).</small>
        }
      </label>

      <label class="campo">
        <span>Descrição</span>
        <textarea formControlName="descricao" rows="6"></textarea>
        @if (form.controls.descricao.touched && form.controls.descricao.invalid) {
          <small class="erro">Descreva o problema.</small>
        }
      </label>

      <div class="linha">
        <label class="campo">
          <span>Categoria</span>
          <select formControlName="categoriaId">
            <option [ngValue]="null">Selecione…</option>
            @for (c of categorias(); track c.id) {
              <option [ngValue]="c.id">{{ c.nome }}</option>
            }
          </select>
          @if (form.controls.categoriaId.touched && form.controls.categoriaId.invalid) {
            <small class="erro">Escolha uma categoria.</small>
          }
        </label>

        <label class="campo">
          <span>Subcategoria (opcional)</span>
          <select formControlName="subcategoriaId">
            <option [ngValue]="null">—</option>
            @for (s of subcategorias(); track s.id) {
              <option [ngValue]="s.id">{{ s.nome }}</option>
            }
          </select>
        </label>

        <label class="campo">
          <span>Prioridade</span>
          <select formControlName="prioridade">
            @for (p of prioridadesPermitidas(); track p) {
              <option [value]="p">{{ rotuloPrioridade(p) }}</option>
            }
          </select>
        </label>
      </div>

      @if (erro(); as mensagem) {
        <p class="alerta erro" role="alert">{{ mensagem }}</p>
      }

      <div class="acoes">
        <button type="submit" class="btn btn-primary" [disabled]="enviando()">
          {{ enviando() ? 'Enviando…' : 'Abrir chamado' }}
        </button>
      </div>
    </form>
  `,
})
export class ChamadoNovoComponent implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly chamados = inject(ChamadosService);
  private readonly catalogo = inject(CategoriasService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = this.fb.group({
    titulo: ['', [Validators.required, Validators.maxLength(150)]],
    descricao: ['', [Validators.required, Validators.maxLength(10000)]],
    categoriaId: this.fb.control<number | null>(null, Validators.required),
    subcategoriaId: this.fb.control<number | null>(null),
    prioridade: ['MEDIA' as Prioridade],
  });
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly subcategorias = signal<Subcategoria[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly enviando = signal(false);

  /** A prioridade CRITICA é reservada a técnicos e administradores. */
  protected readonly prioridadesPermitidas = computed(() =>
    this.auth.usuario()?.perfil === 'SOLICITANTE' ? PRIORIDADES.filter((p) => p !== 'CRITICA') : PRIORIDADES,
  );
  protected readonly rotuloPrioridade = rotuloPrioridade;

  ngOnInit(): void {
    this.catalogo.ativas().subscribe({
      next: (lista) => this.categorias.set(lista),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
    this.form.controls.categoriaId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => this.carregarSubcategorias(id));
  }

  protected enviar(): void {
    const v = this.form.getRawValue();
    if (this.form.invalid || v.categoriaId === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    this.chamados
      .abrir({
        titulo: v.titulo.trim(),
        descricao: v.descricao.trim(),
        categoriaId: v.categoriaId,
        subcategoriaId: v.subcategoriaId,
        prioridade: v.prioridade,
      })
      .pipe(finalize(() => this.enviando.set(false)))
      .subscribe({
        next: (c) => void this.router.navigate(['/chamados', c.id]),
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
  }

  private carregarSubcategorias(categoriaId: number | null): void {
    this.form.controls.subcategoriaId.setValue(null);
    this.subcategorias.set([]);
    if (categoriaId === null) {
      return;
    }
    this.catalogo.subcategorias(categoriaId).subscribe({
      next: (lista) => this.subcategorias.set(lista.filter((s) => s.ativo)),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
