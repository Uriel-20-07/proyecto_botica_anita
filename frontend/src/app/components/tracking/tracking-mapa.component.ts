import { Component, OnInit, OnDestroy, AfterViewInit, Input } from '@angular/core';
import { TrackingService, TrackingResponseDTO } from '../../services/tracking.service';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import * as L from 'leaflet';

@Component({
  selector: 'app-tracking-mapa',
  standalone: true, // Si es un componente standalone
  template: `
    <div class="tracking-card">
      <h2>Seguimiento del Pedido #{{ idPedido }}</h2>
      
      <!-- Control de flujo moderno @if -->
      @if (pedido) {
        <div class="info-panel">
          <p><strong>Estado:</strong> <span class="badge">{{ pedido.estado }}</span></p>
          <p><strong>Dirección:</strong> {{ pedido.direccionEnvio }} ({{ pedido.distrito }})</p>
        </div>
      }

      <div id="map" style="height: 450px; width: 100%; border-radius: 8px; margin-top: 15px;"></div>
    </div>
  `,
  styles: [`
    .tracking-card { padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px; background: #fff; }
    .badge { padding: 4px 8px; background-color: #007bff; color: white; border-radius: 4px; font-weight: bold; }
  `]
})
export class TrackingMapaComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() idPedido!: number;

  pedido?: TrackingResponseDTO;
  private map!: L.Map;
  private markerCliente!: L.Marker;
  private markerRepartidor!: L.Marker;
  
  // Guardamos la suscripción RxJS para cancelarla al destruir el componente
  private pollingSub?: Subscription;

  constructor(private trackingService: TrackingService) {}

  ngOnInit(): void {
    this.iniciarPolling();
  }

  ngAfterViewInit(): void {
    // Corregir posible renderizado gris de Leaflet ajustando el tamaño tras cargar la vista
    setTimeout(() => {
      if (this.map) {
        this.map.invalidateSize();
      }
    }, 400);
  }

  ngOnDestroy(): void {
    // Cancelar la consulta periódica si el usuario sale de esta pantalla
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
    }
  }

  /**
   * Configuración del Polling con RxJS:
   * timer(0, 8000) -> Ejecuta inmediatamente (0 ms) y luego repite cada 8000 ms (8s).
   * switchMap(...) -> Cancela la petición HTTP anterior si aún no ha terminado antes de hacer la nueva.
   */
  private iniciarPolling(): void {
    this.pollingSub = timer(0, 8000)
      .pipe(
        switchMap(() => this.trackingService.getTrackingInfo(this.idPedido))
      )
      .subscribe({
        next: (data) => {
          this.pedido = data;

          // Si el mapa aún no ha sido dibujado, se inicializa por primera vez
          if (!this.map) {
            this.initMap();
          } else {
            // Si el mapa ya existe, actualizamos únicamente la posición del repartidor
            this.actualizarPosicionRepartidor();
          }
        },
        error: (err) => {
          console.error('Error al obtener la ubicación del pedido:', err);
        }
      });
  }

  private initMap(): void {
    if (!this.pedido) return;

    // Usar coordenadas del cliente o valor por defecto (ejemplo: Lima, Perú)
    const latDestino = this.pedido.latitudEntrega || -12.046374;
    const lngDestino = this.pedido.longitudEntrega || -77.042793;

    // 1. Instanciar el mapa centrado en el destino de entrega
    this.map = L.map('map').setView([latDestino, lngDestino], 14);

    // 2. Agregar la capa de mapa base de OpenStreetMap
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    // 3. Crear marcador del destino (Cliente)
    const iconoDestino = L.icon({
      iconUrl: 'assets/icons/casa-icon.png', // Coloca la imagen en src/assets/icons/
      iconSize: [32, 32],
      iconAnchor: [16, 32]
    });

    this.markerCliente = L.marker([latDestino, lngDestino], { icon: iconoDestino })
      .addTo(this.map)
      .bindPopup(`<b>Punto de Entrega</b><br>${this.pedido.direccionEnvio || ''}`);

    // 4. Crear marcador inicial del repartidor si ya tiene coordenadas asociadas
    this.actualizarPosicionRepartidor();
  }

  private actualizarPosicionRepartidor(): void {
    if (!this.pedido || !this.pedido.latitudRepartidor || !this.pedido.longitudRepartidor) {
      return;
    }

    const nuevaPos: L.LatLngExpression = [
      this.pedido.latitudRepartidor,
      this.pedido.longitudRepartidor
    ];

    if (this.markerRepartidor) {
      // Si el marcador del repartidor ya existe en el mapa, solo movemos suavemente sus coordenadas
      this.markerRepartidor.setLatLng(nuevaPos);
    } else if (this.map) {
      // Si no existía, lo creamos y lo añadimos al mapa
      const iconoRepartidor = L.icon({
        iconUrl: 'assets/icons/delivery-icon.png', // Coloca la imagen en src/assets/icons/
        iconSize: [35, 35],
        iconAnchor: [17, 35]
      });

      this.markerRepartidor = L.marker(nuevaPos, { icon: iconoRepartidor })
        .addTo(this.map)
        .bindPopup('<b>Repartidor en camino</b>');
    }
  }
}