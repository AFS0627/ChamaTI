import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { rotuloPerfil } from '../core/util/rotulos';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topo">
      <a class="marca" routerLink="/dashboard">ChamaTI</a>
      <nav class="menu">
        <a routerLink="/dashboard" routerLinkActive="ativo">Dashboard</a>
        <a routerLink="/chamados" routerLinkActive="ativo">Chamados</a>
        @if (ehAdmin()) {
          <a routerLink="/admin" routerLinkActive="ativo">Administração</a>
        }
      </nav>
      <div class="usuario">
        @if (usuario(); as u) {
          <span>{{ u.nome }} · {{ rotuloPerfil(u.perfil) }}</span>
        }
        <button type="button" class="btn" (click)="sair()">Sair</button>
      </div>
    </header>
    <main class="conteudo">
      <router-outlet />
    </main>
  `,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);

  protected readonly usuario = this.auth.usuario;
  protected readonly ehAdmin = computed(() => this.auth.usuario()?.perfil === 'ADMIN');
  protected readonly rotuloPerfil = rotuloPerfil;

  protected sair(): void {
    this.auth.logout().subscribe();
  }
}
