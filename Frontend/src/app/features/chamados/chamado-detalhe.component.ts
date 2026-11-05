import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ChamadosService } from '../../core/api/chamados.service';
import { Chamado } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { rotuloPrioridade, rotuloSla, rotuloStatus } from '../../core/util/rotulos';
import { ChamadoAcoesComponent } from './chamado-acoes.component';
import { ChamadoAnexosComponent } from './chamado-anexos.component';
import { ChamadoAvaliacaoComponent } from './chamado-avaliacao.component';
import { ChamadoComentariosComponent } from './chamado-comentarios.component';
import { ChamadoHistoricoComponent } from './chamado-historico.component';

@Component({
  selector: 'app-chamado-detalhe',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    ChamadoAcoesComponent,
    ChamadoAvaliacaoComponent,
    ChamadoComentariosComponent,
    ChamadoAnexosComponent,
    ChamadoHistoricoComponent,
  ],
  template: `
    <div class="barra">
      <h1>Chamado</h1>
      <a class="btn" routerLink="/chamados">Voltar</a>
    </div>

    @if (erro(); as mensagem) {
      <p class="alerta erro" role="alert">{{ mensagem }}</p>
    }

    @if (chamado(); as c) {
      <section class="card">
        <div class="titulo-chamado">
          <div>
            <small class="muted">{{ c.protocolo }}</small>
            <h2>{{ c.titulo }}</h2>
          </div>
          <div class="selos">
            <span class="badge" [class]="'status-' + c.status">{{ rotuloStatus(c.status) }}</span>
            <span class="badge" [class]="'prio-' + c.prioridade">{{ rotuloPrioridade(c.prioridade) }}</span>
          </div>
        </div>

        <dl class="dados">
          <div><dt>Solicitante</dt><dd>{{ c.solicitante.nome }}</dd></div>
          <div><dt>Técnico</dt><dd>{{ c.tecnico?.nome ?? '—' }}</dd></div>
          <div>
            <dt>Categoria</dt>
            <dd>{{ c.categoria.nome }}@if (c.subcategoria) { / {{ c.subcategoria.nome }} }</dd>
          </div>
          <div><dt>Aberto em</dt><dd>{{ c.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</dd></div>
          <div>
            <dt>1ª resposta até</dt>
            <dd>
              {{ c.slaPrimeiraRespostaLimite ? (c.slaPrimeiraRespostaLimite | date: 'dd/MM/yyyy HH:mm') : '—' }}
              · {{ rotuloSla(c.situacaoSlaPrimeiraResposta) }}
            </dd>
          </div>
          <div>
            <dt>Resolução até</dt>
            <dd>
              {{ c.slaResolucaoLimite ? (c.slaResolucaoLimite | date: 'dd/MM/yyyy HH:mm') : '—' }}
              · {{ rotuloSla(c.situacaoSlaResolucao) }}
            </dd>
          </div>
          @if (c.totalReaberturas > 0) {
            <div><dt>Reaberturas</dt><dd>{{ c.totalReaberturas }}</dd></div>
          }
        </dl>

        <h3>Descrição</h3>
        <p class="texto">{{ c.descricao }}</p>

        @if (c.solucao) {
          <h3>Solução</h3>
          <p class="texto">{{ c.solucao }}</p>
        }
      </section>

      <app-chamado-acoes [chamado]="c" (atualizado)="chamado.set($event)" />
      <app-chamado-avaliacao [chamado]="c" />
      <app-chamado-anexos [chamado]="c" />
      <app-chamado-comentarios [chamado]="c" />
      <app-chamado-historico [chamado]="c" />
    } @else if (!erro()) {
      <p class="muted">Carregando…</p>
    }
  `,
})
export class ChamadoDetalheComponent implements OnInit {
  private readonly servico = inject(ChamadosService);
  private readonly rota = inject(ActivatedRoute);

  protected readonly chamado = signal<Chamado | null>(null);
  protected readonly erro = signal<string | null>(null);

  protected readonly rotuloStatus = rotuloStatus;
  protected readonly rotuloPrioridade = rotuloPrioridade;
  protected readonly rotuloSla = rotuloSla;

  ngOnInit(): void {
    const id = Number(this.rota.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) {
      this.erro.set('Chamado inválido.');
      return;
    }
    this.servico.buscar(id).subscribe({
      next: (c) => this.chamado.set(c),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
