import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PedidoService } from '../../services/pedido.service';
import { TrackingMapaComponent } from '../../components/tracking/tracking-mapa.component';
import { TrackingRutaService, TIENDA_LAT, TIENDA_LNG, MIN_CONFIRMADO, MIN_DESPACHO, MIN_MOTORIZADO, MIN_ENTREGA_EXTRA } from '../../services/tracking-ruta.service';

@Component({
  selector: 'app-seguimiento-pedido',
  standalone: true,
  imports: [CommonModule, RouterModule, TrackingMapaComponent],
  templateUrl: './seguimiento-pedido.html',
  styleUrls: ['./seguimiento-pedido.css']
})
export class SeguimientoPedidoComponent implements OnInit {

  pedido = signal<any>(null);

  numeroPedido = signal('');

  idPedidoRuta = 0;

  /** Minutos estimados de viaje tienda -> entrega (se calcula al cargar el pedido). */
  tripMin = 20;

  constructor(
    private route: ActivatedRoute,
    private pedidoService: PedidoService,
    private ruta: TrackingRutaService
  ) { }

  estadoActual = signal(0);

  pasos: any[] = [];

  ngOnInit(): void {

    const idPedido = Number(
      this.route.snapshot.paramMap.get('id')
    );
    this.idPedidoRuta = idPedido;

    this.pedidoService.obtenerPedidos().subscribe({
      next: async pedidos => {

        const pedido = pedidos.find(
          (p: any) => p.idPedido === idPedido
        );

        this.pedido.set(pedido);

        if (!pedido) return;

        const index = pedidos.findIndex(
          (p: any) => p.idPedido === idPedido
        );

        const numeroVisible =
          (pedidos.length - index)
            .toString()
            .padStart(6, '0');

        this.numeroPedido.set(numeroVisible);

        // Misma distancia que el mapa: se resuelve el destino y se calcula el viaje.
        const [la, ln] = await this.ruta.resolverDestino(pedido);
        this.tripMin = this.ruta.tripMin(this.ruta.distanciaM(TIENDA_LAT, TIENDA_LNG, la, ln));
        this.calcularEstado(pedido.fecha);
        this.generarFechasTimeline(pedido.fecha, pedido.direccionEnvio || 'Bodega seleccionada', pedido.esUrgente);
      }
    });
  }

  generarFechasTimeline(fechaPedido: string, direccionBodega: string, esUrgente: boolean): void {

    const inicio = new Date(fechaPedido);

    // +1 hora
    const despacho = new Date(inicio);
    despacho.setMinutes(
      despacho.getMinutes() + 60
    );

    // +15 min
    const motorizado = new Date(despacho);
    motorizado.setMinutes(
      motorizado.getMinutes() + 15
    );

    // +1 hora
    const camino = new Date(motorizado);
    camino.setMinutes(
      camino.getMinutes() + 60
    );

    // +30 min para entrega final
    const entregado = new Date(camino);
    entregado.setMinutes(
      entregado.getMinutes() + 30
    );

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

  /** Tope de paso según el estado real: el timeline nunca corre más rápido que el pedido. */
  private topeEstadoPedido(): number {
    const est = (this.pedido()?.estado || '').toUpperCase();
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 4;
    if (est.includes('EN_CAMINO')) return 3;
    if (est.includes('PAGAD') || est.includes('CONFIRMAD')) return 1;
    return 4;
  }

  calcularEstado(fechaPedido: string): void {
    const ped = this.pedido();
    if (ped && (ped.estado === 'EN_ESPERA' || ped.estado === 'ESPERANDO' || ped.estado === 'PENDIENTE_VERIFICACION')) {
      this.estadoActual.set(0);
      return;
    }

    const inicio = new Date(fechaPedido).getTime();
    const ahora = new Date().getTime();
    const minutos = (ahora - inicio) / (1000 * 60);

    let paso: number;
    // Tiempos por etapa + viaje según distancia (igual que el mapa).
    const t0 = MIN_CONFIRMADO; // 1
    const t1 = t0 + MIN_DESPACHO; // 6
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