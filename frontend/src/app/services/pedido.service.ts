import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

/**
 * Servicio para gestionar las peticiones HTTP relacionadas con los pedidos del usuario.
 * ProvidedIn: 'root' (Singleton compartido).
 * 
 * Conecta con el endpoint /api/pedidos del backend. Requiere token JWT.
 */
@Injectable({
  providedIn: 'root'
})
export class PedidoService {
  /** URL base para los endpoints de pedidos */
  private readonly apiUrl = `${environment.apiUrl}/api/pedidos`;

  /**
   * @param http cliente HTTP de Angular.
   * @param authService servicio de autenticación para obtener el token JWT actual.
   */
  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  /**
   * Obtiene la lista de pedidos del usuario autenticado actual.
   * 
   * Incluye cabecera (id, fecha, estado, total) y la lista de detalles
   * (producto, cantidad, precio histórico).
   * 
   * @returns Observable con array de pedidos del usuario.
   */
  obtenerPedidos(): Observable<any[]> {
    const token = this.authService.getToken();
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
    return this.http.get<any[]>(this.apiUrl, { headers });
  }
}
