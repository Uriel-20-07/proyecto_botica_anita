import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export interface RecetaMedica {
  id?: string;
  idReceta?: number;
  pacienteId: string;
  medicoId: string;
  medicamentos: Array<{ nombre: string; dosis: string }>;
  fechaEmision: string;
  estado: 'en_espera' | 'aprobada' | 'rechazada';
  documentoUrl?: string;
}

@Injectable({
  providedIn: 'root'
})
export class RecetasService {
  private apiUrl = `${environment.apiUrl}/api/recetas`;

  constructor(private http: HttpClient, private authService: AuthService) { }

  private getHeaders(): HttpHeaders {
    let token = this.authService.getToken();
    if (typeof window !== 'undefined') {
      const isDashboardAdmin = window.location.pathname.includes('/dashboard/admin');
      const adminToken = localStorage.getItem('adminToken');
      if (adminToken && (isDashboardAdmin || !token)) {
        token = adminToken;
      }
    }
    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  cargarReceta(receta: RecetaMedica): Observable<RecetaMedica> {
    return this.http.post<RecetaMedica>(this.apiUrl, receta, { headers: this.getHeaders() });
  }

  getRecetasEnEspera(): Observable<RecetaMedica[]> {
    return this.http.get<RecetaMedica[]>(`${this.apiUrl}?estado=EN_ESPERA`, { headers: this.getHeaders() });
  }

  actualizarEstadoReceta(id: string, nuevoEstado: 'aprobada' | 'rechazada', comentario?: string): Observable<RecetaMedica> {
    const endpoint = nuevoEstado === 'aprobada' ? 'aprobar' : 'rechazar';
    const body = comentario ? { comentario } : {};
    return this.http.patch<RecetaMedica>(`${this.apiUrl}/${id}/${endpoint}`, body, { headers: this.getHeaders() });
  }

  subirDocumento(file: File): Observable<{ url: string }> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<{ url: string }>(`${this.apiUrl}/upload`, formData, { headers: this.getHeaders() });
  }
}