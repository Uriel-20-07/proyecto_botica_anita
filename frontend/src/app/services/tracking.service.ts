import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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
  private apiUrl = 'http://localhost:8080/api/pedidos'; // Ajusta la URL de tu Spring Boot

  constructor(private http: HttpClient) {}

  getTrackingInfo(idPedido: number): Observable<TrackingResponseDTO> {
    return this.http.get<TrackingResponseDTO>(`${this.apiUrl}/${idPedido}/tracking`);
  }
}