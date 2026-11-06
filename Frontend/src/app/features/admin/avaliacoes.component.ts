import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AvaliacoesService } from '../../core/api/avaliacoes.service';
import { Avaliacao, MediaTecnico, Pagina } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';

@Component({
  selector: 'app-avaliacoes',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <h1>Avaliações do atendimento</h1>

    @if (erro(); as mensagem) {
      <p class="alerta erro" role="alert">{{ mensagem }}</p>
    }

    <div class="card tabela">
      <h3>Média por técnico</h3>
      @if (medias(); as lista) {
        @if (lista.length === 0) {
          <p class="muted">Nenhuma avaliação recebida ainda.</p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Técnico</th>
                <th class="num">Média</th>
                <th class="num">Avaliações</th>
              </tr>
            </thead>
            <tbody>
              @for (m of lista; track m.tecnicoId) {
                <tr>
                  <td>{{ m.tecnicoNome }}</td>
                  <td class="num">{{ m.media.toFixed(2) }} <span class="estrelas-texto">{{ estrelas(m.media) }}</span></td>
                  <td class="num">{{ m.quantidade }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
      }
    </div>

    <div class="card tabela">
      <h3>Todas as avaliações</h3>
      @if (resultado(); as r) {
        @if (r.conteudo.length === 0) {
          <p class="muted">Nenhuma avaliação.</p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Chamado</th>
                <th>Nota</th>
                <th>Comentário</th>
                <th>Solicitante</th>
                <th>Técnico</th>
                <th>Data</th>
              </tr>
            </thead>
            <tbody>
              @for (a of r.conteudo; track a.id) {
                <tr>
                  <td><a [routerLink]="['/chamados', a.chamadoId]">{{ a.protocolo }}</a></td>
                  <td><span class="estrelas-texto">{{ estrelas(a.nota) }}</span></td>
                  <td>{{ a.comentario ?? '—' }}</td>
                  <td>{{ a.solicitante.nome }}</td>
                  <td>{{ a.tecnico.nome }}</td>
                  <td>{{ a.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</td>
                </tr>
              }
            </tbody>
          </table>
          <div class="paginacao">
            <button type="button" class="btn" [disabled]="r.pagina === 0" (click)="carregarLista(r.pagina - 1)">Anterior</button>
            <span>Página {{ r.pagina + 1 }} de {{ r.totalPaginas }} · {{ r.totalElementos }} avaliação(ões)</span>
            <button type="button" class="btn" [disabled]="r.pagina + 1 >= r.totalPaginas" (click)="carregarLista(r.pagina + 1)">
              Próxima
            </button>
          </div>
        }
      }
    </div>
  `,
})
export class AvaliacoesComponent implements OnInit {
  private readonly servico = inject(AvaliacoesService);

  protected readonly medias = signal<MediaTecnico[] | null>(null);
  protected readonly resultado = signal<Pagina<Avaliacao> | null>(null);
  protected readonly erro = signal<string | null>(null);

  ngOnInit(): void {
    this.servico.mediasPorTecnico().subscribe({
      next: (lista) => this.medias.set(lista),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
    this.carregarLista(0);
  }

  protected carregarLista(pagina: number): void {
    this.servico.listar(pagina).subscribe({
      next: (r) => this.resultado.set(r),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }

  /** Estrelas cheias para a nota arredondada (ex.: 3,5 → ★★★★☆). */
  protected estrelas(nota: number): string {
    const cheias = Math.max(0, Math.min(5, Math.round(nota)));
    return '★'.repeat(cheias) + '☆'.repeat(5 - cheias);
  }
}
