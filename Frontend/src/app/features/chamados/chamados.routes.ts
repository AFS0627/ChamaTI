import { Routes } from '@angular/router';

export const CHAMADOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Chamados · ChamaTI',
    loadComponent: () => import('./chamado-lista.component').then((m) => m.ChamadoListaComponent),
  },
  {
    path: 'novo',
    title: 'Novo chamado · ChamaTI',
    loadComponent: () => import('./chamado-novo.component').then((m) => m.ChamadoNovoComponent),
  },
  {
    path: ':id',
    title: 'Chamado · ChamaTI',
    loadComponent: () => import('./chamado-detalhe.component').then((m) => m.ChamadoDetalheComponent),
  },
];
