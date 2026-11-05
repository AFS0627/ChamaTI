import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../config';
import {
  Anexo,
  Avaliacao,
  Chamado,
  ChamadoCriar,
  ChamadoResumo,
  Comentario,
  FiltrosChamados,
  Historico,
  Pagina,
  StatusChamado,
  TipoComentario,
} from '../models/api.models';
import { montarParams } from './http-params';

@Injectable({ providedIn: 'root' })
export class ChamadosService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/api/chamados`;

  listar(f: FiltrosChamados): Observable<Pagina<ChamadoResumo>> {
    const params = montarParams({
      q: f.q?.trim(),
      status: f.status,
      prioridade: f.prioridade,
      semTecnico: f.semTecnico ? true : undefined,
      atrasados: f.atrasados ? true : undefined,
      pagina: f.pagina ?? 0,
      tamanho: f.tamanho ?? 20,
    });
    return this.http.get<Pagina<ChamadoResumo>>(this.base, { params });
  }

  buscar(id: number): Observable<Chamado> {
    return this.http.get<Chamado>(`${this.base}/${id}`);
  }

  abrir(dados: ChamadoCriar): Observable<Chamado> {
    return this.http.post<Chamado>(this.base, dados);
  }

  atribuir(id: number, tecnicoId: number, versao: number): Observable<Chamado> {
    return this.http.put<Chamado>(`${this.base}/${id}/tecnico`, { tecnicoId, versao });
  }

  alterarStatus(id: number, status: StatusChamado, observacao: string | null, versao: number): Observable<Chamado> {
    return this.http.patch<Chamado>(`${this.base}/${id}/status`, { status, observacao, versao });
  }

  resolver(id: number, solucao: string, versao: number): Observable<Chamado> {
    return this.http.post<Chamado>(`${this.base}/${id}/resolver`, { solucao, versao });
  }

  fechar(id: number, versao: number): Observable<Chamado> {
    return this.http.post<Chamado>(`${this.base}/${id}/fechar`, { versao });
  }

  reabrir(id: number, motivo: string, versao: number): Observable<Chamado> {
    return this.http.post<Chamado>(`${this.base}/${id}/reabrir`, { motivo, versao });
  }

  comentarios(id: number): Observable<Pagina<Comentario>> {
    return this.http.get<Pagina<Comentario>>(`${this.base}/${id}/comentarios`, {
      params: montarParams({ tamanho: 100 }),
    });
  }

  comentar(id: number, tipo: TipoComentario, conteudo: string): Observable<Comentario> {
    return this.http.post<Comentario>(`${this.base}/${id}/comentarios`, { tipo, conteudo });
  }

  historico(id: number): Observable<Pagina<Historico>> {
    return this.http.get<Pagina<Historico>>(`${this.base}/${id}/historico`, {
      params: montarParams({ tamanho: 100 }),
    });
  }

  anexos(id: number): Observable<Anexo[]> {
    return this.http.get<Anexo[]>(`${this.base}/${id}/anexos`);
  }

  enviarAnexo(id: number, arquivo: File): Observable<Anexo> {
    const dados = new FormData();
    dados.append('arquivo', arquivo, arquivo.name);
    return this.http.post<Anexo>(`${this.base}/${id}/anexos`, dados);
  }

  baixarAnexo(id: number, anexoId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/anexos/${anexoId}/conteudo`, { responseType: 'blob' });
  }

  avaliar(id: number, nota: number, comentario: string | null): Observable<Avaliacao> {
    return this.http.post<Avaliacao>(`${this.base}/${id}/avaliacao`, { nota, comentario });
  }
}
