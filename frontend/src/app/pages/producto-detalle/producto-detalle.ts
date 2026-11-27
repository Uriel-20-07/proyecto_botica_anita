import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CatalogoService, ProductoApi, VarianteApi } from '../../services/catalogo.service';
import { CartService, PresentacionApi } from '../../services/cart.service';
import { FavoritosService } from '../../services/favoritos.service';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-producto-detalle',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './producto-detalle.html',
  styleUrl: './producto-detalle.css'
})
export class ProductoDetalleComponent implements OnInit {
  producto: any = null;
  cargando = true;
  mensaje = '';
  // Flag para mostrar/ocultar todo el flujo de recetas (ver environment.mostrarRecetas)
  readonly mostrarRecetas = environment.mostrarRecetas ?? false;
  cantidad = 1;
  descuentoInfo: any = null;
  agotado = false;

  /** Presentaciones (blíster, caja...) del producto. Lista vacía = se vende normalmente, sin presentaciones. */
  presentaciones: PresentacionApi[] = [];
  presentacionSeleccionada: PresentacionApi | null = null;

  /** Tallas de la línea del producto (pañales...). Vacía = el producto no tiene variantes. Cada talla es un producto aparte. */
  variantes: VarianteApi[] = [];

  private readonly imagenPorCategoria: Record<string, string> = {
    medicamentos: 'assets/img/producto1.png',
    'cuidado personal': 'assets/img/producto2.png',
    belleza: 'assets/img/producto3.png',
    bebé: 'assets/img/producto4.png',
    'vitaminas / suplementos': 'assets/img/producto1.png',
    'equipo médicos': 'assets/img/producto2.png',
    'equipos médicos': 'assets/img/producto2.png'
  };

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly catalogoService: CatalogoService,
    private readonly cartService: CartService,
    private readonly favoritosService: FavoritosService,
    private readonly authService: AuthService,
    private readonly authModalService: AuthModalService,
    private readonly toastService: ToastService
  ) {
    // Recalcular el límite del selector cada vez que cambia el carrito
    // (agregado aquí, en el chatbot, en la página de carrito, etc.).
    this.cartService.items$
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.ajustarCantidadAlLimite());
  }

  /**
   * Unidades de ESTE producto que ya están en el carrito.
   */
  get cantidadEnCarrito(): number {
    if (!this.producto) return 0;
    // Con presentación elegida, el límite de 10 se cuenta sobre la línea de esa presentación.
    return this.cartService.cantidadEnCarrito(this.producto.id, this.presentacionSeleccionada?.idPresentacion ?? null);
  }

  /**
   * Límite máximo seleccionable con los botones - y +:
   * nunca más de 10 en total (sumando lo que ya está en el carrito)
   * ni más de las unidades disponibles en stock.
   */
  get maxPermitido(): number {
    if (!this.producto) return 0;
    if (this.presentacionSeleccionada) {
      // El backend ya calcula cuántas se pueden pedir según el stock (en unidades base) y el tope de 10.
      return Math.max(0, Math.min(this.presentacionSeleccionada.maxPorPedido, 10 - this.cantidadEnCarrito));
    }
    const disponible = this.cartService.stockVendible(this.producto);
    const stock = disponible != null ? disponible : Number.POSITIVE_INFINITY;
    return Math.max(0, Math.min(stock, 10 - this.cantidadEnCarrito));
  }

  /** true si lo elegido no se puede comprar ahora (producto agotado o presentación sin stock suficiente). */
  get noDisponible(): boolean {
    return this.agotado || (!!this.presentacionSeleccionada && !this.presentacionSeleccionada.disponible);
  }

  /** true cuando ya no se puede agregar nada más de este producto. */
  get limiteAlcanzado(): boolean {
    return !!this.producto && this.maxPermitido === 0;
  }

  /** Mensaje claro cuando se alcanzó el máximo del carrito (no por stock agotado). */
  get mensajeLimite(): string {
    if (this.noDisponible || this.maxPermitido > 0) return '';
    if (this.cantidadEnCarrito >= 10) {
      return this.presentacionSeleccionada
        ? 'Ya tienes el máximo de 10 de esta presentación en tu carrito'
        : 'Ya tienes el máximo de 10 unidades de este producto en tu carrito';
    }
    return 'No hay más unidades disponibles de este producto para agregar al carrito';
  }

  /**
   * Ajusta la cantidad seleccionada para que nunca supere el máximo permitido.
   * Se ejecuta al cargar el producto y en cada cambio del carrito.
   * Si el límite se libera (p. ej. se quitaron unidades desde /carrito),
   * vuelve a 1 en lugar de quedarse en 0.
   */
  private ajustarCantidadAlLimite(): void {
    if (!this.producto) return;
    const max = this.maxPermitido;
    if (this.cantidad > max) {
      this.cantidad = Math.max(0, max);
    } else if (this.cantidad < 1 && max >= 1) {
      this.cantidad = 1;
    }
  }

  ngOnInit(): void {
    // Escuchar parámetros de ruta y parámetros de búsqueda de forma combinada
    this.route.paramMap.subscribe(routeParams => {
      this.route.queryParamMap.subscribe(queryParams => {
        let prodIdStr = queryParams.get('producto');

        // Alternativa 1: si no está en query parameters, buscar en el parámetro de ruta :id (ej. id = "producto=1")
        if (!prodIdStr) {
          const pathId = routeParams.get('id');
          if (pathId) {
            const match = pathId.match(/producto=(\d+)/);
            if (match) {
              prodIdStr = match[1];
            } else {
              prodIdStr = pathId;
            }
          }
        }

        // Alternativa 2: fallback buscando en toda la cadena de la URL
        if (!prodIdStr) {
          const url = this.router.url;
          const match = url.match(/producto=(\d+)/);
          if (match) {
            prodIdStr = match[1];
          }
        }

        if (prodIdStr) {
          const prodId = parseInt(prodIdStr, 10);
          this.cargarProducto(prodId);
        } else {
          this.cargando = false;
          this.mensaje = 'Producto no especificado.';
        }
      });
    });
  }

  cargarProducto(id: number): void {
    this.cargando = true;
    this.catalogoService.getProducto(id).subscribe({
      next: (found) => {
        if (found) {
          const catNombre = found.categoria?.nombre ?? 'General';
          const catKey = catNombre.toLowerCase();
          const imagen = found.imgUrl || this.imagenPorCategoria[catKey] || 'assets/img/placeholder-pill.png';
          const fechaCad = this.getFecha(found);
          this.descuentoInfo = this.cartService.getDiscountInfo(found);
          this.agotado = this.cartService.estaAgotado(found);

          this.producto = {
            id: found.idProducto,
            nombre: found.nombre,
            grupoNombre: found.grupoNombre ?? null,
            varianteEtiqueta: found.varianteEtiqueta ?? null,
            descripcion: found.descripcion ?? 'Producto de la farmacia BoticaAnita',
            precioVenta: found.precioVenta,
            precioConDescuento: found.precioConDescuento != null ? Number(found.precioConDescuento) : Number(found.precioVenta),
            categoriaNombre: catNombre,
            imagen: imagen,
            fechaCaducidad: fechaCad,
            stock: found.stock,
            stockEnCarrito: found.stockEnCarrito,
            stockDisponible: found.stockDisponible,
            agotadoOnline: found.agotadoOnline,
            requiereReceta: this.requiereRecetaMedica(found.nombre)
          };
          this.mensaje = ''; // Limpiar mensaje de error ya que el producto fue encontrado con éxito
          // Respetar el límite de 10 por producto según lo que ya esté en el carrito
          this.cantidad = 1;
          this.ajustarCantidadAlLimite();
          this.cargarPresentaciones(found.idProducto);
          this.cargarVariantes(found.idProducto);
        } else {
          this.producto = null; // Limpiar datos previos si no se encuentra
          this.mensaje = 'Producto no encontrado.';
        }
        this.cargando = false;
      },
      error: (err) => {
        this.producto = null;
        this.mensaje = err?.status === 404
          ? 'Producto no encontrado.'
          : 'Error al cargar los detalles del producto.';
        this.cargando = false;
      }
    });
  }

  /** Carga las tallas de la línea del producto. Si no tiene variantes la lista queda vacía. */
  private cargarVariantes(idProducto: number): void {
    this.variantes = [];
    this.catalogoService.getVariantes(idProducto).subscribe((lista) => {
      // Solo si el cliente sigue viendo este mismo producto (puede haber cambiado de talla mientras cargaba).
      if (this.producto?.id === idProducto) this.variantes = lista.length > 1 ? lista : [];
    });
  }

  /** El cliente elige otra talla: se abre el producto de esa talla (con su propio precio y stock). */
  seleccionarVariante(v: VarianteApi): void {
    if (!this.producto || v.idProducto === this.producto.id) return;
    this.router.navigate(['/catalogo/producto'], { queryParams: { producto: v.idProducto } });
  }

  /**
   * Carga las presentaciones del producto. Conserva la que el cliente tenía elegida; si no, usa la
   * predeterminada (la caja) o la primera disponible.
   */
  private cargarPresentaciones(idProducto: number): void {
    const anterior = this.presentacionSeleccionada?.idPresentacion ?? null;
    this.cartService.getPresentaciones(idProducto).subscribe((lista) => {
      this.presentaciones = lista;
      this.presentacionSeleccionada =
        lista.find((p) => p.idPresentacion === anterior)
        ?? lista.find((p) => p.esPredeterminada && p.disponible)
        ?? lista.find((p) => p.disponible)
        ?? lista[0]
        ?? null;
      this.ajustarCantidadAlLimite();
    });
  }

  /** El cliente elige otra presentación (blíster / caja). */
  seleccionarPresentacion(p: PresentacionApi): void {
    if (!p.disponible) return;
    this.presentacionSeleccionada = p;
    this.cantidad = 1;
    this.ajustarCantidadAlLimite();
  }

  /** Precio regular (sin oferta) a mostrar: el de la presentación elegida o el del producto. */
  get precioRegularMostrado(): number {
    if (!this.producto) return 0;
    return this.presentacionSeleccionada ? this.presentacionSeleccionada.precioRegular : Number(this.producto.precioVenta);
  }

  /** true si hay descuento sobre lo elegido (para mostrar el precio regular tachado). */
  get hayDescuentoMostrado(): boolean {
    return this.getPrecioFinal() < this.precioRegularMostrado;
  }

  getFecha(producto: any): string {
    const fecha = producto.fechaCaducidad || producto.fecha_caducidad;
    if (!fecha) return '';
    if (Array.isArray(fecha)) {
      const mes = fecha[1] < 10 ? '0' + fecha[1] : fecha[1];
      const dia = fecha[2] < 10 ? '0' + fecha[2] : fecha[2];
      return `${fecha[0]}-${mes}-${dia}`;
    }
    return fecha;
  }

  getPrecioFinal(): number {
    if (!this.producto) return 0;
    if (this.presentacionSeleccionada) return this.presentacionSeleccionada.precio;
    return this.producto.precioConDescuento != null ? this.producto.precioConDescuento : this.producto.precioVenta;
  }

  incrementar(): void {
    if (this.noDisponible || this.limiteAlcanzado) return;
    if (this.cantidad >= this.maxPermitido) return;
    this.cantidad++;
  }

  decrementar(): void {
    if (this.noDisponible) return;
    if (this.cantidad > 1) {
      this.cantidad--;
    }
  }

  agregarAlCarrito(): void {
  if (!this.producto || this.noDisponible || this.limiteAlcanzado) return;
  const { id, nombre } = this.producto;
  const presentacion = this.presentacionSeleccionada;
  const textoItem = presentacion ? `${nombre} · ${presentacion.etiqueta}` : nombre;
  // Nunca enviar más de lo que permite el límite (10 en total - lo ya en el carrito)
  const cantidad = Math.max(1, Math.min(this.cantidad, this.maxPermitido));

  this.cartService.addWithQty(id, cantidad, presentacion?.idPresentacion ?? null).subscribe({
    next: () => {
      this.toastService.mostrar(`${textoItem} (x${cantidad}) agregado al carrito`);
      this.mensaje = `${textoItem} (x${cantidad}) agregado al carrito.`;
      // El stock disponible cambió (la reserva descuenta unidades): se vuelve a pedir para el selector.
      if (presentacion) this.cargarPresentaciones(id);
      // El carrito ya se actualizó (tap emite antes del next): reiniciar el
      // selector sin superar el nuevo máximo restante (puede ser 0).
      this.cantidad = this.maxPermitido >= 1 ? 1 : 0;
      setTimeout(() => (this.mensaje = ''), 3000);
    },
    error: (err) => {
      if (err?.status === 401) return; // ya se abrió el modal de login
      const msg = err?.error?.error || err?.error?.mensaje || err?.error?.message
        || 'No se pudo agregar el producto al carrito.';
      this.toastService.mostrar(msg);
    },
  });
}

  isFavorito(): boolean {
    if (!this.producto) return false;
    return this.favoritosService.isFavorito(this.producto.id);
  }

  toggleFavorito(): void {
    if (!this.producto) return;
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }
    this.favoritosService.toggleFavorito(this.producto.id).subscribe();
  }

  private requiereRecetaMedica(nombre: string): boolean {
    if (!nombre) return false;
    const n = nombre.toLowerCase();
    return n.includes('clonazepam') || n.includes('losartan') || n.includes('losartán');
  }
}
