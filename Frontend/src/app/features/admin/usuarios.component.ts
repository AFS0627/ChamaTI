import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { UsuariosService } from '../../core/api/usuarios.service';
import { AuthService } from '../../core/auth/auth.service';
import { Pagina, Perfil, UsuarioResumo } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { PERFIS, rotuloPerfil } from '../../core/util/rotulos';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    <div class="barra">
      <h1>Usuários</h1>
      <button type="button" class="btn btn-primary" (click)="mostrarNovo.set(!mostrarNovo())">
        {{ mostrarNovo() ? 'Cancelar' : 'Novo usuário' }}
      </button>
    </div>

    @if (mostrarNovo()) {
      <form class="card formulario" [formGroup]="novoForm" (ngSubmit)="criar()" novalidate>
        <div class="linha">
          <label class="campo">
            <span>Nome</span>
            <input type="text" formControlName="nome" maxlength="120" />
          </label>
          <label class="campo">
            <span>E-mail</span>
            <input type="email" formControlName="email" maxlength="160" autocomplete="off" />
          </label>
          <label class="campo">
            <span>Senha inicial</span>
            <input type="password" formControlName="senha" autocomplete="new-password" />
          </label>
        </div>
        <div class="linha">
          <label class="campo">
            <span>Perfil</span>
            <select formControlName="perfil">
              @for (p of perfis; track p) {
                <option [value]="p">{{ rotuloPerfil(p) }}</option>
              }
            </select>
          </label>
          <label class="campo">
            <span>Departamento</span>
            <input type="text" formControlName="departamento" maxlength="80" />
          </label>
          <label class="campo">
            <span>Telefone</span>
            <input type="text" formControlName="telefone" maxlength="30" />
          </label>
        </div>
        @if (novoForm.touched && novoForm.invalid) {
          <small class="erro">Preencha nome, e-mail válido e senha (8+ caracteres, com letra e número).</small>
        }
        @if (erroNovo(); as mensagem) {
          <p class="alerta erro" role="alert">{{ mensagem }}</p>
        }
        <div class="acoes">
          <button type="submit" class="btn btn-primary" [disabled]="salvando()">Criar usuário</button>
        </div>
      </form>
    }

    <form class="card filtros" [formGroup]="filtroForm" (ngSubmit)="carregar(0)">
      <label class="campo">
        <span>Busca</span>
        <input type="search" formControlName="q" placeholder="Nome ou e-mail" />
      </label>
      <label class="campo">
        <span>Perfil</span>
        <select formControlName="perfil">
          <option value="">Todos</option>
          @for (p of perfis; track p) {
            <option [value]="p">{{ rotuloPerfil(p) }}</option>
          }
        </select>
      </label>
      <label class="campo">
        <span>Situação</span>
        <select formControlName="ativo">
          <option value="">Todas</option>
          <option value="true">Ativos</option>
          <option value="false">Inativos</option>
        </select>
      </label>
      <div class="acoes">
        <button type="submit" class="btn btn-primary">Filtrar</button>
      </div>
    </form>

    @if (erro(); as mensagem) {
      <p class="alerta erro" role="alert">{{ mensagem }}</p>
    }

    <div class="card tabela">
      @if (resultado(); as r) {
        <table>
          <thead>
            <tr>
              <th>Nome</th>
              <th>E-mail</th>
              <th>Perfil</th>
              <th>Departamento</th>
              <th>Último acesso</th>
              <th>Situação</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (u of r.conteudo; track u.id) {
              <tr>
                <td>{{ u.nome }}</td>
                <td>{{ u.email }}</td>
                <td>{{ rotuloPerfil(u.perfil) }}</td>
                <td>{{ u.departamento ?? '—' }}</td>
                <td>{{ u.ultimoLoginEm ? (u.ultimoLoginEm | date: 'dd/MM/yyyy HH:mm') : '—' }}</td>
                <td>
                  <span class="badge" [class]="u.ativo ? 'status-ABERTO' : 'status-FECHADO'">
                    {{ u.ativo ? 'Ativo' : 'Inativo' }}
                  </span>
                </td>
                <td>
                  <button type="button" class="btn" [disabled]="salvando() || u.id === meuId()" (click)="alternar(u)">
                    {{ u.ativo ? 'Desativar' : 'Ativar' }}
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
        <div class="paginacao">
          <button type="button" class="btn" [disabled]="r.pagina === 0" (click)="carregar(r.pagina - 1)">Anterior</button>
          <span>Página {{ r.pagina + 1 }} de {{ r.totalPaginas }} · {{ r.totalElementos }} usuário(s)</span>
          <button type="button" class="btn" [disabled]="r.pagina + 1 >= r.totalPaginas" (click)="carregar(r.pagina + 1)">
            Próxima
          </button>
        </div>
      } @else {
        <p class="muted">Carregando…</p>
      }
    </div>
  `,
})
export class UsuariosComponent implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servico = inject(UsuariosService);
  private readonly auth = inject(AuthService);

  protected readonly filtroForm = this.fb.group({
    q: [''],
    perfil: ['' as Perfil | ''],
    ativo: ['' as '' | 'true' | 'false'],
  });
  protected readonly novoForm = this.fb.group({
    nome: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(160)]],
    senha: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*\p{L})(?=.*\d).*$/u)]],
    perfil: ['SOLICITANTE' as Perfil],
    departamento: [''],
    telefone: [''],
  });

  protected readonly resultado = signal<Pagina<UsuarioResumo> | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly erroNovo = signal<string | null>(null);
  protected readonly salvando = signal(false);
  protected readonly mostrarNovo = signal(false);
  protected readonly perfis = PERFIS;
  protected readonly rotuloPerfil = rotuloPerfil;

  protected meuId(): number | null {
    return this.auth.usuario()?.id ?? null;
  }

  ngOnInit(): void {
    this.carregar(0);
  }

  protected carregar(pagina: number): void {
    const f = this.filtroForm.getRawValue();
    this.erro.set(null);
    this.servico
      .listar({ q: f.q, perfil: f.perfil, ativo: f.ativo === '' ? null : f.ativo === 'true', pagina })
      .subscribe({
        next: (r) => this.resultado.set(r),
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
  }

  protected criar(): void {
    if (this.novoForm.invalid) {
      this.novoForm.markAllAsTouched();
      return;
    }
    const v = this.novoForm.getRawValue();
    this.salvando.set(true);
    this.erroNovo.set(null);
    this.servico
      .criar({
        nome: v.nome.trim(),
        email: v.email.trim(),
        senha: v.senha,
        perfil: v.perfil,
        departamento: v.departamento.trim() || null,
        telefone: v.telefone.trim() || null,
      })
      .pipe(finalize(() => this.salvando.set(false)))
      .subscribe({
        next: () => {
          this.novoForm.reset();
          this.mostrarNovo.set(false);
          this.carregar(0);
        },
        error: (e: unknown) => this.erroNovo.set(mensagemDeErro(e)),
      });
  }

  protected alternar(usuario: UsuarioResumo): void {
    this.salvando.set(true);
    this.erro.set(null);
    const operacao = usuario.ativo ? this.servico.desativar(usuario.id) : this.servico.ativar(usuario.id);
    operacao.pipe(finalize(() => this.salvando.set(false))).subscribe({
      next: () => this.carregar(this.resultado()?.pagina ?? 0),
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
