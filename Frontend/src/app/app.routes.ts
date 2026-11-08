import { Routes } from '@angular/router';
import { autenticadoGuard, perfilGuard, visitanteGuard } from './core/auth/auth.guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Entrar · ChamaTI',
    canActivate: [visitanteGuard],
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [autenticadoGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · ChamaTI',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'conta',
        title: 'Minha conta · ChamaTI',
        loadComponent: () => import('./features/conta/conta.component').then((m) => m.ContaComponent),
      },
      {
        path: 'chamados',
        loadChildren: () => import('./features/chamados/chamados.routes').then((m) => m.CHAMADOS_ROUTES),
      },
      {
        path: 'admin',
        canActivate: [perfilGuard('ADMIN')],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
