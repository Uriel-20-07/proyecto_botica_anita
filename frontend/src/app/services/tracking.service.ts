import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface TrackingResponseDTO {
  idPedido: number;
  estado: string;
  direccionEnvio: string;
  distrito: string;
  esUrgente: boolean;
  latitudEntrega: number;
  longitudEntrega: number;
  repartidorId: number;
  latitudRepartidor: number;
  longitudRepartidor: number;
  ultimaActualizacionUbicacion: string;
}

@Injectable({
  providedIn: 'root'
})
export class TrackingService {
  private apiUrl = `${environment.apiUrl}/api/pedidos-tracking`;

  constructor(private http: HttpClient) {}

  getTrackingInfo(idPedido: number): Observable<TrackingResponseDTO> {
    return this.http.get<TrackingResponseDTO>(`${this.apiUrl}/${idPedido}/tracking`);
  }
}