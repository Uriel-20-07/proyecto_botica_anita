import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PedidoService } from '../../services/pedido.service';

@Component({
  selector: 'app-seguimiento-pedido',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './seguimiento-pedido.html',
  styleUrls: ['./seguimiento-pedido.css']
})
export class SeguimientoPedidoComponent implements OnInit {

  pedido = signal<any>(null);

  numeroPedido = signal('');

  estadoActual = signal(0);

  pasos: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private pedidoService: PedidoService
  ) { }

  ngOnInit(): void {

    const idPedido = Number(
      this.route.snapshot.paramMap.get('id')
    );

    this.pedidoService.obtenerPedidos().subscribe({
      next: pedidos => {

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
    const esEspera = ped && (ped.estado === 'EN_ESPERA' || ped.estado === 'ESPERANDO');

    this.pasos = [
      {
        titulo: esEspera ? 'Pedido en espera' : 'Pedido confirmado',
        descripcion: esEspera ? 'Esperando validación de receta médica' : 'Pago recibido correctamente',
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

  calcularEstado(fechaPedido: string): void {
    const ped = this.pedido();
    if (ped && (ped.estado === 'EN_ESPERA' || ped.estado === 'ESPERANDO')) {
      this.estadoActual.set(0);
      return;
    }

    const inicio = new Date(fechaPedido).getTime();
    const ahora = new Date().getTime();
    const minutos = (ahora - inicio) / (1000 * 60);

    // Pedido confirmado
    if (minutos < 1) {
      this.estadoActual.set(0);
    }
    // En despacho (1 hora)
    else if (minutos < 60) {
      this.estadoActual.set(1);
    }
    // Motorizado asignado (15 min)
    else if (minutos < 75) {
      this.estadoActual.set(2);
    }
    // En camino (1 hora)
    else if (minutos < 135) {
      this.estadoActual.set(3);
    }
    // Últimos 30 min antes de entrega
    else if (minutos < 165) {
      this.estadoActual.set(3);
    }
    // Entregado
    else {
      this.estadoActual.set(4);
    }
  }

  estadoTexto(): string {
    const ped = this.pedido();
    if (ped && (ped.estado === 'EN_ESPERA' || ped.estado === 'ESPERANDO')) {
      return 'esperando a que el pedido se confirme';
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