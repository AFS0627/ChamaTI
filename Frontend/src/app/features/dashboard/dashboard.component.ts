import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { DashboardService } from '../../core/api/dashboard.service';
import { Dashboard } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { PRIORIDADES, STATUS_LISTA, rotuloPrioridade, rotuloStatus } from '../../core/util/rotulos';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <div class="barra">
      <h1>Dashboard</h1>
      <label class="inline">
        <span>Período</span>
        <select (change)="alterarDias($event)">
          @for (d of opcoesDias; track d) {
            <option [value]="d" [selected]="d === dias()">Últimos {{ d }} dias</option>
          }
        </select>
      </label>
    </div>

    @if (erro(); as mensagem) {
      <p class="alerta erro" role="alert">{{ mensagem }}</p>
    }
    @if (carregando() && !dados()) {
      <p class="muted">Carregando…</p>
    }

    @if (dados(); as d) {
      <section class="grade" aria-label="Indicadores">
        <a class="card indicador tom-azul" routerLink="/chamados">
          <strong>{{ d.totais.emAberto }}</strong><span>Em aberto</span>
        </a>
        <a class="card indicador tom-ambar" routerLink="/chamados" [queryParams]="{ semTecnico: true }">
          <strong>{{ d.totais.semTecnico }}</strong><span>Sem técnico (fila)</span>
        </a>
        <a class="card indicador tom-vermelho" routerLink="/chamados" [queryParams]="{ atrasados: true }">
          <strong>{{ d.totais.emAtrasoResolucao }}</strong><span>Resolução atrasada</span>
        </a>
        <div class="card indicador tom-vermelho">
          <strong>{{ d.totais.emAtrasoPrimeiraResposta }}</strong><span>1ª resposta atrasada</span>
        </div>
        <div class="card indicador tom-azul">
          <strong>{{ d.totais.atribuidosAMim }}</strong><span>Atribuídos a mim</span>
        </div>
        <div class="card indicador">
          <strong>{{ d.totais.total }}</strong><span>Total visível</span>
        </div>
      </section>

      <section class="duas-colunas">
        <div class="card">
          <h2>Por status</h2>
          <table>
            <tbody>
              @for (s of statusLista; track s) {
                <tr>
                  <td>{{ rotuloStatus(s) }}</td>
                  <td class="celula-medidor">
                    <div class="medidor" aria-hidden="true">
                      <span [class]="'status-' + s" [style.width.%]="percentual(d.porStatus[s], maxStatus(d))"></span>
                    </div>
                  </td>
                  <td class="num">{{ d.porStatus[s] }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="card">
          <h2>Em aberto por prioridade</h2>
          <table>
            <tbody>
              @for (p of prioridades; track p) {
                <tr>
                  <td>{{ rotuloPrioridade(p) }}</td>
                  <td class="celula-medidor">
                    <div class="medidor" aria-hidden="true">
                      <span [class]="'prio-' + p" [style.width.%]="percentual(d.emAbertoPorPrioridade[p], maxPrioridade(d))"></span>
                    </div>
                  </td>
                  <td class="num">{{ d.emAbertoPorPrioridade[p] }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <section class="duas-colunas">
        <div class="card">
          <h2>Categorias com mais chamados em aberto</h2>
          @if (d.emAbertoPorCategoria.length === 0) {
            <p class="muted">Nenhum chamado em aberto.</p>
          } @else {
            <table>
              <tbody>
                @for (c of d.emAbertoPorCategoria; track c.categoriaId) {
                  <tr>
                    <td>{{ c.nome }}</td>
                    <td class="celula-medidor">
                      <div class="medidor" aria-hidden="true">
                        <span [style.width.%]="percentual(c.total, maxCategoria(d))"></span>
                      </div>
                    </td>
                    <td class="num">{{ c.total }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
        <div class="card">
          <h2>Últimos {{ d.periodoDias }} dias</h2>
          <table>
            <tbody>
              <tr>
                <td>Abertos</td>
                <td class="num">{{ d.periodo.abertos }}</td>
              </tr>
              <tr>
                <td>Resolvidos ou fechados</td>
                <td class="num">{{ d.periodo.resolvidos }}</td>
              </tr>
            </tbody>
          </table>
          <p class="muted">Desde {{ d.periodo.desde | date: 'dd/MM/yyyy HH:mm' }}</p>
        </div>
      </section>
    }
  `,
})
export class DashboardComponent implements OnInit {
  private readonly servico = inject(DashboardService);

  protected readonly opcoesDias = [7, 30, 90];
  protected readonly dias = signal(30);
  protected readonly dados = signal<Dashboard | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly carregando = signal(false);

  protected readonly statusLista = STATUS_LISTA;
  protected readonly prioridades = PRIORIDADES;
  protected readonly rotuloStatus = rotuloStatus;
  protected readonly rotuloPrioridade = rotuloPrioridade;

  ngOnInit(): void {
    this.carregar();
  }

  protected alterarDias(evento: Event): void {
    this.dias.set(Number((evento.target as HTMLSelectElement).value));
    this.carregar();
  }

  protected maxStatus(d: Dashboard): number {
    return Math.max(1, ...Object.values(d.porStatus));
  }

  protected maxPrioridade(d: Dashboard): number {
    return Math.max(1, ...Object.values(d.emAbertoPorPrioridade));
  }

  protected maxCategoria(d: Dashboard): number {
    return Math.max(1, ...d.emAbertoPorCategoria.map((c) => c.total));
  }

  /** Largura da barra, de 0 a 100. */
  protected percentual(valor: number, maximo: number): number {
    return Math.round((valor / maximo) * 100);
  }

  private carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.servico
      .resumo(this.dias())
      .pipe(finalize(() => this.carregando.set(false)))
      .subscribe({
        next: (d) => this.dados.set(d),
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
  }
}
