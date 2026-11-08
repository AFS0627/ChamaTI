import { Component, DestroyRef, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { mensagemDeErro } from '../../core/util/erro';
import { EsperaPorTentativas, segundosParaNovaTentativa } from '../../core/util/espera';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="login">
      <section class="login-marca" aria-hidden="true">
        <div class="logo">
          <svg class="chama" viewBox="0 0 24 24" fill="currentColor">
            <path
              d="M12.6 2.2c.4 3.1-1 4.8-2.4 6.5C8.8 10.4 7.5 12 7.5 14.4a4.9 4.9 0 0 0 9.8.3c0-2.3-1.2-3.8-2.2-5.1-.3 1.3-1 2.1-2 2.6.7-3.5-.1-6.7-.5-10Z"
            />
          </svg>
          <span>Chama<span>TI</span></span>
        </div>
        <h2>Chamados de TI sem perder o prazo.</h2>
        <p>Abra, acompanhe e resolva solicitações com SLA claro e histórico completo de cada atendimento.</p>
        <ul>
          <li>Acompanhe o status em tempo real</li>
          <li>Prazos de primeira resposta e resolução</li>
          <li>Anexos, comentários e avaliação do atendimento</li>
        </ul>
      </section>

      <main class="login-form">
        <form class="card login-card" [formGroup]="form" (ngSubmit)="entrar()" novalidate>
          <h1>Entrar</h1>
          <p class="muted">Use seu e-mail e senha corporativos.</p>

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

          <button type="submit" class="btn btn-primary" [disabled]="enviando() || espera.restante() > 0">
            {{ espera.restante() > 0 ? 'Aguarde ' + espera.restante() + ' s' : enviando() ? 'Entrando…' : 'Entrar' }}
          </button>
        </form>
      </main>
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
  /** Após 5 falhas em 15 min o backend responde 429 com Retry-After. */
  protected readonly espera = new EsperaPorTentativas(inject(DestroyRef));

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
        error: (e: unknown) => {
          this.erro.set(mensagemDeErro(e));
          const segundos = segundosParaNovaTentativa(e);
          if (segundos !== null) {
            this.espera.iniciar(segundos);
          }
        },
      });
  }

  /** Só aceita caminhos internos, para não virar redirecionamento aberto. */
  private destino(): string {
    const retorno = this.rota.snapshot.queryParamMap.get('retorno');
    return retorno !== null && retorno.startsWith('/') && !retorno.startsWith('//') ? retorno : '/dashboard';
  }
}
