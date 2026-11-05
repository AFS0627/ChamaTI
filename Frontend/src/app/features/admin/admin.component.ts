import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <h1>Administração</h1>
    <nav class="abas">
      <a routerLink="/admin/usuarios" routerLinkActive="ativo">Usuários</a>
      <a routerLink="/admin/categorias" routerLinkActive="ativo">Categorias</a>
      <a routerLink="/admin/avaliacoes" routerLinkActive="ativo">Avaliações</a>
    </nav>
    <router-outlet />
  `,
})
export class AdminComponent {}
