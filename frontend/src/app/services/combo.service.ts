import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ComboService {
  private readonly combosUrl = `${environment.apiUrl}/api/combos`;

  constructor(private readonly http: HttpClient) { }

  private getAdminHeaders(): HttpHeaders {
    const token = (typeof window !== 'undefined') ? localStorage.getItem('adminToken') : null;
    return new HttpHeaders({ Authorization: `Bearer ${token ?? ''}` });
  }

  /** Obtiene todos los combos activos (público) */
  obtenerCombosActivos(): Observable<any[]> {
    return this.http.get<any[]>(`${this.combosUrl}/activos`);
  }

  /** Activa un combo (requiere token admin) */
  activarCombo(
    productoPrincipalId: number,
    productoAsociadoId: number,
    descuento: number,
    descripcion: string
  ): Observable<any> {
    return this.http.post(
      this.combosUrl,
      { productoPrincipalId, productoAsociadoId, descuento, descripcion },
      { headers: this.getAdminHeaders() }
    );
  }

  /** Desactiva un combo (requiere token admin) */
  desactivarCombo(
    productoPrincipalId: number,
    productoAsociadoId: number
  ): Observable<any> {
    return this.http.delete(
      `${this.combosUrl}/${productoPrincipalId}/${productoAsociadoId}`,
      { headers: this.getAdminHeaders() }
    );
  }
}
