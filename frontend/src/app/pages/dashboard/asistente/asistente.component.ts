import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../services/admin.service';
import { AsistenteService } from '../../../services/asistente.service';
import { ToastService } from '../../../services/toast.service';
import { AuthService } from '../../../services/auth.service';
import { AuthModalService } from '../../../services/auth-modal.service';
import { CONTACTO } from '../../soporte/contacto';
import { environment } from '../../../../environments/environment';

interface ProductoPOS {
  idProducto: number;
  nombre: string;
  precioVenta: number;
  stock: number;
  stockEnCarrito?: number;
  stockDisponible?: number;
  imgUrl?: string;
  categoria?: { nombre: string };
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

interface PaginaPedido {
  tipo: 'pagina' | 'gap';
  valor: number;
}

const METODOS_PAGO = [
  { valor: 'EFECTIVO', label: 'Efectivo', icono: 'bi-cash-coin' },
  { valor: 'TARJETA', label: 'Tarjeta', icono: 'bi-credit-card-2-front' },
  { valor: 'YAPE', label: 'Yape', icono: 'bi-phone' },
  { valor: 'PLIN', label: 'Plin', icono: 'bi-phone-vibrate' },
] as const;

@Component({
  selector: 'app-asistente-dashboard',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './asistente.component.html',
  styleUrl: './asistente.component.css',
})
export class AsistenteDashboardComponent implements OnInit, OnDestroy {
  @ViewChild('inputBusqueda') inputBusqueda?: ElementRef<HTMLInputElement>;
  @ViewChild('inicioPedidos') inicioPedidos?: ElementRef<HTMLElement>;

  tabActiva = signal<'venta' | 'pedidos' | 'historial'>('venta');
  cargando = signal(false);
  menuAbierto = signal(false);
  usuario: any = null;

  /** Correo de contacto que se muestra en las acciones de suspendido/cancelado. */
  readonly correoContacto = CONTACTO.correo;

  fechaActual = signal('');
  horaActual = signal('');

  // POS
  busqueda = signal('');
  categoriaActiva = signal('todas');
  productos = signal<ProductoPOS[]>([]);
  categorias = computed(() => {
    const nombres = new Set<string>();
    this.productos().forEach((p) => {
      if (p.categoria?.nombre) nombres.add(p.categoria.nombre);
    });
    return ['todas', ...Array.from(nombres).sort((a, b) => a.localeCompare(b))];
  });
  productosFiltrados = computed(() => {
    const q = this.busqueda().toLowerCase().trim();
    const cat = this.categoriaActiva();
    return this.productos()
      .filter((p) => cat === 'todas' || p.categoria?.nombre === cat)
      .filter((p) => !q || p.nombre.toLowerCase().includes(q))
      .slice(0, 40);
  });
  carrito = signal<ItemCarrito[]>([]);
  totalCarrito = computed(() =>
    this.carrito().reduce((s, i) => s + i.producto.precioVenta * i.cantidad, 0)
  );
  cantidadCarrito = computed(() =>
    this.carrito().reduce((s, i) => s + i.cantidad, 0)
  );
  metodoPago = signal('EFECTIVO');
  emailCliente = signal('');
  nombreCliente = signal('');
  procesandoVenta = signal(false);

  readonly metodosPago = METODOS_PAGO;

  // Pedidos
  pedidos = signal<PedidoPendiente[]>([]);
  pedidosFinalizados = signal<PedidoPendiente[]>([]);
  subTabPedidos = signal<'pendientes' | 'finalizados'>('pendientes');
  busquedaPedidos = signal('');
  pedidosVisibles = computed(() =>
    this.subTabPedidos() === 'pendientes' ? this.pedidos() : this.pedidosFinalizados()
  );

  /** Texto normalizado para comparar búsquedas (sin tildes ni mayúsculas). */
  private normalizarPedido(valor: unknown): string {
    return (valor ?? '')
      .toString()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }

  /** Filtra una lista de pedidos con el término de búsqueda actual. */
  private filtrarPedidos(lista: PedidoPendiente[]): PedidoPendiente[] {
    const q = this.normalizarPedido(this.busquedaPedidos()).trim();
    if (!q) return lista;
    return lista.filter(
      (p) =>
        this.normalizarPedido(p.idPedido).includes(q) ||
        this.normalizarPedido(p.fecha).includes(q) ||
        this.normalizarPedido(p.distrito).includes(q) ||
        this.normalizarPedido(p.direccionEnvio).includes(q) ||
        this.normalizarPedido(p.metodoPago).includes(q) ||
        this.normalizarPedido(p.usuario?.nombre).includes(q) ||
        this.normalizarPedido(p.usuario?.correo).includes(q)
    );
  }

