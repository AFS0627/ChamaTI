import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Dashboard } from '../models/api.models';
import { montarParams } from './http-params';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  resumo(dias: number): Observable<Dashboard> {
    return this.http.get<Dashboard>(`/api/dashboard/resumo`, { params: montarParams({ dias }) });
  }
}
