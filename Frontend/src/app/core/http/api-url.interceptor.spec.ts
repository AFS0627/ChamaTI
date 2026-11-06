import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_CONFIG, ehRequisicaoDaApi, provideApiConfig } from './api-config';
import { apiUrlInterceptor } from './api-url.interceptor';

function configurar(baseUrl: string): { client: HttpClient; http: HttpTestingController } {
  TestBed.configureTestingModule({
    providers: [
      provideApiConfig({ baseUrl }),
      provideHttpClient(withInterceptors([apiUrlInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  return { client: TestBed.inject(HttpClient), http: TestBed.inject(HttpTestingController) };
}

describe('apiUrlInterceptor', () => {
  it('acrescenta a origem do backend aos caminhos /api', () => {
    const { client, http } = configurar('http://api.teste');

    client.get('/api/chamados').subscribe();

    http.expectOne('http://api.teste/api/chamados').flush({});
    http.verify();
  });

  it('ignora a barra final da configuração', () => {
    const { client, http } = configurar('http://api.teste/');

    client.get('/api/chamados').subscribe();

    http.expectOne('http://api.teste/api/chamados').flush({});
    http.verify();
  });

  it('com origem vazia (mesma origem) mantém o caminho relativo', () => {
    const { client, http } = configurar('');

    client.get('/api/chamados').subscribe();

    http.expectOne('/api/chamados').flush({});
    http.verify();
  });

  it('não altera URLs absolutas nem caminhos fora de /api', () => {
    const { client, http } = configurar('http://api.teste');

    client.get('https://outro.site/api/x').subscribe();
    client.get('/assets/logo.svg').subscribe();

    http.expectOne('https://outro.site/api/x').flush({});
    http.expectOne('/assets/logo.svg').flush({});
    http.verify();
  });
});

describe('configuração da API', () => {
  it('reconhece chamadas da API pela origem configurada', () => {
    TestBed.configureTestingModule({ providers: [provideApiConfig({ baseUrl: 'http://api.teste' })] });
    const config = TestBed.inject(API_CONFIG);

    expect(config.baseUrl).toBe('http://api.teste');
    expect(ehRequisicaoDaApi(config, 'http://api.teste/api/chamados')).toBe(true);
    expect(ehRequisicaoDaApi(config, 'http://outro.site/api/chamados')).toBe(false);
    expect(ehRequisicaoDaApi(config, 'http://api.teste/swagger-ui.html')).toBe(false);
  });
});
