import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { mensagemDeErro } from '../../core/util/erro';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="login">
      <form class="card login-card" [formGroup]="form" (ngSubmit)="entrar()" novalidate>
        <h1>ChamaTI</h1>
        <p class="muted">Entre com seu e-mail e senha.</p>

        <label class="campo">
          <span>E-mail</span>
          <input type="email" formControlName="email" autocomplete="username" />
          @if (form.controls.email.touched && form.controls.email.invalid) {
            <small class="erro">Informe um e-mail válido.</small>
          }
        </label>

        <label class="campo">
          <span>Senha</span>
          <input type="password" formControlName="senha" autocomplete="current-password" />
          @if (form.controls.senha.touched && form.controls.senha.invalid) {
            <small class="erro">Informe a senha.</small>
          }
        </label>

        @if (erro(); as mensagem) {
          <p class="alerta erro" role="alert">{{ mensagem }}</p>
        }

        <button type="submit" class="btn btn-primary" [disabled]="enviando()">
          {{ enviando() ? 'Entrando…' : 'Entrar' }}
        </button>
      </form>
    </div>
  `,
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', Validators.required],
  });
  protected readonly erro = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected entrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    const { email, senha } = this.form.getRawValue();
    this.auth
      .login(email.trim(), senha)
      .pipe(finalize(() => this.enviando.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(this.destino()),
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
  }

  /** Só aceita caminhos internos, para não virar redirecionamento aberto. */
  private destino(): string {
    const retorno = this.rota.snapshot.queryParamMap.get('retorno');
    return retorno !== null && retorno.startsWith('/') && !retorno.startsWith('//') ? retorno : '/dashboard';
  }
}
