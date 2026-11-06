import { Component, inject } from '@angular/core';
import { NotificacaoService } from './notificacao.service';

/** Área onde os avisos globais aparecem. Fica na raiz da aplicação, então vale também para o login. */
@Component({
  selector: 'app-notificacoes',
  standalone: true,
  template: `
    <div class="notificacoes" aria-live="polite">
      @for (n of itens(); track n.id) {
        <div class="toast" [class]="'toast-' + n.tipo" [attr.role]="n.tipo === 'erro' ? 'alert' : 'status'">
          <span class="toast-texto">{{ n.texto }}</span>
          <button type="button" class="toast-fechar" (click)="remover(n.id)" aria-label="Fechar aviso">×</button>
        </div>
      }
    </div>
  `,
})
export class NotificacoesComponent {
  private readonly servico = inject(NotificacaoService);

  protected readonly itens = this.servico.itens;

  protected remover(id: number): void {
    this.servico.remover(id);
  }
}
