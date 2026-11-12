import { Component, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../services/admin.service';
import { AsistenteService } from '../../../services/asistente.service';
import { ToastService } from '../../../services/toast.service';
import { AuthService } from '../../../services/auth.service';
import { AuthModalService } from '../../../services/auth-modal.service';
import { environment } from '../../../../environments/environment';

interface ProductoPOS {
  idProducto: number;
  nombre: string;
  precioVenta: number;
  stock: number;
  imgUrl?: string;
}

interface ItemCarrito {
  producto: ProductoPOS;
  cantidad: number;
}

interface PedidoPendiente {
  idPedido: number;
  fecha: string;
  estado: string;
  total: number;
  direccionEnvio: string;
  distrito: string;
  metodoPago: string;
  esUrgente: boolean;
  usuario?: { nombre: string; correo: string };
}

@Component({
  selector: 'app-asistente-dashboard',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './asistente.component.html',
  styleUrl: './asistente.component.css',
})
export class AsistenteDashboardComponent implements OnInit {
  tabActiva = signal<'venta' | 'pedidos' | 'historial'>('venta');
  cargando = signal(false);

  // POS
  busqueda = signal('');
  productos = signal<ProductoPOS[]>([]);
  productosFiltrados = computed(() => {
    const q = this.busqueda().toLowerCase().trim();
    if (!q) return this.productos().slice(0, 20);
    return this.productos().filter((p) => p.nombre.toLowerCase().includes(q)).slice(0, 20);
  });
  carrito = signal<ItemCarrito[]>([]);
  totalCarrito = computed(() =>
    this.carrito().reduce((s, i) => s + i.producto.precioVenta * i.cantidad, 0)
  );
  metodoPago = signal('EFECTIVO');
  emailCliente = signal('');
  nombreCliente = signal('');
  procesandoVenta = signal(false);

  // Pedidos
  pedidos = signal<PedidoPendiente[]>([]);
  pedidosEntregados = signal<PedidoPendiente[]>([]);
  subTabPedidos = signal<'pendientes' | 'entregados'>('pendientes');
  pedidosVisibles = computed(() => this.subTabPedidos() === 'pendientes' ? this.pedidos() : this.pedidosEntregados());
  pedidoDetalle = signal<PedidoPendiente | null>(null);

  // Historial
  ventasHoy = signal<any[]>([]);
  totalVentasHoy = computed(() =>
    this.ventasHoy().reduce((s, v: any) => s + (v.total || 0), 0)
  );

  constructor(
    private router: Router,
    private adminService: AdminService,
    private asistenteService: AsistenteService,
    private toast: ToastService,
    private authService: AuthService,
    private authModalService: AuthModalService
  ) {}

  ngOnInit(): void {
    if (!this.adminService.isAuthenticated() || !this.adminService.isAsistente()) {
      this.router.navigate(['/']);
      this.authModalService.open('login');
      return;
    }
    this.cargarProductos();
    this.cargarPedidos();
    this.cargarHistorial();
  }

  logout(): void {
    this.adminService.logout();
    this.router.navigate(['/']);
  }

  // ---------- POS ----------
  cargarProductos(): void {
    this.cargando.set(true);
    this.asistenteService.listarProductos().subscribe({
      next: (data: any) => {
        // Ocultar productos con imagenes rotas (URLs que no resuelven)
        const productos = (data || []).filter((p: any) => {
          const img = (p.imgUrl || '').toLowerCase();
          return !img.includes('soyessenza');
        });
        this.productos.set(productos);
        this.cargando.set(false);
      },
      error: () => {
        this.toast.mostrar('Error al cargar productos', 'error');
        this.cargando.set(false);
      },
    });
  }

  agregarAlCarrito(p: ProductoPOS): void {
    const carrito = [...this.carrito()];
    const idx = carrito.findIndex((i) => i.producto.idProducto === p.idProducto);
    if (idx >= 0) {
      if (carrito[idx].cantidad + 1 > p.stock) {
        this.toast.mostrar('Stock insuficiente', 'error');
        return;
      }
      carrito[idx].cantidad++;
    } else {
      carrito.push({ producto: p, cantidad: 1 });
    }
    this.carrito.set(carrito);
  }

  quitarDelCarrito(idProducto: number): void {
    this.carrito.set(this.carrito().filter((i) => i.producto.idProducto !== idProducto));
  }

  cambiarCantidad(idProducto: number, delta: number): void {
    const carrito = this.carrito().map((i) => {
      if (i.producto.idProducto === idProducto) {
        const nueva = i.cantidad + delta;
        if (nueva < 1) return i;
        if (nueva > i.producto.stock) {
          this.toast.mostrar('Stock insuficiente', 'error');
          return i;
        }
        return { ...i, cantidad: nueva };
      }
      return i;
    });
    this.carrito.set(carrito);
  }

  confirmarVenta(): void {
    if (this.carrito().length === 0) {
      this.toast.mostrar('El carrito está vacío', 'error');
      return;
    }
    this.procesandoVenta.set(true);
    const items = this.carrito().map((i) => ({
      idProducto: i.producto.idProducto,
      cantidad: i.cantidad,
    }));
    this.asistenteService.crearVenta({
      items,
      metodoPago: this.metodoPago(),
      emailCliente: this.emailCliente() || null,
      nombreCliente: this.nombreCliente() || 'Cliente en tienda',
    }).subscribe({
      next: (res: any) => {
        this.toast.mostrar(`Venta registrada. Boleta #${res.nroBoleta} — Total: S/ ${res.total}`, 'exito');
        this.carrito.set([]);
        this.emailCliente.set('');
        this.nombreCliente.set('');
        this.cargarProductos();
        this.cargarHistorial();
        this.procesandoVenta.set(false);
      },
      error: (err: any) => {
        this.toast.mostrar(err?.error?.error || 'Error al registrar la venta', 'error');
        this.procesandoVenta.set(false);
      },
    });
  }

  // ---------- Pedidos ----------
  cargarPedidos(): void {
    this.asistenteService.listarPedidosPendientes().subscribe({
      next: (data: any) => this.pedidos.set((data || []).sort((a: any, b: any) => b.idPedido - a.idPedido)),
      error: () => this.toast.mostrar('Error al cargar pedidos', 'error'),
    });
  }

  cargarPedidosEntregados(): void {
    this.asistenteService.listarPedidosEntregados().subscribe({
      next: (data: any) => this.pedidosEntregados.set((data || []).sort((a: any, b: any) => b.idPedido - a.idPedido)),
      error: () => this.toast.mostrar('Error al cargar pedidos entregados', 'error'),
    });
  }

  marcarEntregado(idPedido: number): void {
    this.asistenteService.marcarEntregado(idPedido).subscribe({
      next: () => {
        this.toast.mostrar('Pedido marcado como entregado', 'exito');
        this.cargarPedidos();
        this.cargarPedidosEntregados();
        this.cargarHistorial();
      },
      error: (err: any) => this.toast.mostrar(err?.error?.error || 'Error al marcar entregado', 'error'),
    });
  }

  verDetalle(p: PedidoPendiente): void {
    this.pedidoDetalle.set(p);
  }

  cerrarDetalle(): void {
    this.pedidoDetalle.set(null);
  }

  estadoLegible(estado: string): string {
    const e = (estado || '').toUpperCase();
    if (e.includes('COMPLETAD') || e.includes('ENTREGAD')) return 'Entregado';
    if (e.includes('EN_CAMINO')) return 'En camino';
    if (e.includes('LISTO')) return 'Listo para recojo';
    if (e.includes('PAGAD') || e.includes('CONFIRMAD')) return 'Pendiente';
    if (e.includes('EN_ESPERA') || e.includes('ESPERANDO')) return 'En espera';
    return estado;
  }

  formatFecha(fecha: string): string {
    if (!fecha) return '—';
    try {
      let s = String(fecha).trim().replace(' ', 'T');
      if (!/(Z|z|[+-]\d{2}:?\d{2})$/.test(s)) s += 'Z';
      const d = new Date(s);
      if (isNaN(d.getTime())) return fecha;
      const dia = String(d.getDate()).padStart(2, '0');
      const mes = String(d.getMonth() + 1).padStart(2, '0');
      const anio = d.getFullYear();
      const hora = String(d.getHours()).padStart(2, '0');
      const min = String(d.getMinutes()).padStart(2, '0');
      return `${dia}/${mes}/${anio} ${hora}:${min}`;
    } catch {
      return fecha;
    }
  }

  // ---------- Historial ----------
  cargarHistorial(): void {
    this.asistenteService.historialVentasHoy().subscribe({
      next: (data: any) => this.ventasHoy.set(data || []),
      error: () => { /* silencioso */ },
    });
  }

  cambiarTab(tab: 'venta' | 'pedidos' | 'historial'): void {
    this.tabActiva.set(tab);
    if (tab === 'pedidos') {
      this.cargarPedidos();
      this.cargarPedidosEntregados();
    }
    if (tab === 'historial') this.cargarHistorial();
  }

  cambiarSubTabPedidos(sub: 'pendientes' | 'entregados'): void {
    this.subTabPedidos.set(sub);
    if (sub === 'entregados') this.cargarPedidosEntregados();
  }
}
