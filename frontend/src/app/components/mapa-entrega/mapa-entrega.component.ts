import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  ViewChild,
  output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BOTICA_ANITA, RADIO_COBERTURA_KM } from '../../services/mapa-ruta.service';
import {
  DireccionSugerida,
  DireccionUbicacion,
  GoogleMapsService,
} from '../../services/google-maps.service';
import { GoogleMapsLoaderService } from '../../services/google-maps-loader.service';

/** Datos que el checkout recibe cuando el cliente elige su punto de entrega. */
export interface UbicacionEntrega {
  direccion: string;
  distrito: string;
  lat: number;
  lng: number;
  distanciaKm: number;
  duracionMin: number;
  dentroCobertura: boolean;
}

@Component({
  selector: 'app-mapa-entrega',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './mapa-entrega.component.html',
  styleUrl: './mapa-entrega.component.css',
})
export class MapaEntregaComponent implements AfterViewInit, OnDestroy {
  /** Emite cada vez que se confirma un punto de entrega válido. */
  readonly ubicacionSeleccionada = output<UbicacionEntrega>();
  /** Emite cuando el usuario edita el texto y la selección anterior deja de ser válida. */
  readonly direccionEditada = output<void>();

  @ViewChild('mapaHost') private mapaHost?: ElementRef<HTMLDivElement>;

  direccion = '';
  sugerencias: DireccionSugerida[] = [];
  mostrarSugerencias = false;
  buscando = false;
  cargandoMapa = true;
  calculandoRuta = false;
  errorMapa = '';

  distanciaKm = 0;
  duracionMin = 0;
  distanciaTexto = '—';
  duracionTexto = '—';
  dentroCobertura = true;
  direccionConfirmada = '';

  private mapa?: google.maps.Map;
  private markerTienda?: google.maps.marker.AdvancedMarkerElement;
  private markerCliente?: google.maps.marker.AdvancedMarkerElement;
  private lineaRuta?: google.maps.Polyline;
  private lineaRutaBorde?: google.maps.Polyline;
  private debounceBusqueda?: ReturnType<typeof setTimeout>;
  private sessionToken?: google.maps.places.AutocompleteSessionToken | null;
  private inicializado = false;
  private destruido = false;
  private solicitudActual = 0;
  private ultimoPunto: { lat: number; lng: number } | null = null;
  private ultimoTexto = '';
  private ultimoDistrito = 'Independencia';

  /**
   * Constructores obtenidos de `importLibrary(...)`. Nunca usamos el namespace
   * global `google` en tiempo de ejecución para evitar `ReferenceError`.
   */
  private CtorPolyline?: typeof google.maps.Polyline;
  private CtorLatLngBounds?: typeof google.maps.LatLngBounds;

