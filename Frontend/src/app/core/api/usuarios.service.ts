import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, Perfil, UsuarioCriar, UsuarioResumo } from '../models/api.models';
import { montarParams } from './http-params';

export interface FiltrosUsuarios {
  q?: string;
  perfil?: Perfil | '';
  ativo?: boolean | null;
  pagina?: number;
  tamanho?: number;
}

/** Endpoints restritos a ADMIN. */
@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);
  private readonly base = `/api/usuarios`;

  listar(f: FiltrosUsuarios): Observable<Pagina<UsuarioResumo>> {
    const params = montarParams({
      q: f.q?.trim(),
      perfil: f.perfil,
      ativo: f.ativo,
      pagina: f.pagina ?? 0,
      tamanho: f.tamanho ?? 20,
    });
    return this.http.get<Pagina<UsuarioResumo>>(this.base, { params });
  }

  criar(dados: UsuarioCriar): Observable<unknown> {
    return this.http.post(this.base, dados);
  }

  ativar(id: number): Observable<unknown> {
    return this.http.patch(`${this.base}/${id}/ativar`, null);
  }

  desativar(id: number): Observable<unknown> {
    return this.http.patch(`${this.base}/${id}/desativar`, null);
  }
}
