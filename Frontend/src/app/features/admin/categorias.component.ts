import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { CategoriasService } from '../../core/api/categorias.service';
import { Categoria } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <h1>Categorias</h1>

    <form class="card formulario linha" [formGroup]="form" (ngSubmit)="criar()" novalidate>
      <label class="campo">
        <span>Nome</span>
        <input type="text" formControlName="nome" maxlength="80" />
      </label>
      <label class="campo largo">
        <span>Descrição (opcional)</span>
        <input type="text" formControlName="descricao" maxlength="255" />
      </label>
      <button type="submit" class="btn btn-primary" [disabled]="salvando() || form.invalid">Criar categoria</button>
    </form>

    @if (erro(); as mensagem) {
      <p class="alerta erro" role="alert">{{ mensagem }}</p>
    }

    <div class="card tabela">
      @if (categorias(); as lista) {
        @if (lista.length === 0) {
          <p class="muted">Nenhuma categoria cadastrada.</p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Nome</th>
                <th>Descrição</th>
                <th>Situação</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (c of lista; track c.id) {
                <tr>
                  <td>{{ c.nome }}</td>
                  <td>{{ c.descricao ?? '—' }}</td>
                  <td>
                    <span class="badge" [class]="c.ativo ? 'status-ABERTO' : 'status-FECHADO'">
                      {{ c.ativo ? 'Ativa' : 'Inativa' }}
                    </span>
                  </td>
                  <td>
                    <button type="button" class="btn" [disabled]="salvando()" (click)="alternar(c)">
                      {{ c.ativo ? 'Desativar' : 'Ativar' }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      } @else {
        <p class="muted">Carregando…</p>
      }
    </div>
  `,
})
export class CategoriasComponent implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servico = inject(CategoriasService);

  protected readonly form = this.fb.group({
    nome: ['', [Validators.required, Validators.maxLength(80)]],
    descricao: ['', Validators.maxLength(255)],
  });
  protected readonly categorias = signal<Categoria[] | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly salvando = signal(false);

  ngOnInit(): void {
    this.carregar();
  }

  protected criar(): void {
    const v = this.form.getRawValue();
    this.executar(this.servico.criar(v.nome.trim(), v.descricao.trim() || null), () => this.form.reset());
  }

  protected alternar(categoria: Categoria): void {
    this.executar(categoria.ativo ? this.servico.desativar(categoria.id) : this.servico.ativar(categoria.id));
  }

  private executar(operacao: ReturnType<CategoriasService['criar']>, aoConcluir?: () => void): void {
    this.salvando.set(true);
    this.erro.set(null);
    operacao.pipe(finalize(() => this.salvando.set(false))).subscribe({
      next: () => {
        aoConcluir?.();
        this.carregar();
      },
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }

  private carregar(): void {
    this.servico.listar(0, 100).subscribe({
      next: (pagina) => this.categorias.set(pagina.conteudo),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
