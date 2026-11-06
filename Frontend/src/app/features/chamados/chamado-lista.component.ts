import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ChamadosService } from '../../core/api/chamados.service';
import { ChamadoResumo, Pagina, Prioridade, StatusChamado } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { PRIORIDADES, STATUS_LISTA, rotuloPrioridade, rotuloStatus } from '../../core/util/rotulos';

@Component({
  selector: 'app-chamado-lista',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  template: `
    <div class="barra">
      <h1>Chamados</h1>
    </div>

    <form class="card filtros" [formGroup]="form" (ngSubmit)="carregar(0)">
      <label class="campo">
        <span>Busca</span>
        <input type="search" formControlName="q" placeholder="Título ou protocolo" />
      </label>
      <label class="campo">
        <span>Status</span>
        <select formControlName="status">
          <option value="">Todos</option>
          @for (s of statusLista; track s) {
            <option [value]="s">{{ rotuloStatus(s) }}</option>
          }
        </select>
      </label>
      <label class="campo">
        <span>Prioridade</span>
        <select formControlName="prioridade">
          <option value="">Todas</option>
          @for (p of prioridades; track p) {
            <option [value]="p">{{ rotuloPrioridade(p) }}</option>
          }
        </select>
      </label>
      <label class="check"><input type="checkbox" formControlName="semTecnico" /> Sem técnico</label>
      <label class="check"><input type="checkbox" formControlName="atrasados" /> Atrasados</label>
      <div class="acoes">
        <button type="submit" class="btn btn-primary">Filtrar</button>
        <button type="button" class="btn" (click)="limpar()">Limpar</button>
      </div>
    </form>

    @if (erro(); as mensagem) {
      <p class="alerta erro" role="alert">{{ mensagem }}</p>
    }

    <div class="card tabela cartoes">
      @if (resultado(); as r) {
        @if (r.conteudo.length === 0) {
          <p class="muted">Nenhum chamado encontrado.</p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Protocolo</th>
                <th>Título</th>
                <th>Status</th>
                <th>Prioridade</th>
                <th>Categoria</th>
                <th>Solicitante</th>
                <th>Técnico</th>
                <th>Aberto em</th>
              </tr>
            </thead>
            <tbody>
              @for (c of r.conteudo; track c.id) {
                <tr>
                  <td data-rotulo="Protocolo"><a [routerLink]="['/chamados', c.id]">{{ c.protocolo }}</a></td>
                  <td data-rotulo="Título">{{ c.titulo }}</td>
                  <td data-rotulo="Status"><span class="badge" [class]="'status-' + c.status">{{ rotuloStatus(c.status) }}</span></td>
                  <td data-rotulo="Prioridade"><span class="badge" [class]="'prio-' + c.prioridade">{{ rotuloPrioridade(c.prioridade) }}</span></td>
                  <td data-rotulo="Categoria">{{ c.categoriaNome }}</td>
                  <td data-rotulo="Solicitante">{{ c.solicitanteNome }}</td>
                  <td data-rotulo="Técnico">{{ c.tecnicoNome ?? '—' }}</td>
                  <td data-rotulo="Aberto em">{{ c.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</td>
                </tr>
              }
            </tbody>
          </table>
          <div class="paginacao">
            <button type="button" class="btn" [disabled]="r.pagina === 0 || carregando()" (click)="carregar(r.pagina - 1)">
              Anterior
            </button>
            <span>Página {{ r.pagina + 1 }} de {{ r.totalPaginas }} · {{ r.totalElementos }} chamado(s)</span>
            <button
              type="button"
              class="btn"
              [disabled]="r.pagina + 1 >= r.totalPaginas || carregando()"
              (click)="carregar(r.pagina + 1)"
            >
              Próxima
            </button>
          </div>
        }
      } @else if (carregando()) {
        <p class="muted">Carregando…</p>
      }
    </div>
  `,
})
export class ChamadoListaComponent implements OnInit {
  private readonly servico = inject(ChamadosService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly rota = inject(ActivatedRoute);

  protected readonly form = this.fb.group({
    q: [''],
    status: ['' as StatusChamado | ''],
    prioridade: ['' as Prioridade | ''],
    semTecnico: [false],
    atrasados: [false],
  });
  protected readonly resultado = signal<Pagina<ChamadoResumo> | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly carregando = signal(false);

  protected readonly statusLista = STATUS_LISTA;
  protected readonly prioridades = PRIORIDADES;
  protected readonly rotuloStatus = rotuloStatus;
  protected readonly rotuloPrioridade = rotuloPrioridade;

  ngOnInit(): void {
    // Os cartões do dashboard apontam para cá com filtros na URL.
    const query = this.rota.snapshot.queryParamMap;
    this.form.patchValue({
      status: STATUS_LISTA.find((s) => s === query.get('status')) ?? '',
      semTecnico: query.get('semTecnico') === 'true',
      atrasados: query.get('atrasados') === 'true',
    });
    this.carregar(0);
  }

  protected limpar(): void {
    this.form.reset();
    this.carregar(0);
  }

  protected carregar(pagina: number): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.servico
      .listar({ ...this.form.getRawValue(), pagina })
      .pipe(finalize(() => this.carregando.set(false)))
      .subscribe({
        next: (r) => this.resultado.set(r),
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
  }
}
