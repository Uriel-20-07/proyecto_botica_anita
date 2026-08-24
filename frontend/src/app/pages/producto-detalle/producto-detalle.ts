import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CatalogoService, ProductoApi } from '../../services/catalogo.service';
import { CartService } from '../../services/cart.service';
import { FavoritosService } from '../../services/favoritos.service';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';

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
  cantidad = 1;
  descuentoInfo: any = null;

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
    private readonly authModalService: AuthModalService
  ) {}

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

          this.producto = {
            id: found.idProducto,
            nombre: found.nombre,
            descripcion: found.descripcion ?? 'Producto de la farmacia FarmaCode',
            precioVenta: found.precioVenta,
            precioConDescuento: found.precioConDescuento != null ? Number(found.precioConDescuento) : Number(found.precioVenta),
            categoriaNombre: catNombre,
            imagen: imagen,
            fechaCaducidad: fechaCad,
            requiereReceta: this.requiereRecetaMedica(found.nombre)
          };
          this.mensaje = ''; // Limpiar mensaje de error ya que el producto fue encontrado con éxito
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
    this.cantidad++;
  }

  decrementar(): void {
    if (this.cantidad > 1) {
      this.cantidad--;
    }
  }

  agregarAlCarrito(): void {
    if (!this.producto) return;
    const prodApi: ProductoApi = {
      idProducto: this.producto.id,
      nombre: this.producto.nombre,
      descripcion: this.producto.descripcion,
      precioVenta: this.producto.precioVenta,
      fechaCaducidad: this.producto.fechaCaducidad
    };
    
    for (let i = 0; i < this.cantidad; i++) {
      this.cartService.add(prodApi);
    }

    this.mensaje = `${this.producto.nombre} (x${this.cantidad}) agregado al carrito.`;
    
    // Clear message after 3 seconds
    setTimeout(() => {
      this.mensaje = '';
    }, 3000);
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
