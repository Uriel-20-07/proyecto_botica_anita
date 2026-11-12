import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AsistenteService {
  private readonly apiUrl = `${environment.apiUrl}/api/asistente`;

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    const token = (typeof window !== 'undefined') ? localStorage.getItem('adminToken') : null;
    return new HttpHeaders({ Authorization: `Bearer ${token ?? ''}` });
  }

  listarProductos(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/productos`, { headers: this.headers() });
  }

  crearVenta(body: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/venta`, body, { headers: this.headers() });
  }

  listarPedidosPendientes(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/pedidos`, { headers: this.headers() });
  }

  listarPedidosEntregados(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/pedidos/entregados`, { headers: this.headers() });
  }

  marcarEntregado(idPedido: number): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/pedidos/${idPedido}/entregar`, {}, { headers: this.headers() });
  }

  historialVentasHoy(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/ventas/historial`, { headers: this.headers() });
  }
}
