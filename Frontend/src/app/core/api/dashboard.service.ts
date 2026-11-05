import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../config';
import { Dashboard } from '../models/api.models';
import { montarParams } from './http-params';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  resumo(dias: number): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${API_URL}/api/dashboard/resumo`, { params: montarParams({ dias }) });
  }
}
