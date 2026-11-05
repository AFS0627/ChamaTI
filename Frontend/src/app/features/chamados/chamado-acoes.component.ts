import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, finalize } from 'rxjs';
import { ChamadosService } from '../../core/api/chamados.service';
import { UsuariosService } from '../../core/api/usuarios.service';
import { AuthService } from '../../core/auth/auth.service';
import { Chamado, StatusChamado, UsuarioResumo } from '../../core/models/api.models';
import { mensagemDeErro } from '../../core/util/erro';
import { rotuloStatus } from '../../core/util/rotulos';

const EM_ANDAMENTO: StatusChamado[] = ['ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'AGUARDANDO_TERCEIRO'];
const PODE_RESOLVER: StatusChamado[] = ['EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'AGUARDANDO_TERCEIRO'];
const STATUS_DE_ANDAMENTO: StatusChamado[] = ['EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'AGUARDANDO_TERCEIRO'];

/**
 * Ações de gestão do chamado. A visibilidade dos botões é só conveniência: quem decide é a API.
 * Todas enviam a versão lida, então uma alteração concorrente responde 409.
 */
@Component({
  selector: 'app-chamado-acoes',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    @if (temAlgumaAcao()) {
      <section class="card">
        <h2>Ações</h2>

        @if (erro(); as mensagem) {
          <p class="alerta erro" role="alert">{{ mensagem }}</p>
        }

        @if (podeAssumir()) {
          <div class="acao">
            <button type="button" class="btn btn-primary" [disabled]="ocupado()" (click)="assumir()">
              Assumir chamado
            </button>
          </div>
        }

        @if (podeAtribuir()) {
          <form class="acao linha" [formGroup]="atribuirForm" (ngSubmit)="atribuir()">
            <label class="campo">
              <span>Atribuir técnico</span>
              <select formControlName="tecnicoId">
                <option [ngValue]="null">Selecione…</option>
                @for (t of tecnicos(); track t.id) {
                  <option [ngValue]="t.id">{{ t.nome }}</option>
                }
              </select>
            </label>
            <button type="submit" class="btn" [disabled]="ocupado() || atribuirForm.invalid">Atribuir</button>
          </form>
        }

        @if (podeAlterarStatus()) {
          <form class="acao linha" [formGroup]="statusForm" (ngSubmit)="alterarStatus()">
            <label class="campo">
              <span>Novo status</span>
              <select formControlName="status">
                @for (s of statusDeAndamento; track s) {
                  <option [value]="s">{{ rotuloStatus(s) }}</option>
                }
              </select>
            </label>
            <label class="campo largo">
              <span>Observação (opcional)</span>
              <input type="text" formControlName="observacao" maxlength="400" />
            </label>
            <button type="submit" class="btn" [disabled]="ocupado()">Alterar status</button>
          </form>
        }

        @if (podeResolver()) {
          <form class="acao" [formGroup]="resolverForm" (ngSubmit)="resolver()">
            <label class="campo">
              <span>Solução aplicada</span>
              <textarea formControlName="solucao" rows="3"></textarea>
            </label>
            <button type="submit" class="btn btn-primary" [disabled]="ocupado() || resolverForm.invalid">
              Resolver chamado
            </button>
          </form>
        }

        @if (podeFechar()) {
          <div class="acao">
            <button type="button" class="btn" [disabled]="ocupado()" (click)="fechar()">Fechar chamado</button>
          </div>
        }

        @if (podeReabrir()) {
          <form class="acao linha" [formGroup]="reabrirForm" (ngSubmit)="reabrir()">
            <label class="campo largo">
              <span>Motivo da reabertura</span>
              <input type="text" formControlName="motivo" maxlength="300" />
            </label>
            <button type="submit" class="btn" [disabled]="ocupado() || reabrirForm.invalid">Reabrir</button>
          </form>
        }
      </section>
    }
  `,
})
export class ChamadoAcoesComponent implements OnInit {
  readonly chamado = input.required<Chamado>();
  readonly atualizado = output<Chamado>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servico = inject(ChamadosService);
  private readonly usuarios = inject(UsuariosService);
  private readonly auth = inject(AuthService);

  protected readonly statusForm = this.fb.group({
    status: ['EM_ATENDIMENTO' as StatusChamado],
    observacao: [''],
  });
  protected readonly resolverForm = this.fb.group({ solucao: ['', [Validators.required, Validators.maxLength(10000)]] });
  protected readonly reabrirForm = this.fb.group({ motivo: ['', [Validators.required, Validators.maxLength(300)]] });
  protected readonly atribuirForm = this.fb.group({
    tecnicoId: this.fb.control<number | null>(null, Validators.required),
  });

  protected readonly tecnicos = signal<UsuarioResumo[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly statusDeAndamento = STATUS_DE_ANDAMENTO;
  protected readonly rotuloStatus = rotuloStatus;

  private readonly ehAdmin = computed(() => this.auth.usuario()?.perfil === 'ADMIN');
  private readonly ehTecnico = computed(() => this.auth.usuario()?.perfil === 'TECNICO');
  private readonly ehSolicitante = computed(() => this.auth.usuario()?.id === this.chamado().solicitante.id);
  private readonly ehResponsavel = computed(
    () => this.ehTecnico() && this.chamado().tecnico?.id === this.auth.usuario()?.id,
  );
  private readonly emAndamento = computed(() => EM_ANDAMENTO.includes(this.chamado().status));
  private readonly gere = computed(() => this.ehAdmin() || this.ehResponsavel());

  protected readonly podeAssumir = computed(
    () => this.ehTecnico() && this.chamado().tecnico === null && this.emAndamento(),
  );
  protected readonly podeAtribuir = computed(() => this.ehAdmin() && this.emAndamento());
  protected readonly podeAlterarStatus = computed(() => this.gere() && this.emAndamento());
  protected readonly podeResolver = computed(() => this.gere() && PODE_RESOLVER.includes(this.chamado().status));
  protected readonly podeFechar = computed(
    () => this.chamado().status === 'RESOLVIDO' && (this.ehAdmin() || this.ehSolicitante()),
  );
  protected readonly podeReabrir = computed(
    () =>
      (this.chamado().status === 'RESOLVIDO' || this.chamado().status === 'FECHADO') &&
      (this.ehAdmin() || this.ehSolicitante() || this.ehResponsavel()),
  );
  protected readonly temAlgumaAcao = computed(
    () =>
      this.podeAssumir() ||
      this.podeAtribuir() ||
      this.podeAlterarStatus() ||
      this.podeResolver() ||
      this.podeFechar() ||
      this.podeReabrir(),
  );

  ngOnInit(): void {
    if (this.ehAdmin()) {
      this.usuarios.listar({ perfil: 'TECNICO', ativo: true, tamanho: 100 }).subscribe({
        next: (pagina) => this.tecnicos.set(pagina.conteudo),
        error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
      });
    }
  }

  protected assumir(): void {
    const usuario = this.auth.usuario();
    if (usuario) {
      this.executar(this.servico.atribuir(this.chamado().id, usuario.id, this.chamado().versao));
    }
  }

  protected atribuir(): void {
    const tecnicoId = this.atribuirForm.getRawValue().tecnicoId;
    if (tecnicoId !== null) {
      this.executar(this.servico.atribuir(this.chamado().id, tecnicoId, this.chamado().versao), () =>
        this.atribuirForm.reset(),
      );
    }
  }

  protected alterarStatus(): void {
    const { status, observacao } = this.statusForm.getRawValue();
    this.executar(
      this.servico.alterarStatus(this.chamado().id, status, observacao.trim() || null, this.chamado().versao),
      () => this.statusForm.reset(),
    );
  }

  protected resolver(): void {
    const { solucao } = this.resolverForm.getRawValue();
    this.executar(this.servico.resolver(this.chamado().id, solucao.trim(), this.chamado().versao), () =>
      this.resolverForm.reset(),
    );
  }

  protected fechar(): void {
    this.executar(this.servico.fechar(this.chamado().id, this.chamado().versao));
  }

  protected reabrir(): void {
    const { motivo } = this.reabrirForm.getRawValue();
    this.executar(this.servico.reabrir(this.chamado().id, motivo.trim(), this.chamado().versao), () =>
      this.reabrirForm.reset(),
    );
  }

  private executar(operacao: Observable<Chamado>, aoConcluir?: () => void): void {
    this.ocupado.set(true);
    this.erro.set(null);
    operacao.pipe(finalize(() => this.ocupado.set(false))).subscribe({
      next: (c) => {
        aoConcluir?.();
        this.atualizado.emit(c);
      },
      error: (e: unknown) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
