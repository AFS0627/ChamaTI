import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { rotuloPerfil } from '../core/util/rotulos';

const RAIZ = 'ChamaTI';

const ROTULOS: Record<string, string> = {
  dashboard: 'Dashboard',
  chamados: 'Chamados',
  novo: 'Novo chamado',
  conta: 'Minha conta',
  admin: 'Administração',
  usuarios: 'Usuários',
  categorias: 'Categorias',
  avaliacoes: 'Avaliações',
};

/** Estrutura da aplicação: barra lateral, cabeçalho e área de conteúdo. */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  host: { '(document:keydown.escape)': 'fechar()' },
  template: `
    <a class="pular" href="#conteudo">Ir para o conteúdo</a>

    <div class="app" [class.menu-aberto]="menuAberto()">
      <aside class="lateral" id="menu-lateral" aria-label="Menu lateral">
        <a class="lateral-marca" routerLink="/dashboard" aria-label="ChamaTI, ir para o dashboard">
          <svg class="chama" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path
              d="M12.6 2.2c.4 3.1-1 4.8-2.4 6.5C8.8 10.4 7.5 12 7.5 14.4a4.9 4.9 0 0 0 9.8.3c0-2.3-1.2-3.8-2.2-5.1-.3 1.3-1 2.1-2 2.6.7-3.5-.1-6.7-.5-10Z"
            />
            <path
              fill="#0a1426"
              fill-opacity=".35"
              d="M12.2 21.5a3 3 0 0 1-3-3c0-1.6.9-2.5 1.9-3.6.3 1 .9 1.6 1.7 2 .1-1 .6-1.7 1.1-2.4.9 1 1.3 2 1.3 3.1a3 3 0 0 1-3 3.9Z"
            />
          </svg>
          <span class="nome">Chama<span>TI</span></span>
        </a>

        <button type="button" class="btn-fechar" (click)="fechar()" aria-label="Fechar menu">
          <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>

        <nav class="menu" aria-label="Principal">
          <a class="menu-item" routerLink="/dashboard" routerLinkActive="ativo" ariaCurrentWhenActive="page" title="Dashboard">
            <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" />
              <rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" />
            </svg>
            <span class="rotulo">Dashboard</span>
          </a>
          <a class="menu-item" routerLink="/chamados" routerLinkActive="ativo" ariaCurrentWhenActive="page" title="Chamados">
            <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <rect x="5" y="3" width="14" height="18" rx="2" /><path d="M9 8h6M9 12h6M9 16h4" />
            </svg>
            <span class="rotulo">Chamados</span>
          </a>

          @if (ehAdmin()) {
            <p class="menu-grupo">Administração</p>
            <a class="menu-item" routerLink="/admin/usuarios" routerLinkActive="ativo" ariaCurrentWhenActive="page" title="Usuários">
              <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="9" cy="8" r="3.5" /><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" />
                <path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18.5 14.3c2 .7 3.5 2.4 3.5 5.7" />
              </svg>
              <span class="rotulo">Usuários</span>
            </a>
            <a class="menu-item" routerLink="/admin/categorias" routerLinkActive="ativo" ariaCurrentWhenActive="page" title="Categorias">
              <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8Z" /><circle cx="7.5" cy="7.5" r="1.3" />
              </svg>
              <span class="rotulo">Categorias</span>
            </a>
            <a class="menu-item" routerLink="/admin/avaliacoes" routerLinkActive="ativo" ariaCurrentWhenActive="page" title="Avaliações">
              <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9Z" />
              </svg>
              <span class="rotulo">Avaliações</span>
            </a>
          }
        </nav>

        @if (usuario(); as u) {
          <div class="lateral-rodape">
            <a class="conta-link" routerLink="/conta" routerLinkActive="ativo" ariaCurrentWhenActive="page" title="Minha conta">
              <span class="avatar" aria-hidden="true">{{ iniciais() }}</span>
              <span class="quem">
                <strong>{{ u.nome }}</strong>
                <span>{{ rotuloPerfil(u.perfil) }}</span>
              </span>
              <span class="sr-only">Minha conta</span>
            </a>
            <button type="button" class="btn-sair" (click)="sair()" title="Sair" aria-label="Sair">
              <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M9 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4" /><path d="M16 17l5-5-5-5M21 12H9" />
              </svg>
            </button>
          </div>
        }
      </aside>

      <div class="cortina" (click)="fechar()" aria-hidden="true"></div>

      <div class="principal">
        <header class="cabecalho">
          <button
            type="button"
            class="btn-menu"
            (click)="alternar()"
            aria-controls="menu-lateral"
            [attr.aria-expanded]="menuAberto()"
            aria-label="Abrir ou fechar o menu"
          >
            <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
              @if (menuAberto()) {
                <path d="M6 6l12 12M18 6L6 18" />
              } @else {
                <path d="M4 7h16M4 12h16M4 17h16" />
              }
            </svg>
          </button>

          <ol class="trilha" aria-label="Você está em">
            @for (parte of trilha(); track $index) {
              <li [class.raiz]="$first">{{ parte }}</li>
            }
          </ol>

          <div class="cabecalho-acoes">
            <a class="btn btn-primary" routerLink="/chamados/novo" aria-label="Novo chamado">
              <svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
              <span class="btn-texto">Novo chamado</span>
            </a>
          </div>
        </header>

        <main class="conteudo" id="conteudo" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly usuario = this.auth.usuario;
  protected readonly ehAdmin = computed(() => this.auth.usuario()?.perfil === 'ADMIN');
  protected readonly rotuloPerfil = rotuloPerfil;

  protected readonly menuAberto = signal(false);
  private readonly url = signal(this.router.url);

  /** Caminho atual como trilha: "ChamaTI › Chamados › Chamado nº 12", "ChamaTI › Administração › Usuários". */
  protected readonly trilha = computed(() => {
    const partes = this.url().split(/[?#]/)[0].split('/').filter(Boolean);
    const rotulos = partes.map((p) => ROTULOS[p] ?? (/^\d+$/.test(p) ? `Chamado nº ${p}` : p));
    return [RAIZ, ...(rotulos.length > 0 ? rotulos : ['Dashboard'])];
  });

  protected readonly iniciais = computed(() => {
    const nomes = (this.auth.usuario()?.nome ?? '').trim().split(/\s+/).filter(Boolean);
    if (nomes.length === 0) {
      return '?';
    }
    const primeira = nomes[0].charAt(0);
    const ultima = nomes.length > 1 ? nomes[nomes.length - 1].charAt(0) : '';
    return (primeira + ultima).toUpperCase();
  });

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => {
        this.url.set(e.urlAfterRedirects);
        this.menuAberto.set(false);
      });
  }

  protected alternar(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  protected fechar(): void {
    this.menuAberto.set(false);
  }

  protected sair(): void {
    this.auth.logout().subscribe();
  }
}
