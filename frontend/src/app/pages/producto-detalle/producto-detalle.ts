import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CatalogoService, ProductoApi } from '../../services/catalogo.service';
import { CartService } from '../../services/cart.service';
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
    return this.cartService.cantidadEnCarrito(this.producto.id);
  }

  /**
   * Límite máximo seleccionable con los botones - y +:
   * nunca más de 10 en total (sumando lo que ya está en el carrito)
   * ni más de las unidades disponibles en stock.
   */
  get maxPermitido(): number {
    if (!this.producto) return 0;
    const stock = this.producto.stock != null ? Number(this.producto.stock) : Number.POSITIVE_INFINITY;
    return Math.max(0, Math.min(stock, 10 - this.cantidadEnCarrito));
  }

  /** true cuando ya no se puede agregar nada más de este producto. */
  get limiteAlcanzado(): boolean {
    return !!this.producto && this.maxPermitido === 0;
  }

  /** Mensaje claro cuando se alcanzó el máximo del carrito (no por stock agotado). */
  get mensajeLimite(): string {
    if (this.agotado || this.maxPermitido > 0) return '';
    if (this.cantidadEnCarrito >= 10) {
      return 'Ya tienes el máximo de 10 unidades de este producto en tu carrito';
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
    this.catalogoService.getProductos().subscribe({
      next: (productos) => {
        const found = productos.find(p => p.idProducto === id);
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
            descripcion: found.descripcion ?? 'Producto de la farmacia BoticaAnita',
            precioVenta: found.precioVenta,
            precioConDescuento: found.precioConDescuento != null ? Number(found.precioConDescuento) : Number(found.precioVenta),
            categoriaNombre: catNombre,
            imagen: imagen,
            fechaCaducidad: fechaCad,
            stock: found.stock,
            requiereReceta: this.requiereRecetaMedica(found.nombre)
          };
          this.mensaje = ''; // Limpiar mensaje de error ya que el producto fue encontrado con éxito
          // Respetar el límite de 10 por producto según lo que ya esté en el carrito
          this.cantidad = 1;
          this.ajustarCantidadAlLimite();
        } else {
          this.producto = null; // Limpiar datos previos si no se encuentra
          this.mensaje = 'Producto no encontrado.';
        }
        this.cargando = false;
      },
      error: () => {
        this.producto = null;
        this.mensaje = 'Error al cargar los detalles del producto.';
        this.cargando = false;
      }
    });
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
    return this.producto.precioConDescuento != null ? this.producto.precioConDescuento : this.producto.precioVenta;
  }

  incrementar(): void {
    if (this.agotado || this.limiteAlcanzado) return;
    if (this.cantidad >= this.maxPermitido) return;
    this.cantidad++;
  }

  decrementar(): void {
    if (this.agotado) return;
    if (this.cantidad > 1) {
      this.cantidad--;
    }
  }

  agregarAlCarrito(): void {
  if (!this.producto || this.agotado || this.limiteAlcanzado) return;
  const { id, nombre } = this.producto;
  // Nunca enviar más de lo que permite el límite (10 en total - lo ya en el carrito)
  const cantidad = Math.max(1, Math.min(this.cantidad, this.maxPermitido));

  this.cartService.addWithQty(id, cantidad).subscribe({
    next: () => {
      this.toastService.mostrar(`${nombre} (x${cantidad}) agregado al carrito`);
      this.mensaje = `${nombre} (x${cantidad}) agregado al carrito.`;
      // El carrito ya se actualizó (tap emite antes del next): reiniciar el
      // selector sin superar el nuevo máximo restante (puede ser 0).
      this.cantidad = this.maxPermitido >= 1 ? 1 : 0;
      setTimeout(() => (this.mensaje = ''), 3000);
    },
    error: (err) => {
      if (err?.status === 401) return; // ya se abrió el modal de login
      const msg = err?.error?.mensaje || err?.error?.message
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
