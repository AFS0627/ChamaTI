import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificacoesComponent } from './core/notificacoes/notificacoes.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NotificacoesComponent],
  template: `
    <router-outlet />
    <app-notificacoes />
  `,
})
export class AppComponent {}
