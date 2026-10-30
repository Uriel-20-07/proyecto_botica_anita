import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/** Respuesta de GET /api/calificaciones/pendiente */
export interface CalificacionPendiente {
  pendiente: boolean;
  idPedido?: number;
  estado?: string;
}

/** Calificación de satisfacción del cliente (1 a 5 estrellas + comentario opcional). */
@Injectable({ providedIn: 'root' })
export class CalificacionService {
  private readonly apiUrl = `${environment.apiUrl}/api/calificaciones`;

  constructor(private http: HttpClient, private authService: AuthService) {}

  private headers(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.authService.getToken()}` });
  }

  /** ¿Tiene el cliente un pedido (listo para recojo o completado) pendiente de calificar? */
  obtenerPendiente(): Observable<CalificacionPendiente> {
    return this.http.get<CalificacionPendiente>(`${this.apiUrl}/pendiente`, { headers: this.headers() });
  }

  /** Envía la calificación de un pedido. */
  enviar(idPedido: number, estrellas: number, comentario: string): Observable<any> {
    return this.http.post(this.apiUrl, { idPedido, estrellas, comentario }, { headers: this.headers() });
  }
}
