import { Component, OnInit, OnDestroy, AfterViewInit, Input } from '@angular/core';
import { TrackingService, TrackingResponseDTO } from '../../services/tracking.service';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import * as L from 'leaflet';
import { TrackingRutaService, TIENDA_LAT, TIENDA_LNG, VELOCIDAD_KMH, MIN_CONFIRMADO, MIN_DESPACHO, MIN_MOTORIZADO } from '../../services/tracking-ruta.service';

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
          <p><strong>Dirección:</strong> {{ direccionCompleta() }}</p>
          @if (mostrandoSimulacion) {
            <p class="sim-aviso">Repartidor en camino (simulación en vivo)</p>
          }
        </div>
        <div class="progreso-row">
          <div class="progreso-bar"><div class="progreso-fill" [style.width.%]="progreso"></div></div>
          <div class="progreso-meta">
            <span>{{ progreso }}% del camino</span>
            <span>Llega en ~{{ etaMin }} min</span>
          </div>
        </div>
      }

      <div id="map" style="height: 450px; width: 100%; border-radius: 8px; margin-top: 15px;"></div>

      <div class="leyenda">
        <span><img src="icons/farmacia-icon.png" alt="tienda" /> Tienda</span>
        @if (mostrarMoto) {
          <span class="moto">🛵 Repartidor</span>
        }
        <span>📍 Punto de entrega</span>
      </div>
    </div>
  `,
  styles: [`
    .tracking-card { padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; box-shadow: 0 2px 10px rgba(26,46,74,.06); }
    .tracking-card h2 { color: #1a2e4a; margin: 0 0 12px; }
    .info-panel { background: #f8fafc; border-radius: 8px; padding: 10px 14px; }
    .badge { padding: 4px 10px; background-color: #1a2e4a; color: white; border-radius: 20px; font-weight: bold; font-size: .8rem; }
    .sim-aviso { color: #e85d04; font-weight: 700; font-size: .85rem; }
    .progreso-row { margin-top: 12px; }
    .progreso-bar { height: 10px; background: #e2e8f0; border-radius: 6px; overflow: hidden; }
    .progreso-fill { height: 100%; background: linear-gradient(90deg, #1a2e4a, #e85d04); transition: width 1.5s linear; }
    .progreso-meta { display: flex; justify-content: space-between; font-size: .8rem; color: #64748b; margin-top: 4px; font-weight: 600; }
    .leyenda { display: flex; gap: 18px; margin-top: 10px; font-size: .82rem; color: #475569; font-weight: 600; align-items: center; }
    .leyenda img { width: 22px; height: 22px; vertical-align: middle; }
    .leyenda .moto { font-size: 1rem; }
  `]
})
export class TrackingMapaComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() idPedido!: number;
  @Input() fechaPedido?: string;

  pedido?: TrackingResponseDTO;
  progreso = 0;
  etaMin = 0;
  tripMin = 0;
  mostrandoSimulacion = false;
  // La moto solo aparece cuando hay motorizado asignado/en camino.
  mostrarMoto = false;

  private map!: L.Map;
  private markerTienda!: L.Marker;
  private markerCliente!: L.Marker;
  private markerRepartidor?: L.Marker;
  private lineaRuta?: L.Polyline;
  private lineaRecorrida?: L.Polyline;

  // Simulación: puntos interpolados tienda -> entrega y posición actual.
  private rutaSimulada: Array<[number, number]> = [];
  private pasoSim = 0;

  // Guardamos la suscripción RxJS para cancelarla al destruir el componente
  private pollingSub?: Subscription;

  constructor(private trackingService: TrackingService, private ruta: TrackingRutaService) {}

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
          const est0 = (data.estado || '').toUpperCase();
          this.mostrarMoto = est0.includes('EN_CAMINO') || est0.includes('COMPLETAD') || est0.includes('ENTREGAD');

          // Si el mapa aún no ha sido dibujado, se resuelve el destino real y se inicializa.
          if (!this.map) {
            this.resolverDestinoYCrearMapa();
          }

          if (this.tieneCoordsReales()) {
            // GPS real del repartidor: se usa tal cual y se apaga la simulación.
            this.mostrandoSimulacion = false;
            this.actualizarPosicionRepartidor();
          } else {
            // Sin GPS real: simulación suave tienda -> entrega.
            this.mostrandoSimulacion = true;
            this.avanzarSimulacion();
          }

          // Pedido entregado, completado, rechazado o cancelado: ya no hay nada que seguir.
          const est = (data.estado || '').toUpperCase();
          if ((est.includes('ENTREGAD') || est.includes('COMPLETAD') || est.includes('CANCELAD') || est.includes('RECHAZAD')) && this.pollingSub) {
            this.progreso = est.includes('RECHAZAD') || est.includes('CANCELAD') ? 0 : 100;
            this.etaMin = 0;
            this.pollingSub.unsubscribe();
          }
        },
        error: (err) => {
          console.error('Error al obtener la ubicación del pedido:', err);
        }
      });
  }

  private tieneCoordsReales(): boolean {
    return !!this.pedido && !!this.pedido.latitudRepartidor && !!this.pedido.longitudRepartidor;
  }

  /** Tope de progreso (%) según el estado real: la animación nunca corre más rápido que el pedido. */
  private topePorEstado(): number {
    const est = (this.pedido?.estado || '').toUpperCase();
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 100;
    if (est.includes('EN_CAMINO')) return 95;
    if (est.includes('RECHAZAD') || est.includes('CANCELAD')) return 0;
    return 0; // PAGADO, CONFIRMADO, EN_ESPERA: el motorizado sigue en tienda.
  }

  /** Resuelve el destino con el servicio compartido y crea el mapa. */
  private async resolverDestinoYCrearMapa(): Promise<void> {
    if (!this.pedido || this.map) return;
    const [lat, lng] = await this.ruta.resolverDestino(this.pedido);
    this.initMap(lat, lng);
  }

  /** Dirección sin duplicar el distrito si ya viene incluido. */
  direccionCompleta(): string {
    const d = this.pedido?.direccionEnvio || '';
    const dist = this.pedido?.distrito || '';
    if (!dist || d.toLowerCase().includes(dist.toLowerCase())) return d || dist;
    return `${d} (${dist})`;
  }

  private initMap(latDestino: number, lngDestino: number): void {
    if (!this.pedido) return;

    // 1. Instanciar el mapa con vista de toda la ruta tienda -> entrega.
    this.map = L.map('map');
    this.map.fitBounds(L.latLngBounds([TIENDA_LAT, TIENDA_LNG], [latDestino, lngDestino]).pad(0.25));

    // 2. Agregar la capa de mapa base de OpenStreetMap
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    // 3. Marcador de la tienda (punto de partida).
    const iconoTienda = L.icon({
      iconUrl: 'icons/farmacia-icon.png',
      iconSize: [40, 40],
      iconAnchor: [20, 40]
    });
    this.markerTienda = L.marker([TIENDA_LAT, TIENDA_LNG], { icon: iconoTienda })
      .addTo(this.map)
      .bindPopup('<b>Botica Anita</b><br>Esq. Las Violetas con Av. Los Jazmines, Independencia');

    // 4. Marcador del destino (Cliente).
    const iconoDestino = L.icon({
      iconUrl: 'icons/casa-icon.png',
      iconSize: [32, 32],
      iconAnchor: [16, 32]
    });

    this.markerCliente = L.marker([latDestino, lngDestino], { icon: iconoDestino })
      .addTo(this.map)
      .bindPopup(`<b>Punto de Entrega</b><br>${this.pedido.direccionEnvio || ''}`);

    // 5. Línea de ruta completa tienda -> entrega.
    this.lineaRuta = L.polyline([[TIENDA_LAT, TIENDA_LNG], [latDestino, lngDestino]], {
      color: '#94a3b8', weight: 4, dashArray: '8 8'
    }).addTo(this.map);

    // 6. Preparar la simulación y crear el marcador del repartidor.
    this.prepararSimulacion(latDestino, lngDestino);
    this.actualizarPosicionRepartidor();
  }

  /** Divide el tramo tienda -> entrega en pasos para animar el avance. */
  private prepararSimulacion(latDestino: number, lngDestino: number): void {
    const distM = this.ruta.distanciaM(TIENDA_LAT, TIENDA_LNG, latDestino, lngDestino);
    this.tripMin = this.ruta.tripMin(distM);
    const pasos = Math.max(20, this.tripMin * 8);
    this.rutaSimulada = [];
    for (let i = 0; i <= pasos; i++) {
      const f = i / pasos;
      this.rutaSimulada.push([
        TIENDA_LAT + (latDestino - TIENDA_LAT) * f,
        TIENDA_LNG + (lngDestino - TIENDA_LNG) * f
      ]);
    }
    this.pasoSim = 0;
    this.actualizarEta(distM);
  }

  /** Avanza según el tiempo real desde el pedido: misma distancia y tiempos que el timeline. */
  private avanzarSimulacion(): void {
    if (!this.map || this.rutaSimulada.length === 0) return;
    const tope = this.topePorEstado();
    const topePaso = Math.floor((tope / 100) * (this.rutaSimulada.length - 1));
    let objetivo = topePaso;
    if (this.fechaPedido && this.tripMin > 0) {
      const elapsedMin = (Date.now() - new Date(this.fechaPedido).getTime()) / 60000;
      const inicioViaje = MIN_CONFIRMADO + MIN_DESPACHO + MIN_MOTORIZADO;
      const frac = Math.min(Math.max((elapsedMin - inicioViaje) / this.tripMin, 0), 1);
      objetivo = Math.min(Math.floor(frac * (this.rutaSimulada.length - 1)), topePaso);
    }
    // Nunca retrocede (evita saltos por redondeo entre polls).
    this.pasoSim = Math.max(this.pasoSim, Math.min(objetivo, topePaso));
    const pos = this.rutaSimulada[this.pasoSim];
    this.pintarRepartidor(pos[0], pos[1]);
    this.progreso = Math.round((this.pasoSim / (this.rutaSimulada.length - 1)) * 100);
    const restante = this.ruta.distanciaM(pos[0], pos[1],
      this.rutaSimulada[this.rutaSimulada.length - 1][0],
      this.rutaSimulada[this.rutaSimulada.length - 1][1]);
    this.actualizarEta(restante);
  }

  private actualizarPosicionRepartidor(): void {
    if (!this.pedido || !this.map) return;
    if (this.tieneCoordsReales()) {
      const lat = Number(this.pedido.latitudRepartidor);
      const lng = Number(this.pedido.longitudRepartidor);
      this.pintarRepartidor(lat, lng);
      this.progreso = 100;
      this.etaMin = 0;
      return;
    }
    // Sin GPS real, la posición la maneja la simulación.
    if (this.rutaSimulada.length > 0) {
      const pos = this.rutaSimulada[this.pasoSim];
      this.pintarRepartidor(pos[0], pos[1]);
    }
  }

  private pintarRepartidor(lat: number, lng: number): void {
    // Sin motorizado asignado no se muestra la moto.
    if (!this.mostrarMoto) {
      if (this.markerRepartidor && this.map) {
        this.map.removeLayer(this.markerRepartidor);
        this.markerRepartidor = undefined;
      }
      return;
    }
    const iconoRepartidor = L.icon({
      iconUrl: 'icons/delivery-icon.png',
      iconSize: [38, 38],
      iconAnchor: [19, 38]
    });
    if (this.markerRepartidor) {
      // Si el marcador del repartidor ya existe en el mapa, solo movemos suavemente sus coordenadas
      this.markerRepartidor.setLatLng([lat, lng]);
      this.markerRepartidor.setIcon(iconoRepartidor);
    } else if (this.map) {
      // Si no existía, lo creamos y lo añadimos al mapa
      this.markerRepartidor = L.marker([lat, lng], { icon: iconoRepartidor })
        .addTo(this.map)
        .bindPopup('<b>Repartidor en camino</b>');
    }
    // Línea naranja con lo ya recorrido.
    const recorridos = this.rutaSimulada.slice(0, this.pasoSim + 1);
    if (recorridos.length > 1) {
      if (this.lineaRecorrida) {
        this.lineaRecorrida.setLatLngs(recorridos);
      } else {
        this.lineaRecorrida = L.polyline(recorridos, { color: '#e85d04', weight: 5 }).addTo(this.map);
      }
    }
  }

  private actualizarEta(distM: number): void {
    this.etaMin = Math.max(1, Math.round((distM / 1000 / VELOCIDAD_KMH) * 60));
  }
}