  pedidosPendientesFiltrados = computed(() => this.filtrarPedidos(this.pedidos()));
  pedidosFinalizadosFiltrados = computed(() => this.filtrarPedidos(this.pedidosFinalizados()));
  pedidosNoEntregados = computed(
    () =>
      this.pedidosFinalizados().filter((p) => {
        const e = (p.estado || '').toUpperCase();
        return e.includes('SUSPENDID') || e.includes('CANCELAD');
      }).length
  );
  pedidosEntregadosCount = computed(
    () =>
      this.pedidosFinalizados().filter((p) => {
        const e = (p.estado || '').toUpperCase();
        return e.includes('COMPLETAD') || e.includes('ENTREGAD');
      }).length
  );

  pedidosEnCamino = computed(
    () =>
      this.pedidos().filter((p) => {
        const e = (p.estado || '').toUpperCase();
        return e.includes('EN_CAMINO') || e.includes('LISTO');
      }).length
  );

  // Paginación de la lista de pedidos (se aplica después del filtro de búsqueda)
  readonly tamanoPaginaPedidos = 20;
  paginaPedidos = signal(1);
  /** Lista activa según la sub-pestaña, ya filtrada por la búsqueda. */
  pedidosFiltrados = computed(() =>
    this.subTabPedidos() === 'pendientes'
      ? this.pedidosPendientesFiltrados()
      : this.pedidosFinalizadosFiltrados()
  );
  totalPaginasPedidos = computed(() =>
    Math.max(1, Math.ceil(this.pedidosFiltrados().length / this.tamanoPaginaPedidos))
  );
  paginaPedidosActual = computed(() =>
    Math.min(Math.max(1, this.paginaPedidos()), this.totalPaginasPedidos())
  );
  pedidosPaginados = computed(() => {
    const pagina = this.paginaPedidosActual();
    const inicio = (pagina - 1) * this.tamanoPaginaPedidos;
    return this.pedidosFiltrados().slice(inicio, inicio + this.tamanoPaginaPedidos);
  });
  rangoPedidos = computed(() => {
    const total = this.pedidosFiltrados().length;
    if (total === 0) return { desde: 0, hasta: 0, total };
    const pagina = this.paginaPedidosActual();
    return {
      desde: (pagina - 1) * this.tamanoPaginaPedidos + 1,
      hasta: Math.min(pagina * this.tamanoPaginaPedidos, total),
      total,
    };
  });
  paginasPedidos = computed<PaginaPedido[]>(() => {
    const total = this.totalPaginasPedidos();
    const actual = this.paginaPedidosActual();
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => ({ tipo: 'pagina' as const, valor: i + 1 }));
    }
    const paginas: PaginaPedido[] = [{ tipo: 'pagina', valor: 1 }];
    const desde = Math.max(2, actual - 1);
    const hasta = Math.min(total - 1, actual + 1);
    if (desde > 2) paginas.push({ tipo: 'gap', valor: 0 });
    for (let i = desde; i <= hasta; i++) paginas.push({ tipo: 'pagina', valor: i });
    if (hasta < total - 1) paginas.push({ tipo: 'gap', valor: 0 });
    paginas.push({ tipo: 'pagina', valor: total });
    return paginas;
  });

  /** Cambia de página y deja visible el inicio de la tabla. */
  irAPaginaPedidos(pagina: number): void {
    const destino = Math.min(Math.max(1, Math.trunc(pagina) || 1), this.totalPaginasPedidos());
    if (destino === this.paginaPedidos()) return;
    this.paginaPedidos.set(destino);
    this.inicioPedidos?.nativeElement?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  anteriorPaginaPedidos(): void {
    this.irAPaginaPedidos(this.paginaPedidosActual() - 1);
  }

  siguientePaginaPedidos(): void {
    this.irAPaginaPedidos(this.paginaPedidosActual() + 1);
  }

  actualizarBusquedaPedidos(valor: string): void {
    this.busquedaPedidos.set(valor);
    this.paginaPedidos.set(1);
  }

  limpiarBusquedaPedidos(): void {
    this.busquedaPedidos.set('');
    this.paginaPedidos.set(1);
  }
  pedidoDetalle = signal<PedidoPendiente | null>(null);
  pedidoAConfirmar = signal<PedidoPendiente | null>(null);
  /** Acción que se está confirmando: entregado, suspendido o cancelado. */
  accionPendiente = signal<'entregado' | 'suspendido' | 'cancelado'>('entregado');
  procesandoEntrega = signal(false);

  /** Textos e iconos del modal de confirmación según la acción elegida. */
  accionInfo = computed(() => {
    switch (this.accionPendiente()) {
      case 'suspendido':
        return {
          titulo: '¿Marcar como suspendido?',
          descripcion: 'se marcará como suspendido (no entregado) y se notificará al cliente por correo.',
          boton: 'Sí, suspender',
          icono: 'bi-exclamation-triangle',
          clase: 'warn',
        };
      case 'cancelado':
        return {
          titulo: '¿Cancelar este pedido?',
          descripcion: 'se marcará como cancelado por receta médica incorrecta y se notificará al cliente por correo.',
          boton: 'Sí, cancelar',
          icono: 'bi-x-octagon',
          clase: 'danger',
        };
      default:
        return {
          titulo: '¿Confirmar entrega?',
          descripcion: 'se marcará como entregado y se notificará al cliente por correo.',
          boton: 'Sí, entregar',
          icono: 'bi-check2-circle',
          clase: 'ok',
        };
    }
  });

  // Historial
  ventasHoy = signal<any[]>([]);
  totalVentasHoy = computed(() =>
    this.ventasHoy().reduce((s, v: any) => s + (Number(v.total) || 0), 0)
  );
  ticketPromedio = computed(() =>
    this.ventasHoy().length > 0 ? this.totalVentasHoy() / this.ventasHoy().length : 0
  );
  ventasPorMetodo = computed(() => {
    const mapa = new Map<string, { metodo: string; cantidad: number; total: number }>();
    this.ventasHoy().forEach((v: any) => {
      const metodo = (v.metodoPago || 'OTRO').toUpperCase();
      const item = mapa.get(metodo) ?? { metodo, cantidad: 0, total: 0 };
      item.cantidad++;
      item.total += Number(v.total) || 0;
      mapa.set(metodo, item);
    });
    return Array.from(mapa.values()).sort((a, b) => b.total - a.total);
  });
  metodoMasUsado = computed(() => this.ventasPorMetodo()[0]?.metodo ?? '—');

  tituloSeccion = computed(() => {
    switch (this.tabActiva()) {
      case 'pedidos':
        return 'Pedidos';
      case 'historial':
        return 'Historial de ventas';
      default:
        return 'Punto de venta';
    }
  });

  subtituloSeccion = computed(() => {
    switch (this.tabActiva()) {
      case 'pedidos':
        return 'Confirma entregas y revisa pedidos';
      case 'historial':
        return 'Resumen de ventas presenciales del día';
      default:
        return 'Registra ventas presenciales de forma rápida';
    }
  });

  private relojId?: ReturnType<typeof setInterval>;
  private refreshId?: ReturnType<typeof setInterval>;

  constructor(
    private router: Router,
    private adminService: AdminService,
    private asistenteService: AsistenteService,
    private toast: ToastService,
    private authService: AuthService,
    private authModalService: AuthModalService
  ) {
    this.usuario = this.adminService.getCurrentUser();
  }

  ngOnInit(): void {
    if (!this.adminService.isAuthenticated() || !this.adminService.isAsistente()) {
      this.router.navigate(['/']);
      this.authModalService.open('login');
      return;
    }
    this.actualizarReloj();
    this.relojId = setInterval(() => this.actualizarReloj(), 15000);
    this.refreshId = setInterval(() => {
      if (this.tabActiva() === 'pedidos') {
        this.cargarPedidos();
        this.cargarPedidosFinalizados();
      } else if (this.tabActiva() === 'historial') {
        this.cargarHistorial();
      }
    }, 60000);

    this.cargarProductos();
    this.cargarPedidos();
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    if (this.relojId) clearInterval(this.relojId);
    if (this.refreshId) clearInterval(this.refreshId);
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      if (this.tabActiva() !== 'venta') this.cambiarTab('venta');
      setTimeout(() => this.inputBusqueda?.nativeElement.focus(), 0);
      return;
    }
    if (event.key === 'Escape') {
      if (this.pedidoAConfirmar()) this.cancelarConfirmacion();
      else if (this.pedidoDetalle()) this.cerrarDetalle();
      else if (this.menuAbierto()) this.menuAbierto.set(false);
    }
  }

  private actualizarReloj(): void {
    const ahora = new Date();
    this.fechaActual.set(
      ahora.toLocaleDateString('es-PE', { weekday: 'short', day: '2-digit', month: 'short' })
    );
    this.horaActual.set(
      ahora.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })
    );
  }

  logout(): void {
    this.adminService.logout();
    this.router.navigate(['/']);
  }

  refrescar(): void {
    const tab = this.tabActiva();
    if (tab === 'venta') this.cargarProductos();
    else if (tab === 'pedidos') {
      this.cargarPedidos();
      this.cargarPedidosFinalizados();
    } else {
      this.cargarHistorial();
    }
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
        const categorias = new Set(
          productos.map((p: any) => p.categoria?.nombre).filter(Boolean)
        );
        if (this.categoriaActiva() !== 'todas' && !categorias.has(this.categoriaActiva())) {
          this.categoriaActiva.set('todas');
        }
        this.cargando.set(false);
      },
      error: () => {
        this.toast.mostrar('Error al cargar productos', 'error');
        this.cargando.set(false);
      },
    });
  }

  /** Unidades que el POS puede vender: stock físico menos reservas online. */
  stockVendible(p: ProductoPOS): number {
    if (!p) return 0;
    return p.stockDisponible != null ? Number(p.stockDisponible) : Number(p.stock) || 0;
  }

  agregarAlCarrito(p: ProductoPOS): void {
    const carrito = [...this.carrito()];
    const idx = carrito.findIndex((i) => i.producto.idProducto === p.idProducto);
    if (idx >= 0) {
      if (carrito[idx].cantidad + 1 > this.stockVendible(p)) {
        this.toast.mostrar('Stock insuficiente (las unidades reservadas en carritos no se pueden vender en tienda)', 'error');
        return;
      }
      carrito[idx].cantidad++;
    } else {
      if (this.stockVendible(p) < 1) {
        this.toast.mostrar('Producto sin stock disponible', 'error');
        return;
      }
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
        if (nueva > this.stockVendible(i.producto)) {
          this.toast.mostrar('Stock insuficiente (las unidades reservadas en carritos no se pueden vender en tienda)', 'error');
          return i;
        }
        return { ...i, cantidad: nueva };
      }
      return i;
    });
    this.carrito.set(carrito);
  }

  limpiarCarrito(): void {
    this.carrito.set([]);
    this.toast.mostrar('Carrito vaciado');
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
      next: (data: any) => {
        this.pedidos.set((data || []).sort((a: any, b: any) => b.idPedido - a.idPedido));
        this.paginaPedidos.update((p) => Math.min(p, this.totalPaginasPedidos()));
      },
      error: () => this.toast.mostrar('Error al cargar pedidos', 'error'),
    });
  }

  cargarPedidosFinalizados(): void {
    this.asistenteService.listarPedidosFinalizados().subscribe({
      next: (data: any) => {
        this.pedidosFinalizados.set((data || []).sort((a: any, b: any) => b.idPedido - a.idPedido));
        this.paginaPedidos.update((p) => Math.min(p, this.totalPaginasPedidos()));
      },
      error: () => this.toast.mostrar('Error al cargar pedidos finalizados', 'error'),
    });
  }

  /** Abre el modal de confirmación para la acción elegida. */
  solicitarAccion(p: PedidoPendiente, accion: 'entregado' | 'suspendido' | 'cancelado'): void {
    this.pedidoDetalle.set(null);
    this.accionPendiente.set(accion);
    this.pedidoAConfirmar.set(p);
  }

  cancelarConfirmacion(): void {
    if (this.procesandoEntrega()) return;
    this.pedidoAConfirmar.set(null);
  }

  /** Ejecuta la acción confirmada (entregado / suspendido / cancelado). */
  confirmarAccion(): void {
    const p = this.pedidoAConfirmar();
    if (!p || this.procesandoEntrega()) return;
    const accion = this.accionPendiente();
    this.procesandoEntrega.set(true);

    const peticion =
      accion === 'suspendido'
        ? this.asistenteService.marcarSuspendido(p.idPedido)
        : accion === 'cancelado'
          ? this.asistenteService.marcarCancelado(p.idPedido)
          : this.asistenteService.marcarEntregado(p.idPedido);

    peticion.subscribe({
      next: () => {
        const mensaje =
          accion === 'suspendido'
            ? `Pedido #${p.idPedido} marcado como suspendido`
            : accion === 'cancelado'
              ? `Pedido #${p.idPedido} marcado como cancelado`
              : `Pedido #${p.idPedido} marcado como entregado`;
        this.toast.mostrar(mensaje, 'exito');
        this.pedidoAConfirmar.set(null);
        this.procesandoEntrega.set(false);
        this.cargarPedidos();
        this.cargarPedidosFinalizados();
        this.cargarHistorial();
      },
      error: (err: any) => {
        this.toast.mostrar(err?.error?.error || 'Error al actualizar el pedido', 'error');
        this.procesandoEntrega.set(false);
      },
    });
  }

  verDetalle(p: PedidoPendiente): void {
    this.pedidoDetalle.set(p);
  }

  cerrarDetalle(): void {
    this.pedidoDetalle.set(null);
  }

  puedeEntregar(p: PedidoPendiente): boolean {
    const e = (p.estado || '').toUpperCase();
    return !e.includes('COMPLETAD') && !e.includes('ENTREGAD') && !e.includes('CANCELAD')
      && !e.includes('SUSPENDID') && !e.includes('RECHAZAD');
  }

  estadoLegible(estado: string): string {
    const e = (estado || '').toUpperCase();
    if (e.includes('COMPLETAD') || e.includes('ENTREGAD')) return 'Entregado';
    if (e.includes('SUSPENDID')) return 'Suspendido';
    if (e.includes('CANCELAD')) return 'Cancelado';
    if (e.includes('EN_CAMINO')) return 'En camino';
    if (e.includes('LISTO')) return 'Listo para recojo';
    if (e.includes('PAGAD') || e.includes('CONFIRMAD')) return 'Pendiente';
    if (e.includes('EN_ESPERA') || e.includes('ESPERANDO')) return 'En espera';
    return estado;
  }

  estadoClase(estado: string): string {
    const e = (estado || '').toUpperCase();
    if (e.includes('COMPLETAD') || e.includes('ENTREGAD')) return 'entregado';
    if (e.includes('SUSPENDID')) return 'suspendido';
    if (e.includes('CANCELAD')) return 'cancelado';
    if (e.includes('EN_CAMINO')) return 'encamino';
    if (e.includes('LISTO')) return 'listo';
    if (e.includes('PAGAD') || e.includes('CONFIRMAD')) return 'pendiente';
    if (e.includes('ESPERA') || e.includes('ESPERANDO')) return 'espera';
    return 'neutral';
  }

  iconoMetodo(metodo?: string): string {
    switch ((metodo || '').toUpperCase()) {
      case 'EFECTIVO':
        return 'bi-cash-coin';
      case 'TARJETA':
        return 'bi-credit-card-2-front';
      case 'YAPE':
        return 'bi-phone';
      case 'PLIN':
        return 'bi-phone-vibrate';
      default:
        return 'bi-wallet2';
    }
  }

  iniciales(nombre?: string): string {
    if (!nombre) return 'CL';
    const partes = nombre.trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return 'CL';
    if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
    return `${partes[0][0] ?? ''}${partes[partes.length - 1][0] ?? ''}`.toUpperCase();
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

  exportarHistorial(): void {
    if (this.ventasHoy().length === 0) return;
    const filas: string[][] = [
      ['Boleta', 'Fecha', 'Metodo', 'Total'],
      ...this.ventasHoy().map((v: any) => [
        `#${v.idPedido}`,
        this.formatFecha(v.fecha),
        String(v.metodoPago || ''),
        String(v.total ?? ''),
      ]),
    ];
    const csv = filas
      .map((fila) => fila.map((valor) => `"${valor.replace(/"/g, '""')}"`).join(','))
      .join('\r\n');
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    const hoy = new Date().toISOString().slice(0, 10);
    enlace.href = url;
    enlace.download = `ventas-${hoy}.csv`;
    enlace.click();
    URL.revokeObjectURL(url);
    this.toast.mostrar('Historial exportado');
  }

  cambiarTab(tab: 'venta' | 'pedidos' | 'historial'): void {
    this.tabActiva.set(tab);
    this.menuAbierto.set(false);
    if (tab === 'pedidos') {
      this.busquedaPedidos.set('');
      this.paginaPedidos.set(1);
      this.cargarPedidos();
      this.cargarPedidosFinalizados();
    }
    if (tab === 'historial') this.cargarHistorial();
  }

  cambiarSubTabPedidos(sub: 'pendientes' | 'finalizados'): void {
    this.subTabPedidos.set(sub);
    this.busquedaPedidos.set('');
    this.paginaPedidos.set(1);
    if (sub === 'finalizados') this.cargarPedidosFinalizados();
  }
}
