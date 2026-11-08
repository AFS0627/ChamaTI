import { Component, DestroyRef, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { NotificacaoService } from '../../core/notificacoes/notificacao.service';
import { mensagemDeErro } from '../../core/util/erro';
import { EsperaPorTentativas, segundosParaNovaTentativa } from '../../core/util/espera';
import { rotuloPerfil } from '../../core/util/rotulos';

/** O backend limita a senha a 72 BYTES (UTF-8), não a 72 caracteres. */
function limiteDeBytes(maximo: number) {
  return (controle: AbstractControl): ValidationErrors | null => {
    const valor = String(controle.value ?? '');
    return new TextEncoder().encode(valor).length > maximo ? { bytes: true } : null;
  };
}

function confirmacaoIgual(grupo: AbstractControl): ValidationErrors | null {
  const nova = grupo.get('novaSenha')?.value as string | undefined;
  const confirmacao = grupo.get('confirmacao')?.value as string | undefined;
  return confirmacao && nova !== confirmacao ? { confirmacao: true } : null;
}

function diferenteDaAtual(grupo: AbstractControl): ValidationErrors | null {
  const atual = grupo.get('senhaAtual')?.value as string | undefined;
  const nova = grupo.get('novaSenha')?.value as string | undefined;
  return atual && nova && atual === nova ? { igualAtual: true } : null;
}

@Component({
  selector: 'app-conta',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="barra">
      <h1>Minha conta</h1>
    </div>

    @if (usuario(); as u) {
      <section class="card">
        <h2>Meus dados</h2>
        <dl class="dados">
          <div><dt>Nome</dt><dd>{{ u.nome }}</dd></div>
          <div><dt>E-mail</dt><dd>{{ u.email }}</dd></div>
          <div><dt>Perfil</dt><dd>{{ rotuloPerfil(u.perfil) }}</dd></div>
        </dl>
      </section>
    }

    <section class="card">
      <h2>Alterar senha</h2>
      <p class="muted">
        Ao trocar a senha, os outros dispositivos são desconectados. Esta sessão continua aberta.
      </p>
      <form class="formulario" [formGroup]="form" (ngSubmit)="alterarSenha()" novalidate>
        <div class="linha">
          <label class="campo">
            <span>Senha atual</span>
            <input type="password" formControlName="senhaAtual" autocomplete="current-password" />
          </label>
          <label class="campo">
            <span>Nova senha</span>
            <input type="password" formControlName="novaSenha" autocomplete="new-password" />
          </label>
          <label class="campo">
            <span>Confirmar nova senha</span>
            <input type="password" formControlName="confirmacao" autocomplete="new-password" />
          </label>
        </div>

        @if (form.controls.novaSenha.touched && form.controls.novaSenha.invalid) {
          <small class="erro">A nova senha deve ter de 8 a 72 caracteres, com ao menos uma letra e um número.</small>
        }
        @if (form.touched && form.hasError('confirmacao')) {
          <small class="erro">A confirmação não confere com a nova senha.</small>
        }
        @if (form.touched && form.hasError('igualAtual')) {
          <small class="erro">A nova senha deve ser diferente da atual.</small>
        }
        @if (erro(); as mensagem) {
          <p class="alerta erro" role="alert">{{ mensagem }}</p>
        }

        <div class="acoes">
          <button type="submit" class="btn btn-primary" [disabled]="enviando() || espera.restante() > 0">
            {{ espera.restante() > 0 ? 'Aguarde ' + espera.restante() + ' s' : enviando() ? 'Salvando…' : 'Alterar senha' }}
          </button>
        </div>
      </form>
    </section>

    <section class="card">
      <h2>Sessões</h2>
      <p class="muted">
        Encerra a sessão em todos os dispositivos, inclusive este. Quem estiver com a conta aberta em outro lugar
        precisará entrar de novo (o acesso já emitido pode continuar valendo por até 15 minutos).
      </p>
      @if (confirmando()) {
        <div class="acoes">
          <button type="button" class="btn btn-primary" [disabled]="saindo()" (click)="sairDeTodos()">
            Sim, sair de todos os dispositivos
          </button>
          <button type="button" class="btn" [disabled]="saindo()" (click)="confirmando.set(false)">Cancelar</button>
        </div>
      } @else {
        <button type="button" class="btn" (click)="confirmando.set(true)">Sair de todos os dispositivos</button>
      }
      @if (erroSessoes(); as mensagem) {
        <p class="alerta erro" role="alert">{{ mensagem }}</p>
      }
    </section>
  `,
})
export class ContaComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly avisos = inject(NotificacaoService);

  protected readonly usuario = this.auth.usuario;
  protected readonly rotuloPerfil = rotuloPerfil;
  protected readonly espera = new EsperaPorTentativas(inject(DestroyRef));

  protected readonly form = this.fb.group(
    {
      senhaAtual: ['', [Validators.required, Validators.maxLength(72)]],
      novaSenha: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(/^(?=.*\p{L})(?=.*\d).*$/u),
          limiteDeBytes(72),
        ],
      ],
      confirmacao: ['', Validators.required],
    },
    { validators: [confirmacaoIgual, diferenteDaAtual] },
  );

  protected readonly erro = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly confirmando = signal(false);
  protected readonly saindo = signal(false);
  protected readonly erroSessoes = signal<string | null>(null);

  protected alterarSenha(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { senhaAtual, novaSenha } = this.form.getRawValue();
    this.enviando.set(true);
    this.erro.set(null);
    this.auth
      .alterarSenha(senhaAtual, novaSenha)
      .pipe(finalize(() => this.enviando.set(false)))
      .subscribe({
        next: () => {
          this.form.reset();
          this.avisos.sucesso('Senha alterada. Os outros dispositivos foram desconectados.');
        },
        error: (e: unknown) => {
          this.erro.set(mensagemDeErro(e));
          const segundos = segundosParaNovaTentativa(e);
          if (segundos !== null) {
            this.espera.iniciar(segundos);
          }
        },
      });
  }

  protected sairDeTodos(): void {
    this.saindo.set(true);
    this.erroSessoes.set(null);
    this.auth
      .logoutTodos()
      .pipe(finalize(() => this.saindo.set(false)))
      .subscribe({ error: (e: unknown) => this.erroSessoes.set(mensagemDeErro(e)) });
  }
}