  constructor(
    private readonly zone: NgZone,
    private readonly googleMaps: GoogleMapsService,
    private readonly mapsLoader: GoogleMapsLoaderService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngAfterViewInit(): void {
    // Inicializamos fuera del ciclo de detección de cambios para evitar el
    // NG0100 (ExpressionChangedAfterItHasBeenCheckedError) cuando cambiamos
    // `cargandoMapa` o `errorMapa`.
    setTimeout(() => void this.inicializar(), 0);
  }

  ngOnDestroy(): void {
    this.destruido = true;
    if (this.debounceBusqueda) clearTimeout(this.debounceBusqueda);
    this.lineaRuta?.setMap(null);
    this.lineaRutaBorde?.setMap(null);
    this.markerCliente?.remove();
    this.markerTienda?.remove();
    this.mapa = undefined;
  }

  // ── Búsqueda ────────────────────────────────────────────────────────────

  onInput(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.direccion = valor;
    this.limpiarConfirmacion();
    this.direccionEditada.emit();

    if (this.debounceBusqueda) clearTimeout(this.debounceBusqueda);
    if (valor.trim().length < 3) {
      this.sugerencias = [];
      this.mostrarSugerencias = false;
      return;
    }

    this.debounceBusqueda = setTimeout(() => void this.buscar(valor), 280);
  }

  onFocus(): void {
    if (this.sugerencias.length) this.mostrarSugerencias = true;
  }

  cerrarSugerencias(): void {
    // Pequeño retardo para permitir el click/tap sobre la sugerencia.
    setTimeout(() => this.zone.run(() => (this.mostrarSugerencias = false)), 140);
  }

  private async buscar(consulta: string): Promise<void> {
    if (!this.mapsLoader.disponible) return;

    this.zone.run(() => {
      this.buscando = true;
      this.errorMapa = '';
    });
    try {
      // Garantiza que la librería `places` exista antes de crear el token.
      await this.mapsLoader.importLibrary('places');
      if (this.destruido) return;
      this.sessionToken ??= this.googleMaps.crearSessionToken();
      const resultados = await this.googleMaps.buscarSugerencias(consulta, this.sessionToken);
      this.zone.run(() => {
        this.sugerencias = resultados;
        this.mostrarSugerencias = resultados.length > 0;
      });
    } catch (error) {
      console.warn('No se pudieron obtener sugerencias de Google Places.', error);
      this.zone.run(() => {
        this.sugerencias = [];
        this.mostrarSugerencias = false;
      });
    } finally {
      this.zone.run(() => (this.buscando = false));
    }
  }

  async seleccionarSugerencia(sug: DireccionSugerida): Promise<void> {
    if (!sug.prediction) return;

    this.mostrarSugerencias = false;
    this.buscando = true;
    try {
      const detalle = await this.googleMaps.resolverSugerencia(sug.prediction);
      this.sessionToken = null;
      if (!detalle) {
        this.zone.run(() => {
          this.errorMapa = 'No pudimos ubicar esa dirección. Intenta con otra referencia.';
        });
        return;
      }
      await this.aplicarUbicacion(detalle);
    } catch (error) {
      console.warn('No se pudo resolver la dirección seleccionada.', error);
      this.zone.run(() => {
        this.errorMapa = 'No pudimos obtener los datos de esa dirección. Intenta con otra.';
      });
    } finally {
      this.zone.run(() => (this.buscando = false));
    }
  }

  // ── Mapa ────────────────────────────────────────────────────────────────

  private async inicializar(): Promise<void> {
    if (this.inicializado || this.destruido) return;
    this.inicializado = true;

    if (!this.mapsLoader.disponible) {
      this.actualizarEstado(() => {
        this.errorMapa = 'Google Maps no está configurado en este entorno.';
        this.cargandoMapa = false;
      });
      return;
    }

    try {
      // 1) Esperar a que `window.google.maps.importLibrary` exista de verdad.
      await this.mapsLoader.cargar();
      if (this.destruido) return;

      // 2) Obtener las librerías y usar sus constructores (sin `google.maps.*`).
      const [mapsLib, markerLib, coreLib] = await Promise.all([
        this.mapsLoader.importLibrary('maps'),
        this.mapsLoader.importLibrary('marker'),
        this.mapsLoader.importLibrary('core'),
      ]);
      if (this.destruido) return;

      const host = this.mapaHost?.nativeElement;
      if (!host) throw new Error('Contenedor del mapa no disponible.');

      this.CtorPolyline = mapsLib.Polyline;
      this.CtorLatLngBounds = coreLib.LatLngBounds;

      this.mapa = new mapsLib.Map(host, {
        center: { lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng },
        zoom: 15,
        // Requerido para que AdvancedMarkerElement renderice los tiles vectoriales.
        mapId: 'DEMO_MAP_ID',
        disableDefaultUI: true,
        zoomControl: true,
        clickableIcons: false,
        gestureHandling: 'cooperative',
      });

      this.markerTienda = new markerLib.AdvancedMarkerElement({
        map: this.mapa,
        position: { lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng },
        title: BOTICA_ANITA.nombre,
        content: this.crearPinTienda(),
      });

      // El marcador del cliente arranca en la botica hasta que elija su dirección.
      this.markerCliente = new markerLib.AdvancedMarkerElement({
        map: this.mapa,
        position: { lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng },
        title: 'Punto de entrega',
        gmpDraggable: true,
        content: this.crearPinCliente(),
      });

      this.markerCliente.addEventListener('gmp-dragend', () => {
        const pos = this.markerCliente?.position;
        if (!pos) return;
        const { lat, lng } = this.aLatLng(pos);
        void this.actualizarPorArrastre(lat, lng);
      });

      this.actualizarEstado(() => (this.cargandoMapa = false));
    } catch (error) {
      console.error('No se pudo inicializar Google Maps.', error);
      this.actualizarEstado(() => {
        this.errorMapa = 'No se pudo cargar el mapa. Revisa tu conexión o la API key.';
        this.cargandoMapa = false;
      });
    }
  }

  /** Aplica cambios de estado dentro de Angular y refresca la vista. */
  private actualizarEstado(cambio: () => void): void {
    this.zone.run(() => {
      cambio();
      this.cdr.markForCheck();
    });
  }

  /** Normaliza la posición de un marcador a `{ lat, lng }`. */
  private aLatLng(
    pos: google.maps.LatLng | google.maps.LatLngLiteral
  ): { lat: number; lng: number } {
    if (typeof (pos as google.maps.LatLng).lat === 'function') {
      const ll = pos as google.maps.LatLng;
      return { lat: ll.lat(), lng: ll.lng() };
    }
    const literal = pos as google.maps.LatLngLiteral;
    return { lat: literal.lat, lng: literal.lng };
  }

  private async actualizarPorArrastre(lat: number, lng: number): Promise<void> {
    let texto = this.direccion;
    let distrito = '';

    try {
      const detalle = await this.googleMaps.geocodificarInverso(lat, lng);
      if (detalle) {
        texto = this.textoDireccion(detalle);
        distrito = detalle.distrito;
      } else if (!texto) {
        texto = `Ubicación (${lat.toFixed(5)}, ${lng.toFixed(5)})`;
      }
    } catch (error) {
      console.warn('Geocodificación inversa no disponible.', error);
      if (!texto) texto = `Ubicación (${lat.toFixed(5)}, ${lng.toFixed(5)})`;
    }

    this.zone.run(() => {
      this.direccion = texto;
    });
    await this.procesarPunto(lat, lng, texto, distrito);
  }

  private async aplicarUbicacion(detalle: DireccionUbicacion): Promise<void> {
    const texto = this.textoDireccion(detalle);
    this.zone.run(() => {
      this.direccion = texto;
    });
    await this.procesarPunto(detalle.lat, detalle.lng, texto, detalle.distrito);
  }

  /** Mueve el marcador, calcula/dibuja la ruta y actualiza el resumen. */
  private async procesarPunto(
    lat: number,
    lng: number,
    texto: string,
    distrito: string
  ): Promise<void> {
    const solicitud = ++this.solicitudActual;
    this.zone.run(() => {
      this.calculandoRuta = true;
      this.errorMapa = '';
      this.direccionConfirmada = texto;
      this.ultimoPunto = { lat, lng };
      this.ultimoTexto = texto;
      this.ultimoDistrito = distrito || 'Independencia';
      // Emitimos de inmediato para que el checkout tenga dirección y coords
      // aunque el cálculo de la ruta aún esté en curso.
      this.emitirUbicacion();
    });

    this.moverMarcador(lat, lng);
    this.mapa?.panTo({ lat, lng });

    let distanciaM: number;
    let duracionSeg: number;
    let coords: Array<[number, number]> = [
      [BOTICA_ANITA.lat, BOTICA_ANITA.lng],
      [lat, lng],
    ];

    try {
      const ruta = await this.googleMaps.calcularRuta(
        { lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng },
        { lat, lng }
      );
      if (ruta) {
        coords = ruta.coords;
        distanciaM = ruta.distanciaM;
        duracionSeg = ruta.duracionSeg;
      } else {
        distanciaM = this.distanciaM(BOTICA_ANITA.lat, BOTICA_ANITA.lng, lat, lng);
        duracionSeg = (distanciaM / 1000 / 25) * 3600;
      }
    } catch {
      distanciaM = this.distanciaM(BOTICA_ANITA.lat, BOTICA_ANITA.lng, lat, lng);
      duracionSeg = (distanciaM / 1000 / 25) * 3600;
    }

    if (solicitud !== this.solicitudActual) return;

    this.dibujarRuta(coords);
    this.encuadrarRuta(coords);
    const km = distanciaM / 1000;
    const min = Math.max(1, Math.round(duracionSeg / 60));
    // La cobertura se valida en línea recta, igual que en el checkout/backend.
    const distLinealM = this.distanciaM(BOTICA_ANITA.lat, BOTICA_ANITA.lng, lat, lng);

    this.zone.run(() => {
      this.distanciaKm = km;
      this.duracionMin = min;
      this.distanciaTexto = this.googleMaps.formatearDistancia(distanciaM);
      this.duracionTexto = this.googleMaps.formatearDuracion(duracionSeg);
      this.dentroCobertura = distLinealM <= RADIO_COBERTURA_KM * 1000;
      this.calculandoRuta = false;
      this.emitirUbicacion();
    });
  }

  private emitirUbicacion(): void {
    if (!this.ultimoPunto) return;
    this.ubicacionSeleccionada.emit({
      direccion: this.ultimoTexto,
      distrito: this.ultimoDistrito,
      lat: this.ultimoPunto.lat,
      lng: this.ultimoPunto.lng,
      distanciaKm: this.distanciaKm,
      duracionMin: this.duracionMin,
      dentroCobertura: this.dentroCobertura,
    });
  }

  private moverMarcador(lat: number, lng: number): void {
    if (this.markerCliente) this.markerCliente.position = { lat, lng };
  }

  /** Descarta la confirmación anterior mientras el usuario vuelve a escribir. */
  private limpiarConfirmacion(): void {
    this.direccionConfirmada = '';
    this.distanciaTexto = '—';
    this.duracionTexto = '—';
    this.distanciaKm = 0;
    this.duracionMin = 0;
    this.dentroCobertura = true;
    this.ultimoPunto = null;
    this.lineaRuta?.setPath([]);
    this.lineaRutaBorde?.setPath([]);
  }

  private dibujarRuta(coords: Array<[number, number]>): void {
    const Polyline = this.CtorPolyline;
    if (!this.mapa || !Polyline) return;

    const path = coords.map(([lat, lng]) => ({ lat, lng }));

    if (!this.lineaRutaBorde) {
      this.lineaRutaBorde = new Polyline({
        map: this.mapa,
        strokeColor: '#ffffff',
        strokeOpacity: 0.9,
        strokeWeight: 9,
        geodesic: true,
        zIndex: 10,
      });
    }
    if (!this.lineaRuta) {
      this.lineaRuta = new Polyline({
        map: this.mapa,
        strokeColor: '#e85d04',
        strokeOpacity: 0.95,
        strokeWeight: 5,
        geodesic: true,
        zIndex: 11,
      });
    }

    this.lineaRutaBorde.setPath(path);
    this.lineaRuta.setPath(path);
  }

  private encuadrarRuta(coords: Array<[number, number]>): void {
    const LatLngBounds = this.CtorLatLngBounds;
    if (!this.mapa || !LatLngBounds) return;
    const bounds = new LatLngBounds();
    bounds.extend({ lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng });
    coords.forEach(([lat, lng]) => bounds.extend({ lat, lng }));
    this.mapa.fitBounds(bounds, { top: 56, right: 48, bottom: 56, left: 48 });
  }

  // ── Utilidades ──────────────────────────────────────────────────────────

  formatearDistanciaSugerencia(metros: number | null): string {
    return this.googleMaps.formatearDistancia(metros ?? NaN);
  }

  private textoDireccion(dir: DireccionUbicacion): string {
    const via = [dir.calle, dir.numero].filter(Boolean).join(' ').trim();
    const partes = [via || dir.direccion, dir.distrito].filter(Boolean);
    return partes.join(', ');
  }

  private distanciaM(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const r = 6371000;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLng = ((lng2 - lng1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLng / 2) ** 2;
    return 2 * r * Math.asin(Math.sqrt(a));
  }

  private crearPinTienda(): HTMLElement {
    const pin = document.createElement('div');
    pin.style.cssText =
      'display:flex;flex-direction:column;align-items:center;transform:translateY(-4px);';
    pin.innerHTML = `
      <div style="background:#1a2e4a;color:#fff;border:2px solid #fff;border-radius:10px;
        padding:6px 11px;font:700 11px/1 'Outfit',system-ui,sans-serif;letter-spacing:.2px;
        box-shadow:0 8px 20px rgba(15,23,42,.35);white-space:nowrap;">Botica Anita</div>
      <div style="width:2px;height:7px;background:#1a2e4a;"></div>
      <div style="width:11px;height:11px;border-radius:50%;background:#1a2e4a;
        border:2px solid #fff;box-shadow:0 2px 6px rgba(15,23,42,.35);"></div>`;
    return pin;
  }

  private crearPinCliente(): HTMLElement {
    const pin = document.createElement('div');
    pin.style.cssText = 'transform:translateY(-3px);filter:drop-shadow(0 6px 10px rgba(232,93,4,.35));';
    pin.innerHTML = `
      <div style="width:24px;height:24px;background:#e85d04;border:3px solid #fff;
        border-radius:50% 50% 50% 0;transform:rotate(-45deg);"></div>`;
    return pin;
  }
}
