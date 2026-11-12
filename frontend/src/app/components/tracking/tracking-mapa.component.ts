import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  Input,
  ElementRef,
  ViewChild,
  signal,
  computed,
} from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import * as maplibregl from 'maplibre-gl';

// MapLibre necesita el worker de WebGL; en bundles (Angular/Vite) la URL se pierde,
// así que se fija explícitamente a la versión instalada.
maplibregl.setWorkerUrl('/maplibre-gl-worker.mjs');
import { TrackingService, TrackingResponseDTO } from '../../services/tracking.service';
import { PedidoService } from '../../services/pedido.service';
import {
  MapaRutaService,
  BOTICA_ANITA,
  MIN_CONFIRMADO,
  MIN_DESPACHO,
  MIN_MOTORIZADO,
  MIN_ENTREGA_EXTRA,
  RutaInfo,
} from '../../services/mapa-ruta.service';
import { msDesdeFecha } from '../../services/fecha-util';

@Component({
  selector: 'app-tracking-mapa',
  standalone: true,
  template: `
    <div class="tracking-card">
      <!-- Header del seguimiento -->
      <div class="tracking-header">
        <h2>Seguimiento del Pedido #{{ idPedido }}</h2>
        <span class="estado-badge" [class.entregado]="estadoFinal()">{{ estadoTexto() }}</span>
      </div>

      @if (pedido()) {
        <div class="info-panel">
          <p><strong>Estado:</strong> <span class="badge">{{ pedido()?.estado }}</span></p>
          <p><strong>Dirección:</strong> {{ direccionCompleta() }}</p>
          @if (mostrandoSimulacion()) {
            <p class="sim-aviso">🛵 Repartidor en camino (simulación en vivo)</p>
          }
        </div>
      }

      <!-- Contenedor del mapa: NO se destruye con *ngIf para evitar romper el WebGL de Mapbox.
           Se oculta con una clase CSS y se recalcula con map.resize() al volver a mostrarse. -->
      <div class="mapa-wrapper" [class.hidden]="mapaOculto()">
        <div #mapContainer class="map-container"></div>

        <!-- Botón re-centrar -->
        <button class="btn-recentrar" (click)="recentrar()" title="Re-centrar mapa">🎯</button>

        <!-- Tarjeta flotante compacta de estilo Rappi -->
        <div class="floating-card" [class.hidden]="!mostrarFloatingCard()">
          <div class="card-body">
            <div class="card-row">
              <span class="icon-eta">🕒</span>
              <span class="eta-big">{{ etaMin() > 0 ? (etaMin() + ' min') : 'Entregado' }}</span>
              @if (etaMin() > 0) {
                <span class="eta-hora">≈ {{ etaHora() }}</span>
              }
              <span class="distancia">{{ distanciaKm().toFixed(1) }} km</span>
            </div>
            <div class="progreso-ruta">
              <div class="barra" [style.width.%]="progreso()"></div>
            </div>
            <div class="timeline">
              @for (p of pasos; track p.id) {
                <div class="paso" [class.activo]="pasoActual() >= p.id" [class.actual]="pasoActual() === p.id">
                  <span class="punto-paso"></span>
                  <span class="label-paso">{{ p.label }}</span>
                </div>
              }
            </div>
            <div class="card-row2">
              <span class="nota-entrega">{{ notaEntrega() }}</span>
              <span class="costo">{{ costoEnvioTexto() }}</span>
            </div>
            @if (pedido()?.distrito) {
              <div class="distrito-chip">
                <span>📍 {{ pedido()?.distrito }}</span>
                @if (usandoFallback) {
                  <span class="aprox">(ubicación estimada)</span>
                }
              </div>
            }
          </div>
        </div>

        <!-- Leyenda inferior con iconos reales -->
        <div class="leyenda">
          <span class="item tienda">
            <span class="icono-leyenda" style="background-image:url('/icons/farmacia-icon.png')"></span>
            <span class="texto">Botica Anita</span>
          </span>
          @if (mostrarMoto()) {
            <span class="item moto">
              <span class="icono-leyenda" style="background-image:url('/icons/delivery-icon.png')"></span>
              <span class="texto">Repartidor</span>
            </span>
          }
          <span class="item destino">
            <span class="icono-leyenda" style="background-image:url('/icons/casa-icon.png')"></span>
            <span class="texto">Tu domicilio</span>
          </span>
        </div>
      </div>

      @if (!pedido() && !mapaOculto()) {
        <div class="sin-datos">Cargando seguimiento del pedido…</div>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      }

      .tracking-card {
        padding: 0;
        border: 1px solid #e2e8f0;
        border-radius: 16px;
        background: #ffffff;
        box-shadow: 0 4px 20px rgba(26, 46, 74, 0.08);
        overflow: hidden;
      }

      .tracking-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 16px 20px;
        border-bottom: 1px solid #f1f5f9;
        background: #ffffff;
      }

      .tracking-header h2 {
        margin: 0;
        font-size: 1.1rem;
        color: #1a2e4a;
        font-weight: 700;
      }

      .estado-badge {
        font-size: 0.75rem;
        font-weight: 700;
        padding: 4px 10px;
        border-radius: 20px;
        background: #f1f5f9;
        color: #475569;
      }

      .estado-badge.entregado {
        background: #dcfce7;
        color: #166534;
      }

      .info-panel {
        background: #f8fafc;
        border-bottom: 1px solid #f1f5f9;
        padding: 12px 20px;
      }

      .info-panel p {
        margin: 4px 0;
        font-size: 0.85rem;
        color: #334155;
      }

      .badge {
        font-weight: 700;
        color: #1a2e4a;
      }

      .sim-aviso {
        margin-top: 6px;
        color: #e85d04;
        font-weight: 700;
        font-size: 0.8rem;
      }

      /* Mapa */
      .mapa-wrapper {
        position: relative;
        height: 420px;
        width: 100%;
        transition: opacity 0.25s ease, height 0.25s ease;
      }

      .mapa-wrapper.hidden {
        opacity: 0;
        height: 0;
        overflow: hidden;
        pointer-events: none;
      }

      .map-container {
        height: 100%;
        width: 100%;
        border-radius: 0;
      }

      .sin-datos {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #64748b;
        font-size: 0.95rem;
        background: #f8fafc;
      }

      /* Tarjeta flotante compacta de estilo Rappi */
      .floating-card {
        position: absolute;
        top: 12px;
        right: 12px;
        left: auto;
        max-width: 260px;
        z-index: 50;
        background: #ffffff;
        border-radius: 12px;
        box-shadow: 0 6px 24px rgba(26, 46, 74, 0.18);
        border: 1px solid rgba(26, 46, 74, 0.08);
        overflow: hidden;
        transition: transform 0.3s ease, opacity 0.3s ease;
      }

      .floating-card.hidden {
        transform: translateY(120%);
        opacity: 0;
        pointer-events: none;
      }

      .card-body {
        padding: 10px 12px;
      }

      .card-row {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .icon-eta {
        font-size: 1rem;
      }

      .eta-big {
        font-size: 1.5rem;
        font-weight: 800;
        color: #e85d04;
        line-height: 1;
      }

      .distancia {
        margin-left: auto;
        font-size: 0.8rem;
        font-weight: 700;
        color: #1a2e4a;
        background: #f1f5f9;
        padding: 2px 8px;
        border-radius: 8px;
      }

      .progreso-ruta {
        margin-top: 8px;
        height: 4px;
        background: #e2e8f0;
        border-radius: 2px;
        overflow: hidden;
      }

      .progreso-ruta .barra {
        height: 100%;
        background: linear-gradient(90deg, #1a2e4a, #e85d04);
        border-radius: 2px;
        transition: width 0.6s ease;
      }

      .card-row2 {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 6px;
        gap: 8px;
      }

      .nota-entrega {
        font-size: 0.72rem;
        color: #64748b;
        font-weight: 600;
        flex: 1;
      }

      .costo {
        font-size: 0.78rem;
        font-weight: 700;
        color: #1a2e4a;
        white-space: nowrap;
      }

      .eta-hora {
        font-size: 0.72rem;
        font-weight: 600;
        color: #64748b;
        background: #f1f5f9;
        padding: 2px 6px;
        border-radius: 6px;
      }

      .timeline {
        display: flex;
        justify-content: space-between;
        margin-top: 10px;
        padding-top: 8px;
        border-top: 1px solid #f1f5f9;
        gap: 2px;
      }

      .paso {
        display: flex;
        flex-direction: column;
        align-items: center;
        flex: 1;
        opacity: 0.4;
        transition: opacity 0.3s ease;
      }

      .paso.activo {
        opacity: 1;
      }

      .punto-paso {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #cbd5e1;
        margin-bottom: 4px;
        transition: background 0.3s ease, transform 0.3s ease;
      }

      .paso.activo .punto-paso {
        background: #1a2e4a;
      }

      .paso.actual .punto-paso {
        background: #e85d04;
        transform: scale(1.4);
        box-shadow: 0 0 0 3px rgba(232, 93, 4, 0.2);
      }

      .label-paso {
        font-size: 0.6rem;
        font-weight: 600;
        color: #64748b;
        text-align: center;
        line-height: 1.1;
      }

      .paso.actual .label-paso {
        color: #e85d04;
        font-weight: 700;
      }

      /* Leyenda inferior */
      .leyenda {
        display: flex;
        gap: 16px;
        padding: 10px 20px;
        background: #f8fafc;
        border-top: 1px solid #f1f5f9;
        font-size: 0.8rem;
        font-weight: 600;
        color: #475569;
        align-items: center;
      }

      .leyenda .item {
        display: flex;
        align-items: center;
        gap: 6px;
      }

      .leyenda .punto {
        width: 16px;
        height: 16px;
        border-radius: 50%;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 10px;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
      }

      .icono-leyenda {
        width: 18px;
        height: 18px;
        background-size: contain;
        background-repeat: no-repeat;
        background-position: center;
        display: inline-block;
        filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
      }

      .btn-recentrar {
        position: absolute;
        bottom: 12px;
        right: 12px;
        z-index: 60;
        width: 40px;
        height: 40px;
        border-radius: 50%;
        border: 1px solid rgba(26, 46, 74, 0.12);
        background: #ffffff;
        box-shadow: 0 4px 12px rgba(26, 46, 74, 0.18);
        cursor: pointer;
        font-size: 1.1rem;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: transform 0.15s ease, box-shadow 0.15s ease;
      }

      .btn-recentrar:hover {
        transform: scale(1.08);
        box-shadow: 0 6px 16px rgba(26, 46, 74, 0.24);
      }

      .distrito-chip {
        margin-top: 8px;
        font-size: 0.7rem;
        color: #475569;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 4px 8px;
        display: flex;
        align-items: center;
        gap: 6px;
      }

      .distrito-chip .aprox {
        color: #b45309;
        font-style: italic;
      }

      .punto-tienda {
        background: #1a2e4a;
      }
      .punto-moto {
        background: #e85d04;
      }
      .punto-destino {
        background: #10b981;
      }

      /* Marcadores de mapa con iconos */
      .marker-tienda,
      .marker-destino,
      .marker-repartidor {
        cursor: pointer;
        transition: transform 0.2s ease;
      }
      .marker-tienda:hover,
      .marker-destino:hover,
      .marker-repartidor:hover {
        transform: scale(1.15);
      }
      .marker-tienda img,
      .marker-destino img,
      .marker-repartidor img {
        display: block;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.35));
      }
    `,
  ],
})
export class TrackingMapaComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() idPedido!: number;
  @Input() fechaPedido?: string;

  /** Tipo de entrega: 'delivery' o 'recojo'. Se inyecta desde el padre. */
  @Input() tipoEntrega: 'delivery' | 'recojo' = 'delivery';

  /** Estado actual calculado por tiempo (0-4), pasado desde el padre para no adelantarse. */
  @Input() estadoActualMapa?: number;

  /** Señales reactivas para el estado del pedido. */
  pedido = signal<TrackingResponseDTO | null>(null);
  progreso = signal(0);
  etaMin = signal(0);
  distanciaKm = signal(0);
  costoEnvio = signal(0);
  mostrandoSimulacion = signal(false);

  /** Pasos del timeline de entrega (0..4). */
  pasos = [
    { id: 0, label: 'Confirmado' },
    { id: 1, label: 'Despacho' },
    { id: 2, label: 'Motorizado' },
    { id: 3, label: 'En camino' },
    { id: 4, label: 'Entregado' },
  ];

  private map?: maplibregl.Map;
  private creandoMapa = false;
  private destLat = BOTICA_ANITA.lat;
  private destLng = BOTICA_ANITA.lng;
  usandoFallback = false;
  private bearingActual = 0;
  /** Coordenadas corregidas manualmente por el usuario (persisten entre polls). */
  private coordsOverride: { lat: number; lng: number } | null = null;
  private ultimasCoordsDestino: { lat: number; lng: number } | null = null;
  private markerTienda?: maplibregl.Marker;
  private markerDestino?: maplibregl.Marker;
  private markerRepartidor?: maplibregl.Marker;

  private rutaSimulada: Array<[number, number]> = [];
  private pasoSim = 0;
  private lineaRutaId = 'ruta-botica-cliente';
  private lineaRecorridaId = 'ruta-recorrida';

  // Guardamos la suscripción RxJS para cancelarla al destruir el componente
  private pollingSub?: Subscription;

  @ViewChild('mapContainer') mapContainer?: ElementRef<HTMLDivElement>;

  constructor(
    private trackingService: TrackingService,
    private mapaService: MapaRutaService,
    private pedidoService: PedidoService
  ) {}

  /** Señales derivadas para el template. */
  estadoFinal = computed(() => {
    const est = (this.pedido()?.estado || '').toUpperCase();
    return (
      est.includes('COMPLETAD') ||
      est.includes('ENTREGAD') ||
      est.includes('CANCELAD') ||
      est.includes('RECHAZAD')
    );
  });

  mapaOculto = computed(() => {
    // El mapa solo se oculta cuando el pedido ya se entregó/completó
    const ped = this.pedido();
    if (!ped) return false;
    const est = (ped.estado || '').toUpperCase();
    if (this.tipoEntrega === 'recojo') return true;
    const oculto = est.includes('COMPLETAD') || est.includes('ENTREGAD');
    return oculto;
  });

  mostrarFloatingCard = computed(() => {
    if (this.mapaOculto()) return false;
    // Mostrar la tarjeta desde el principio para que el usuario vea el progreso.
    return this.tipoEntrega === 'delivery';
  });

  mostrarMoto = computed(() => {
    if (this.tipoEntrega !== 'delivery') return false;
    const ped = this.pedido();
    if (!ped) return false;
    const est = (ped.estado || '').toUpperCase();
    if (est.includes('CANCELAD') || est.includes('RECHAZAD')) return false;
    // La moto aparece a partir de "Motorizado asignado" (paso >= 2).
    return this.pasoEfectivo() >= 2;
  });

  /** La ruta (gris + naranja) solo se muestra a partir de "En camino" (paso >= 3). */
  mostrarRuta = computed(() => {
    if (this.tipoEntrega !== 'delivery') return false;
    return this.pasoEfectivo() >= 3;
  });

  estadoTexto = computed(() => {
    const ped = this.pedido();
    if (!ped) return 'Cargando…';

    // Usar el paso efectivo (padre o derivado del estado del pedido) para que
    // el badge coincida con el estado mostrado en la página de seguimiento.
    const paso = this.pasoEfectivo();
    const est = (ped.estado || '').toUpperCase();

    // Si el pedido está en espera/verificación, no mostrar estados de entrega
    if (est.includes('EN_ESPERA') || est.includes('ESPERANDO') || est.includes('PENDIENTE_VERIFICACION')) {
      return est === 'PENDIENTE_VERIFICACION' ? 'Verificando pago' : 'Esperando confirmación';
    }

    // Mapear el paso (0-4) al texto correspondiente, respetando el tiempo real
    switch (paso) {
      case 0: return 'Pedido confirmado';
      case 1: return 'En despacho';
      case 2: return this.tipoEntrega === 'delivery' ? 'Motorizado asignado' : 'Listo para recojo';
      case 3: return 'En camino';
      case 4: return 'Entregado';
      default:
        // Fallback por estado crudo si no hay paso claro
        if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 'Entregado';
        if (est.includes('EN_CAMINO')) return 'En camino';
        if (est.includes('LISTO')) return 'Listo para recojo';
        if (est.includes('PAGAD') || est.includes('CONFIRMAD')) return this.tipoEntrega === 'delivery' ? 'Motorizado asignado' : 'Listo para recojo';
        return 'Pedido confirmado';
    }
  });

  direccionCompleta = computed(() => {
    const d = this.pedido()?.direccionEnvio || '';
    const dist = this.pedido()?.distrito || '';
    if (!dist || d.toLowerCase().includes(dist.toLowerCase())) return d || dist;
    return `${d} (${dist})`;
  });

  costoEnvioTexto = computed(() => {
    const c = this.costoEnvio();
    if (Number.isNaN(c)) return 'Fuera de cobertura';
    if (c === 0) return 'Gratis';
    return `S/ ${c.toFixed(2)}`;
  });

  notaEntrega = computed(() => {
    const est = (this.pedido()?.estado || '').toUpperCase();
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 'Pedido entregado.';
    if (est.includes('EN_CAMINO')) return 'Repartidor en ruta hacia tu domicilio.';
    if (est.includes('LISTO')) return 'Listo para recojo en tienda.';
    return 'Preparando tu pedido.';
  });

  etaHora = computed(() => {
    const m = this.etaMin();
    const d = new Date();
    d.setMinutes(d.getMinutes() + m);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
  });

  /** Paso efectivo (0-4) usado tanto por el badge como por el timeline.
   *  Nunca queda por debajo del estado real del backend (piso). */
  pasoEfectivo = computed(() => {
    const est = (this.pedido()?.estado || '').toUpperCase();
    let backendPaso = 0;
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) backendPaso = 4;
    else if (est.includes('EN_CAMINO')) backendPaso = 3;
    else if (est.includes('LISTO')) backendPaso = 2;
    else if (est.includes('PAGAD') || est.includes('CONFIRMAD')) backendPaso = 1;

    const padre = (this.estadoActualMapa != null && this.estadoActualMapa > 0) ? this.estadoActualMapa : 0;
    let paso = Math.max(padre, backendPaso);
    // No mostrar "Entregado" (paso 4) a menos que el backend lo confirme.
    if (backendPaso < 4 && paso >= 4) paso = 3;
    return paso;
  });

  pasoActual = computed(() => this.pasoEfectivo());

  ngOnInit(): void {
    this.iniciarPolling();
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      if (this.pedido() && !this.map) {
        this.crearMapa();
      }
      if (this.map) {
        this.map.resize();
      }
    }, 50);
  }

  ngOnDestroy(): void {
    if (this.pollingSub) this.pollingSub.unsubscribe();
    if (this.map) this.map.remove();
  }

  /** Inicializa el mapa MapLibre GL JS una sola vez. */
  private async crearMapa(): Promise<void> {
    const el = this.mapContainer?.nativeElement;
    if (!el || this.map || this.creandoMapa) return;
    const ped = this.pedido();
    if (!ped) return;
    this.creandoMapa = true;

    // Resolver coordenadas reales: override manual > backend > Geoapify.
    let latDestino: number;
    let lngDestino: number;
    if (this.coordsOverride) {
      latDestino = this.coordsOverride.lat;
      lngDestino = this.coordsOverride.lng;
      this.usandoFallback = false;
    } else {
      latDestino = Number(ped.latitudEntrega);
      lngDestino = Number(ped.longitudEntrega);
      this.usandoFallback = false;
      if (!latDestino || !lngDestino) {
        this.usandoFallback = true;
        try {
          const coords = await this.mapaService.resolverDestino(ped);
          latDestino = coords[0];
          lngDestino = coords[1];
        } catch {
          latDestino = BOTICA_ANITA.lat;
          lngDestino = BOTICA_ANITA.lng;
        }
      }
    }
    // Validar que las coords sean números válidos dentro de Perú; si no, usar la tienda.
    const coordsValidas =
      Number.isFinite(latDestino) && Number.isFinite(lngDestino) &&
      latDestino >= -20 && latDestino <= 0 &&
      lngDestino >= -82 && lngDestino <= -68;
    if (!coordsValidas) {
      latDestino = BOTICA_ANITA.lat;
      lngDestino = BOTICA_ANITA.lng;
      this.usandoFallback = true;
    }
    this.destLat = latDestino;
    this.destLng = lngDestino;

    // Guardar en el pedido para no re-resolver
    if (ped && !ped.latitudEntrega) {
      (ped as any).latitudEntrega = latDestino;
      (ped as any).longitudEntrega = lngDestino;
    }

    // Asegurar que el contenedor tenga dimensiones explícitas
    el.style.height = '420px';
    el.style.width = '100%';
    el.style.display = 'block';

    // Esperar a que el DOM se actualice antes de crear el mapa
    setTimeout(() => {
      if (this.map || !this.mapContainer?.nativeElement) { this.creandoMapa = false; return; }

      try {
        this.map = new maplibregl.Map({
          container: this.mapContainer.nativeElement,
          style: {
            version: 8,
            sources: {
              'osm-tiles': {
                type: 'raster',
                tiles: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}'],
                tileSize: 256,
                attribution: 'Tiles &copy; Esri',
              },
            },
            layers: [
              {
                id: 'osm-tiles',
                type: 'raster',
                source: 'osm-tiles',
                minzoom: 0,
                maxzoom: 19,
              },
            ],
          },
          center: [lngDestino, latDestino],
          zoom: 14,
        });

        this.map.on('error', (e: any) => {
          console.error('Error de MapLibre:', e);
        });

        this.map.on('load', () => {
          if (!this.map) return;
          try {
            this.dibujarElementos(latDestino, lngDestino);
            this.ajustarCamara(latDestino, lngDestino);
            this.map.resize();
            const z = this.map.getZoom();
            if (z < 8) {
              this.map.setCenter([BOTICA_ANITA.lng, BOTICA_ANITA.lat]);
              this.map.setZoom(15);
            }
          } catch (e) {
            console.error('Error en load del mapa:', e);
          }
        });

        // Forzar resize después de un tiempo
        setTimeout(() => {
          if (this.map) this.map.resize();
        }, 500);
      } catch (e) {
        console.error('Error al crear el mapa:', e);
      } finally {
        this.creandoMapa = false;
      }
    }, 100);
  }

  /** Re-centra el mapa para encuadrar tienda y destino. */
  recentrar(): void {
    if (!this.map) return;
    this.ajustarCamara(this.destLat, this.destLng);
  }

  /** Ajusta la cámara para encuadrar tienda y destino. */
  private ajustarCamara(latDestino: number, lngDestino: number): void {
    if (!this.map) return;
    // Validar coords
    const coordsValidas =
      Number.isFinite(latDestino) && Number.isFinite(lngDestino) &&
      latDestino >= -20 && latDestino <= 0 &&
      lngDestino >= -82 && lngDestino <= -68;
    if (!coordsValidas) {
      this.map.setCenter([BOTICA_ANITA.lng, BOTICA_ANITA.lat]);
      this.map.setZoom(15);
      return;
    }
    const mismaUbicacion =
      Math.abs(latDestino - BOTICA_ANITA.lat) < 0.0001 &&
      Math.abs(lngDestino - BOTICA_ANITA.lng) < 0.0001;
    if (mismaUbicacion) {
      this.map.setCenter([BOTICA_ANITA.lng, BOTICA_ANITA.lat]);
      this.map.setZoom(15);
      return;
    }
    // Construir bounds con SW/NE correctamente ordenados (evita zoom a continentes).
    const swLng = Math.min(BOTICA_ANITA.lng, lngDestino);
    const swLat = Math.min(BOTICA_ANITA.lat, latDestino);
    const neLng = Math.max(BOTICA_ANITA.lng, lngDestino);
    const neLat = Math.max(BOTICA_ANITA.lat, latDestino);
    const bounds = new maplibregl.LngLatBounds([swLng, swLat], [neLng, neLat]);
    this.map.fitBounds(bounds, { padding: 90, duration: 0, maxZoom: 16 });
  }

  /** Dibuja todos los elementos del mapa (tienda, destino, ruta, repartidor). */
  private dibujarElementos(latDestino: number, lngDestino: number): void {
    if (!this.map) return;

    // Marcador de la tienda (pin personalizado con icono)
    if (!this.markerTienda) {
      const elTienda = document.createElement('div');
      elTienda.className = 'marker-tienda';
      elTienda.style.width = '40px';
      elTienda.style.height = '40px';
      elTienda.style.backgroundImage = "url('/icons/farmacia-icon.png')";
      elTienda.style.backgroundSize = 'contain';
      elTienda.style.backgroundRepeat = 'no-repeat';
      elTienda.style.backgroundPosition = 'center';
      elTienda.style.filter = 'drop-shadow(0 2px 4px rgba(0,0,0,0.35))';
      this.markerTienda = new maplibregl.Marker({ element: elTienda, anchor: 'bottom' })
        .setLngLat([BOTICA_ANITA.lng, BOTICA_ANITA.lat])
        .setPopup(
          new maplibregl.Popup({ offset: 25 }).setHTML(
            `<strong>Botica Anita</strong><br>${BOTICA_ANITA.direccion}<br><em>Preparando tu pedido</em>`
          )
        )
        .addTo(this.map);
    } else {
      this.markerTienda.setLngLat([BOTICA_ANITA.lng, BOTICA_ANITA.lat]);
    }

    // Marcador de destino (casa del cliente)
    if (!this.markerDestino) {
      const elDestino = document.createElement('div');
      elDestino.className = 'marker-destino';
      elDestino.style.width = '40px';
      elDestino.style.height = '40px';
      elDestino.style.backgroundImage = "url('/icons/casa-icon.png')";
      elDestino.style.backgroundSize = 'contain';
      elDestino.style.backgroundRepeat = 'no-repeat';
      elDestino.style.backgroundPosition = 'center';
      elDestino.style.filter = 'drop-shadow(0 2px 4px rgba(0,0,0,0.35))';
      this.markerDestino = new maplibregl.Marker({ element: elDestino, anchor: 'bottom', draggable: true })
        .setLngLat([lngDestino, latDestino])
        .setPopup(
          new maplibregl.Popup({ offset: 25 }).setHTML(
            `<strong>Tu domicilio</strong><br>${this.direccionCompleta()}<br><em>Arrastra para ajustar</em>`
          )
        )
        .addTo(this.map);
      this.markerDestino.on('dragend', () => {
        if (!this.markerDestino || !this.map) return;
        const ll = this.markerDestino.getLngLat();
        this.destLat = ll.lat;
        this.destLng = ll.lng;
        this.coordsOverride = { lat: ll.lat, lng: ll.lng };
        // Guardar en el pedido local
        const ped = this.pedido();
        if (ped) {
          (ped as any).latitudEntrega = ll.lat;
          (ped as any).longitudEntrega = ll.lng;
        }
        // Persistir en el backend para que el email "en camino" use la ubicación corregida.
        if (this.idPedido) {
          this.pedidoService.actualizarUbicacion(this.idPedido, ll.lat, ll.lng).subscribe({
            next: () => { /* ok */ },
            error: (e) => { console.error('No se pudo guardar la ubicación corregida:', e); },
          });
        }
        // Recalcular ruta y cámara
        this.mapaService.obtenerRutaPorCalles(BOTICA_ANITA.lng, BOTICA_ANITA.lat, ll.lng, ll.lat).then((ruta) => {
          if (!this.map) return;
          const coordsRuta = ruta.coords.map((c) => [c[1], c[0]]);
          const srcRuta = this.map.getSource(this.lineaRutaId) as maplibregl.GeoJSONSource | undefined;
          if (srcRuta) srcRuta.setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coordsRuta } });
          const srcRec = this.map.getSource(this.lineaRecorridaId) as maplibregl.GeoJSONSource | undefined;
          if (srcRec) srcRec.setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: [] } });
          this.rutaSimulada = ruta.coords;
          this.pasoSim = 0;
          this.distanciaKm.set(ruta.distanciaM / 1000);
          this.ajustarCamara(ll.lat, ll.lng);
          this.sincronizarVisibilidadRuta();
          this.avanzarSimulacion();
          this.actualizarPosicionRepartidor();
      });
    });
    } else {
      this.markerDestino.setLngLat([lngDestino, latDestino]);
      this.markerDestino.setPopup(
        new maplibregl.Popup({ offset: 25 }).setHTML(
          `<strong>Tu domicilio</strong><br>${this.direccionCompleta()}`
        )
      );
    }

    // Ruta por calles (OSRM)
    this.mapaService.obtenerRutaPorCalles(BOTICA_ANITA.lng, BOTICA_ANITA.lat, lngDestino, latDestino).then((ruta) => {
      if (!this.map) return;

      this.rutaSimulada = ruta.coords;
      this.pasoSim = 0;
      this.distanciaKm.set(ruta.distanciaM / 1000);
      this.calcularCostoEnvio();
      this.actualizarEta(ruta.distanciaM);

      const coordsRuta = ruta.coords.map((c) => [c[1], c[0]]);

      // Línea de ruta completa (gris)
      const srcRuta = this.map.getSource(this.lineaRutaId) as maplibregl.GeoJSONSource | undefined;
      if (srcRuta) {
        srcRuta.setData({
          type: 'Feature',
          properties: {},
          geometry: { type: 'LineString', coordinates: coordsRuta },
        });
      } else {
        this.map.addSource(this.lineaRutaId, {
          type: 'geojson',
          data: {
            type: 'Feature',
            properties: {},
            geometry: { type: 'LineString', coordinates: coordsRuta },
          },
        });
        this.map.addLayer({
          id: this.lineaRutaId,
          type: 'line',
          source: this.lineaRutaId,
          paint: {
            'line-color': '#94a3b8',
            'line-width': 6,
            'line-opacity': this.mostrarRuta() ? 0.8 : 0,
          },
          layout: { 'line-cap': 'round', 'line-join': 'round' },
        });
      }

      // Línea del recorrido (naranja, inicialmente vacía)
      const srcRec = this.map.getSource(this.lineaRecorridaId) as maplibregl.GeoJSONSource | undefined;
      if (srcRec) {
        srcRec.setData({
          type: 'Feature',
          properties: {},
          geometry: { type: 'LineString', coordinates: [] },
        });
      } else {
        this.map.addSource(this.lineaRecorridaId, {
          type: 'geojson',
          data: {
            type: 'Feature',
            properties: {},
            geometry: { type: 'LineString', coordinates: [] },
          },
        });
        this.map.addLayer({
          id: this.lineaRecorridaId,
          type: 'line',
          source: this.lineaRecorridaId,
          paint: {
            'line-color': '#e85d04',
            'line-width': 7,
            'line-opacity': this.mostrarRuta() ? 1 : 0,
          },
          layout: { 'line-cap': 'round', 'line-join': 'round' },
        });
      }

      // Capa de borde blanco (casing) para mayor contraste de la ruta.
      if (this.map && !this.map.getLayer('ruta-casing')) {
        this.map.addLayer({
          id: 'ruta-casing',
          type: 'line',
          source: this.lineaRutaId,
          paint: {
            'line-color': '#ffffff',
            'line-width': 10,
            'line-opacity': this.mostrarRuta() ? 0.9 : 0,
          },
          layout: { 'line-cap': 'round', 'line-join': 'round' },
        }, this.lineaRutaId); // insert below the gray route layer
      }

      this.avanzarSimulacion();
    });
  }

  /** Actualiza el destino cuando el backend ya geolocalizó la dirección. */
  private actualizarDestinoSiCambio(): void {
    if (!this.map || !this.pedido()) return;
    const ped = this.pedido();
    if (!ped) return;
    const lat = Number(ped.latitudEntrega) || 0;
    const lng = Number(ped.longitudEntrega) || 0;
    if (!lat || !lng) return;

    // Solo re-fetch si las coords cambiaron (evita parpadeo en cada poll).
    if (this.ultimasCoordsDestino &&
        Math.abs(this.ultimasCoordsDestino.lat - lat) < 0.00001 &&
        Math.abs(this.ultimasCoordsDestino.lng - lng) < 0.00001) {
      return;
    }
    this.ultimasCoordsDestino = { lat, lng };

    if (this.markerDestino) {
      this.markerDestino.setLngLat([lng, lat]);
      // Mantener la dirección ingresada por el usuario (no sobrescribir con geocodificación inversa).
      this.markerDestino.setPopup(
        new maplibregl.Popup({ offset: 25 }).setHTML(`<strong>Tu domicilio</strong><br>${this.direccionCompleta()}`)
      );
    }

    this.mapaService.obtenerRutaPorCalles(BOTICA_ANITA.lng, BOTICA_ANITA.lat, lng, lat).then((ruta) => {
      if (!this.map) return;
      const source = this.map.getSource(this.lineaRutaId) as maplibregl.GeoJSONSource;
      if (source) {
        source.setData({
          type: 'Feature',
          properties: {},
          geometry: {
            type: 'LineString',
            coordinates: ruta.coords.map((c) => [c[1], c[0]]),
          },
        });
      }
      this.rutaSimulada = ruta.coords;
      this.distanciaKm.set(ruta.distanciaM / 1000);
      this.calcularCostoEnvio();
      this.actualizarEta(ruta.distanciaM);
      this.ajustarCamara(lat, lng);
    });
  }

  /** Polling del backend cada 8s. */
  private iniciarPolling(): void {
    this.pollingSub = timer(0, 8000)
      .pipe(switchMap(() => this.trackingService.getTrackingInfo(this.idPedido)))
      .subscribe({
        next: (data) => {
          // Preservar corrección manual del usuario (drag) sobre el backend.
          if (this.coordsOverride && data) {
            (data as any).latitudEntrega = this.coordsOverride.lat;
            (data as any).longitudEntrega = this.coordsOverride.lng;
          }
          this.pedido.set(data);
          const est = (data.estado || '').toUpperCase();
          this.sincronizarVisibilidadRuta();

          if (!this.map) {
            this.crearMapa();
          } else {
            this.actualizarDestinoSiCambio();
          }

          if (this.tieneCoordsReales()) {
            this.mostrandoSimulacion.set(false);
            this.actualizarPosicionRepartidor();
          } else {
            this.mostrandoSimulacion.set(true);
            this.avanzarSimulacion();
          }

          const estFinal =
            est.includes('ENTREGAD') ||
            est.includes('COMPLETAD') ||
            est.includes('CANCELAD') ||
            est.includes('RECHAZAD');
          if (estFinal) {
            this.progreso.set(est.includes('RECHAZAD') || est.includes('CANCELAD') ? 0 : 100);
            this.etaMin.set(0);
            if (this.pollingSub) this.pollingSub.unsubscribe();
          }
        },
        error: (err) => {
          console.error('Error al obtener la ubicación del pedido:', err);
        },
      });
  }

  private tieneCoordsReales(): boolean {
    const p = this.pedido();
    return !!p && !!p.latitudRepartidor && !!p.longitudRepartidor;
  }

  /** Tope de progreso (%) según el estado real. */
  private topePorEstado(): number {
    const est = (this.pedido()?.estado || '').toUpperCase();
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 100;
    if (est.includes('EN_CAMINO')) return 95;
    if (est.includes('RECHAZAD') || est.includes('CANCELAD')) return 0;
    return 0;
  }

  /** Avanza el marcador del repartidor según el tiempo real desde el pedido. */
  private avanzarSimulacion(): void {
    if (!this.map || this.rutaSimulada.length === 0) return;
    const tope = this.topePorEstado();
    const topePaso = Math.floor((tope / 100) * (this.rutaSimulada.length - 1));
    let objetivo = topePaso;
    if (this.fechaPedido && this.rutaSimulada.length > 0) {
      const elapsedMin = (Date.now() - msDesdeFecha(this.fechaPedido)) / 60000;
      const inicioViaje = MIN_CONFIRMADO + MIN_DESPACHO + MIN_MOTORIZADO;
      const tripMin = this.tripMinDesdeDistancia();
      const frac = Math.min(Math.max((elapsedMin - inicioViaje) / tripMin, 0), 1);
      objetivo = Math.min(Math.floor(frac * (this.rutaSimulada.length - 1)), topePaso);
    }
    this.pasoSim = Math.max(this.pasoSim, Math.min(objetivo, topePaso));
    const pos = this.rutaSimulada[this.pasoSim];
    this.pintarRepartidor(pos[0], pos[1]);
    this.progreso.set(Math.round((this.pasoSim / Math.max(1, this.rutaSimulada.length - 1)) * 100));
    const restante = this.mapaService.distanciaM(
      pos[0],
      pos[1],
      this.rutaSimulada[this.rutaSimulada.length - 1][0],
      this.rutaSimulada[this.rutaSimulada.length - 1][1]
    );
    this.actualizarEta(restante);
  }

  private tripMinDesdeDistancia(): number {
    const d = this.distanciaKm() * 1000;
    return Math.max(1, Math.round((d / 1000 / 25) * 60));
  }

  private actualizarPosicionRepartidor(): void {
    const ped = this.pedido();
    if (!ped || !this.map) return;
    if (this.tieneCoordsReales()) {
      const lat = Number(ped.latitudRepartidor);
      const lng = Number(ped.longitudRepartidor);
      this.pintarRepartidor(lat, lng);
      this.progreso.set(100);
      this.etaMin.set(0);
      return;
    }
    if (this.rutaSimulada.length > 0) {
      const pos = this.rutaSimulada[this.pasoSim];
      this.pintarRepartidor(pos[0], pos[1]);
    }
  }

  private pintarRepartidor(lat: number, lng: number): void {
    if (!this.mostrarMoto()) {
      if (this.markerRepartidor) {
        this.markerRepartidor.remove();
        this.markerRepartidor = undefined;
      }
      return;
    }
    const mapInstance = this.map;
    if (!mapInstance) return;

    if (this.markerRepartidor) {
      this.markerRepartidor.setLngLat([lng, lat]);
      this.aplicarRotacionMoto();
    } else {
      const el = document.createElement('div');
      el.style.width = '34px';
      el.style.height = '34px';
      el.style.backgroundImage = "url('/icons/delivery-icon.png')";
      el.style.backgroundSize = 'contain';
      el.style.backgroundRepeat = 'no-repeat';
      el.style.backgroundPosition = 'center';
      el.style.lineHeight = '1';
      el.style.filter = 'drop-shadow(0 2px 4px rgba(0,0,0,0.3))';
      el.style.transition = 'opacity 0.4s ease, transform 0.3s ease';
      el.style.opacity = '0';
      this.markerRepartidor = new maplibregl.Marker({ element: el, anchor: 'center' })
        .setLngLat([lng, lat])
        .setPopup(new maplibregl.Popup({ offset: 25 }).setHTML('<strong>Repartidor en camino</strong>'))
        .addTo(mapInstance);
      setTimeout(() => { el.style.opacity = '1'; }, 60);
      this.aplicarRotacionMoto();
    }

    // Línea del recorrido crece conforme avanza el repartidor.
    const recorridos = this.rutaSimulada.slice(0, this.pasoSim + 1);
    if (recorridos.length > 1) {
      const source = mapInstance.getSource(this.lineaRecorridaId) as maplibregl.GeoJSONSource | undefined;
      if (source) {
        source.setData({
          type: 'Feature',
          properties: {},
          geometry: {
            type: 'LineString',
            coordinates: recorridos.map((c) => [c[1], c[0]]),
          },
        });
      }
    }
  }

  /** Rota el icono de la moto para que mire en la dirección del viaje. */
  private aplicarRotacionMoto(): void {
    if (!this.markerRepartidor || this.rutaSimulada.length < 2) return;
    const idx = Math.min(this.pasoSim, this.rutaSimulada.length - 2);
    const a = this.rutaSimulada[idx];
    const b = this.rutaSimulada[idx + 1];
    if (!a || !b) return;
    const lat1 = a[0], lng1 = a[1];
    const lat2 = b[0], lng2 = b[1];
    const toRad = (d: number) => (d * Math.PI) / 180;
    const y = Math.sin(toRad(lng2 - lng1)) * Math.cos(toRad(lat2));
    const x =
      Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) -
      Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(toRad(lng2 - lng1));
    const brng = (Math.atan2(y, x) * 180) / Math.PI;
    this.bearingActual = (brng + 360) % 360;
    const el = this.markerRepartidor.getElement();
    if (el) {
      el.style.transform = `rotate(${this.bearingActual}deg)`;
      el.style.transformOrigin = 'center center';
      el.style.transition = 'transform 0.4s ease';
    }
  }

  /** Actualiza la visibilidad de las líneas de ruta según mostrarRuta. */
  private sincronizarVisibilidadRuta(): void {
    if (!this.map) return;
    const visible = this.mostrarRuta();
    const opRuta = visible ? 0.8 : 0;
    const opCasing = visible ? 0.9 : 0;
    const opRec = visible ? 1 : 0;
    try {
      if (this.map.getLayer(this.lineaRutaId)) this.map.setPaintProperty(this.lineaRutaId, 'line-opacity', opRuta);
      if (this.map.getLayer('ruta-casing')) this.map.setPaintProperty('ruta-casing', 'line-opacity', opCasing);
      if (this.map.getLayer(this.lineaRecorridaId)) this.map.setPaintProperty(this.lineaRecorridaId, 'line-opacity', opRec);
    } catch (e) {
      // ignore
    }
  }

  private actualizarEta(distM: number): void {
    this.etaMin.set(Math.max(1, Math.round((distM / 1000 / 25) * 60)));
  }

  private calcularCostoEnvio(): void {
    const distKm = this.distanciaKm();
    const esUrgente = this.tipoEntrega === 'delivery';
    this.costoEnvio.set(this.mapaService.costoEnvio(distKm, esUrgente));
  }
}
