import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Categoria, Pagina, Subcategoria } from '../models/api.models';
import { montarParams } from './http-params';

@Injectable({ providedIn: 'root' })
export class CategoriasService {
  private readonly http = inject(HttpClient);
  private readonly base = `/api/categorias`;

  /** Qualquer perfil autenticado: usada no formulário de abertura de chamado. */
  ativas(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(`${this.base}/ativas`);
  }

  subcategorias(categoriaId: number): Observable<Subcategoria[]> {
    return this.http.get<Subcategoria[]>(`${this.base}/${categoriaId}/subcategorias`);
  }

  /** Somente ADMIN. */
  listar(pagina: number, tamanho = 20): Observable<Pagina<Categoria>> {
    return this.http.get<Pagina<Categoria>>(this.base, { params: montarParams({ pagina, tamanho }) });
  }

  criar(nome: string, descricao: string | null): Observable<Categoria> {
    return this.http.post<Categoria>(this.base, { nome, descricao });
  }

  ativar(id: number): Observable<Categoria> {
    return this.http.patch<Categoria>(`${this.base}/${id}/ativar`, null);
  }

  desativar(id: number): Observable<Categoria> {
    return this.http.patch<Categoria>(`${this.base}/${id}/desativar`, null);
  }
}
