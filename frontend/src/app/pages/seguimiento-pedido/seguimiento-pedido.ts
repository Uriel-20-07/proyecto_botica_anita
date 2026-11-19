import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { interval, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { PedidoService } from '../../services/pedido.service';
import { TrackingService } from '../../services/tracking.service';
import { TrackingMapaComponent } from '../../components/tracking/tracking-mapa.component';
import {
  MapaRutaService,
  BOTICA_ANITA,
  MIN_CONFIRMADO,
  MIN_DESPACHO,
  MIN_MOTORIZADO,
  MIN_ENTREGA_EXTRA,
} from '../../services/mapa-ruta.service';
import { msDesdeFecha, fechaComoDate } from '../../services/fecha-util';

@Component({
  selector: 'app-seguimiento-pedido',
  standalone: true,
  imports: [CommonModule, RouterModule, TrackingMapaComponent],
  templateUrl: './seguimiento-pedido.html',
  styleUrls: ['./seguimiento-pedido.css']
})
export class SeguimientoPedidoComponent implements OnInit, OnDestroy {

  private destroy$ = new Subject<void>();

  pedido = signal<any>(null);

  numeroPedido = signal('');

  idPedidoRuta = 0;

  /** Minutos estimados de viaje tienda -> entrega (se calcula al cargar el pedido). */
  tripMin = 20;

  /** Evita recalcular la ruta real más de una vez para el mismo destino. */
  private rutaRefinadaKey: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private pedidoService: PedidoService,
    private trackingService: TrackingService,
    private mapaService: MapaRutaService
  ) { }

  estadoActual = signal(0);

  pasos: any[] = [];

  ngOnInit(): void {

    const idPedido = Number(
      this.route.snapshot.paramMap.get('id')
    );
    this.idPedidoRuta = idPedido;

    this.cargarPedido();

    // Refrescar el estado cada 15s para que el timeline no quede con datos viejos.
    interval(15000).pipe(takeUntil(this.destroy$)).subscribe(() => this.refrescarEstado());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private cargarPedido(): void {
    const idPedido = this.idPedidoRuta;

    // 1) Pintado inmediato con los datos ya cargados en memoria (al venir del
    //    historial, el pedido suele estar en la caché compartida del servicio).
    const cache = this.pedidoService.pedidosDesdeCache();
    if (cache && cache.length > 0) {
      this.renderPedido(cache, idPedido, false);
    }

    // 2) Confirmar/actualizar con el backend. La caché del servicio evita
    //    repetir la consulta si los datos siguen vigentes.
    this.pedidoService.obtenerPedidos().subscribe({
      next: pedidos => this.renderPedido(pedidos, idPedido, true)
    });
  }

  /**
   * Vuelca el pedido en la vista y dibuja estado + timeline sin esperar a que
   * se inicialice el mapa ni a que se calcule la ruta por calles.
   */
  private renderPedido(pedidos: any[], idPedido: number, resolverRuta: boolean): void {
    const index = pedidos.findIndex((p: any) => p.idPedido === idPedido);
    if (index < 0) {
      if (!this.pedido()) this.pedido.set(null);
      return;
    }

    const pedido = pedidos[index];
    this.pedido.set(pedido);

    const numeroVisible = (pedidos.length - index).toString().padStart(6, '0');
    this.numeroPedido.set(numeroVisible);

    // Estimación instantánea (Haversine) para no bloquear el timeline.
    this.tripMin = this.estimarTripMin(pedido);
    this.calcularEstado(pedido.fecha);
    this.generarFechasTimeline(pedido.fecha, pedido.direccionEnvio || 'Bodega seleccionada', pedido.esUrgente);

    // La ruta real se calcula después, sin bloquear el render.
    if (resolverRuta) {
      void this.refinarRuta(pedido);
    }
  }

  /** Minutos de viaje aproximados usando distancia en línea recta. */
  private estimarTripMin(pedido: any): number {
    const lat = Number(pedido?.latitudEntrega) || 0;
    const lng = Number(pedido?.longitudEntrega) || 0;
    if (!lat || !lng) return this.tripMin || 20;
    return this.mapaService.tripMin(
      this.mapaService.distanciaM(BOTICA_ANITA.lat, BOTICA_ANITA.lng, lat, lng)
    );
  }

  /** Refina la duración con la ruta real por calles (una sola vez por destino). */
  private async refinarRuta(pedido: any): Promise<void> {
    if (pedido?.esUrgente === false) return; // recojo: no hay viaje

    const lat = Number(pedido?.latitudEntrega) || 0;
    const lng = Number(pedido?.longitudEntrega) || 0;
    const key = lat && lng ? `${lat.toFixed(5)},${lng.toFixed(5)}` : 'geocode';
    if (this.rutaRefinadaKey === key) return;
    this.rutaRefinadaKey = key;

    try {
      const [la, ln] = await this.mapaService.resolverDestino(pedido);
      const r = await this.mapaService.obtenerRutaPorCalles(BOTICA_ANITA.lng, BOTICA_ANITA.lat, ln, la);
      if (!r?.tripMin) return;
      this.tripMin = r.tripMin;

      const actual = this.pedido() ?? pedido;
      this.generarFechasTimeline(actual.fecha, actual.direccionEnvio || 'Bodega seleccionada', actual.esUrgente);
      this.calcularEstado(actual.fecha);
    } catch {
      // Se conserva la estimación Haversine.
    }
  }

  /** Relee el pedido para actualizar el timeline sin recalcular la ruta. */
  private refrescarEstado(): void {
    const idPedido = this.idPedidoRuta;
    // Se usa el endpoint de tracking porque es el que hace avanzar el estado
    // en el backend (el de pedidos solo devuelve el estado guardado).
    this.trackingService.getTrackingInfo(idPedido).subscribe({
      next: (info) => {
        if (!info) return;
        this.pedido.update(p => ({ ...(p || {}), estado: info.estado, direccionEnvio: info.direccionEnvio || p?.direccionEnvio, distrito: info.distrito || p?.distrito }));
        const fecha = info.fecha || this.pedido()?.fecha;
        this.calcularEstado(fecha);
        // Refrescar también el timeline para que las horas mostradas no queden viejas.
        const pedido = this.pedido();
        if (pedido) {
          this.generarFechasTimeline(fecha, pedido.direccionEnvio || 'Bodega seleccionada', pedido.esUrgente);
        }
      },
      error: () => {
        // Si falla el tracking, se reintenta con el listado de pedidos.
        this.pedidoService.obtenerPedidos(true).subscribe({
          next: pedidos => {
            const pedido = pedidos.find((p: any) => p.idPedido === idPedido);
            if (!pedido) return;
            this.pedido.set(pedido);
            this.calcularEstado(pedido.fecha);
            this.generarFechasTimeline(pedido.fecha, pedido.direccionEnvio || 'Bodega seleccionada', pedido.esUrgente);
          }
        });
      }
    });
  }

  generarFechasTimeline(fechaPedido: string, direccionBodega: string, esUrgente: boolean): void {

    const inicio = fechaComoDate(fechaPedido) ?? new Date();
    const at = (min: number): Date => {
      const d = new Date(inicio);
      d.setMinutes(d.getMinutes() + min);
      return d;
    };

    const pedR = this.pedido();
    const esperaR = pedR && (pedR.estado === 'EN_ESPERA' || pedR.estado === 'ESPERANDO' || pedR.estado === 'PENDIENTE_VERIFICACION');

    // Flujo de recojo en tienda: sin motorizado ni viaje.
    if (!esUrgente) {
      this.pasos = [
        {
          titulo: esperaR ? 'Pedido en espera' : 'Pedido confirmado',
          descripcion: pedR?.estado === 'PENDIENTE_VERIFICACION' ? 'Verificando tu pago Plin' : (esperaR ? 'Esperando validación de receta médica' : 'Pago recibido correctamente'),
          fecha: inicio
        },
        {
          titulo: 'En preparación',
          descripcion: 'Preparando tus productos',
          fecha: at(MIN_CONFIRMADO)
        },
        {
          titulo: 'Listo para recojo',
          descripcion: 'Recoge tu pedido en Botica Anita, Av. Los Jazmines 408, Independencia',
          fecha: at(MIN_CONFIRMADO + MIN_DESPACHO)
        },
        {
          titulo: 'Entregado',
          descripcion: 'Pedido entregado. ¡Gracias por tu compra!',
          fecha: at(MIN_CONFIRMADO + MIN_DESPACHO + 60)
        }
      ];
      return;
    }

    // Flujo delivery con tiempos por etapa + viaje según distancia.
    const despacho = at(MIN_CONFIRMADO);
    const motorizado = at(MIN_CONFIRMADO + MIN_DESPACHO);
    const camino = at(MIN_CONFIRMADO + MIN_DESPACHO + MIN_MOTORIZADO);
    const entregado = at(MIN_CONFIRMADO + MIN_DESPACHO + MIN_MOTORIZADO + this.tripMin + MIN_ENTREGA_EXTRA);

    const descCamino = esUrgente
      ? `Tu pedido está de camino a tu hogar: ${direccionBodega}`
      : `Tu pedido está de camino a la bodega: ${direccionBodega}`;

    const descEntregado = esUrgente
      ? `Pedido entregado exitosamente en tu hogar: ${direccionBodega}`
      : `Pedido entregado exitosamente. Listo para recoger en: ${direccionBodega}`;

    const ped = this.pedido();
    const esEspera = ped && (ped.estado === 'EN_ESPERA' || ped.estado === 'ESPERANDO' || ped.estado === 'PENDIENTE_VERIFICACION');

    this.pasos = [
      {
        titulo: esEspera ? 'Pedido en espera' : 'Pedido confirmado',
        descripcion: ped?.estado === 'PENDIENTE_VERIFICACION' ? 'Verificando tu pago Plin' : (esEspera ? 'Esperando validación de receta médica' : 'Pago recibido correctamente'),
        fecha: inicio
      },
      {
        titulo: 'En despacho',
        descripcion: 'Preparando productos',
        fecha: despacho
      },
      {
        titulo: 'Motorizado asignado',
        descripcion: 'Motorizado saliendo del almacén',
        fecha: motorizado
      },
      {
        titulo: 'En camino',
        descripcion: descCamino,
        fecha: camino
      },
      {
        titulo: 'Entregado',
        descripcion: descEntregado,
        fecha: entregado
      }
    ];
  }

  /** Fecha del pedido como Date (el backend la guarda en UTC). */
  fechaPedido(): Date | null {
    return fechaComoDate(this.pedido()?.fecha);
  }

  /** Tipo de entrega inferido del estado del pedido. */
  tipoEntrega(): 'delivery' | 'recojo' {
    const ped = this.pedido();
    return ped?.esUrgente === false ? 'recojo' : 'delivery';
  }

  /** El mapa de delivery solo existe para delivery y se oculta cuando ya se entregó/completó.
       Se oculta con clase CSS (no con *ngIf) para no romper el WebGL de Mapbox. */
  mapaVisible(): boolean {
    const ped = this.pedido();
    if (this.tipoEntrega() === 'recojo') return false;
    const est = (ped?.estado || '').toUpperCase();
    return !(est.includes('COMPLETAD') || est.includes('ENTREGAD'));
  }
  /** Tope de paso según el estado real: el timeline nunca corre más rápido que el pedido. */
  private topeEstadoPedido(): number {
    const ped = this.pedido();
    const urgente = ped?.esUrgente !== false;
    const est = (ped?.estado || '').toUpperCase();
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return urgente ? 4 : 3;
    if (est.includes('EN_CAMINO')) return 3;
    if (est.includes('LISTO')) return 2;
    if (est.includes('PAGAD') || est.includes('CONFIRMAD')) return urgente ? 2 : 1;
    return urgente ? 4 : 3;
  }

  /** Paso mínimo que impone el estado real del backend (no se puede retroceder). */
  private pasoMinimoBackend(): number {
    const est = (this.pedido()?.estado || '').toUpperCase();
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 4;
    if (est.includes('EN_CAMINO')) return 3;
    if (est.includes('LISTO')) return 2;
    if (est.includes('PAGAD') || est.includes('CONFIRMAD')) return 1;
    return 0;
  }

  calcularEstado(fechaPedido: string): void {
    const ped = this.pedido();
    if (ped && (ped.estado === 'EN_ESPERA' || ped.estado === 'ESPERANDO' || ped.estado === 'PENDIENTE_VERIFICACION')) {
      this.estadoActual.set(0);
      return;
    }

    const inicio = msDesdeFecha(fechaPedido);
    const ahora = Date.now();
    const minutos = (ahora - inicio) / (1000 * 60);

    let paso: number;
    // Tiempos por etapa + viaje según distancia (igual que el mapa).
    // Recojo: 1 + 5 fijos, listo a los 6 y cierre a los 66.
    const urgente = ped?.esUrgente !== false;
    const t0 = MIN_CONFIRMADO; // 1
    const t1 = t0 + MIN_DESPACHO; // 6
    if (!urgente) {
      const tRecojo = t1 + 60;
      if (minutos < t0) {
        paso = 0;
      } else if (minutos < t1) {
        paso = 1;
      } else if (minutos < tRecojo) {
        paso = 2;
      } else {
        paso = 3;
      }
      paso = Math.max(paso, this.pasoMinimoBackend());
      this.estadoActual.set(Math.min(paso, this.topeEstadoPedido()));
      return;
    }
    const t2 = t1 + MIN_MOTORIZADO; // 11
    const t3 = t2 + this.tripMin + MIN_ENTREGA_EXTRA;
    // Pedido confirmado
    if (minutos < t0) {
      paso = 0;
    }
    // En despacho
    else if (minutos < t1) {
      paso = 1;
    }
    // Motorizado asignado
    else if (minutos < t2) {
      paso = 2;
    }
    // En camino (dura lo que el viaje)
    else if (minutos < t3) {
      paso = 3;
    }
    // Entregado (1 min después de llegar)
    else {
      paso = 4;
    }
    paso = Math.max(paso, this.pasoMinimoBackend());
    this.estadoActual.set(Math.min(paso, this.topeEstadoPedido()));
  }

  estadoTexto(): string {
    const ped = this.pedido();
    if (ped && (ped.estado === 'EN_ESPERA' || ped.estado === 'ESPERANDO' || ped.estado === 'PENDIENTE_VERIFICACION')) {
      return ped.estado === 'PENDIENTE_VERIFICACION' ? 'verificando tu pago Plin' : 'esperando a que el pedido se confirme';
    }

    switch (this.estadoActual()) {
      case 0:
        return 'Pedido confirmado';
      case 1:
        return 'En despacho';
      case 2:
        return 'Motorizado asignado';
      case 3:
        return 'En camino';
      case 4:
        return 'Entregado';
      default:
        return 'Pedido confirmado';
    }
  }
}