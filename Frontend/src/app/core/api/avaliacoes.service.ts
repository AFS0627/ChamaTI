import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../config';
import { Avaliacao, MediaTecnico, Pagina } from '../models/api.models';
import { montarParams } from './http-params';

/** Endpoints restritos a ADMIN. */
@Injectable({ providedIn: 'root' })
export class AvaliacoesService {
  private readonly http = inject(HttpClient);

  listar(pagina: number, tamanho = 20): Observable<Pagina<Avaliacao>> {
    return this.http.get<Pagina<Avaliacao>>(`${API_URL}/api/avaliacoes`, {
      params: montarParams({ pagina, tamanho }),
    });
  }

  mediasPorTecnico(): Observable<MediaTecnico[]> {
    return this.http.get<MediaTecnico[]>(`${API_URL}/api/avaliacoes/tecnicos`);
  }
}
