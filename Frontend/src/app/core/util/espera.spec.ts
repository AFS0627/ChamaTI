import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { DestroyRef, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ApiError } from '../http/api-error';
import { EsperaPorTentativas, segundosParaNovaTentativa } from './espera';

const erro429 = (cabecalhos: Record<string, string>): ApiError =>
  ApiError.deHttp(
    new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders(cabecalhos),
      error: { mensagem: 'Muitas tentativas. Tente novamente mais tarde.' },
    }),
  );

describe('segundosParaNovaTentativa', () => {
  it('lê o Retry-After do 429', () => {
    expect(segundosParaNovaTentativa(erro429({ 'Retry-After': '42' }))).toBe(42);
  });

  it('devolve null sem o cabeçalho, com valor inválido ou em outros erros', () => {
    expect(segundosParaNovaTentativa(erro429({}))).toBeNull();
    expect(segundosParaNovaTentativa(erro429({ 'Retry-After': 'logo' }))).toBeNull();
    expect(segundosParaNovaTentativa(ApiError.deHttp(new HttpErrorResponse({ status: 422 })))).toBeNull();
    expect(segundosParaNovaTentativa(new Error('x'))).toBeNull();
  });
});

describe('EsperaPorTentativas', () => {
  it('faz a contagem regressiva até zerar', async () => {
    const espera = TestBed.runInInjectionContext(() => new EsperaPorTentativas(inject(DestroyRef), 5));

    espera.iniciar(3);
    expect(espera.restante()).toBe(3);

    await new Promise<void>((resolve) => setTimeout(resolve, 100));

    expect(espera.restante()).toBe(0);
  });

  it('uma nova espera reinicia a contagem', () => {
    const espera = TestBed.runInInjectionContext(() => new EsperaPorTentativas(inject(DestroyRef), 1000));

    espera.iniciar(30);
    espera.iniciar(10);

    expect(espera.restante()).toBe(10);
  });
});
