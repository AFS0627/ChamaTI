import { Routes } from '@angular/router';
import { AdminComponent } from './admin.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'usuarios' },
      {
        path: 'usuarios',
        title: 'Usuários · ChamaTI',
        loadComponent: () => import('./usuarios.component').then((m) => m.UsuariosComponent),
      },
      {
        path: 'categorias',
        title: 'Categorias · ChamaTI',
        loadComponent: () => import('./categorias.component').then((m) => m.CategoriasComponent),
      },
      {
        path: 'avaliacoes',
        title: 'Avaliações · ChamaTI',
        loadComponent: () => import('./avaliacoes.component').then((m) => m.AvaliacoesComponent),
      },
    ],
  },
];
