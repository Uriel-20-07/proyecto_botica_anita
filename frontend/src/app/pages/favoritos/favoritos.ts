import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FavoritosService } from '../../services/favoritos.service';
import { CartService } from '../../services/cart.service';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';
import { ProductoApi } from '../../services/catalogo.service';

interface ProductoVista {
  id: number;
  nombre: string;
  descripcion: string;
  precio: number;
  precioOriginal: number;
  categoriaNombre: string;
  imagen: string;
  etiquetaPromo?: string;
  colorPromo?: 'orange' | 'red';
  fechaCaducidad?: string;
  descuentoInfo?: {
    percentage: number;
    message: string;
    isExpired: boolean;
  };
}

@Component({
  selector: 'app-favoritos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './favoritos.html',
  styleUrls: ['./favoritos.css']
})
export class FavoritosComponent implements OnInit {
  productos: ProductoVista[] = [];
  cargando = true;
  mensaje = '';

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
    readonly authService: AuthService,
    private readonly authModalService: AuthModalService,
    private readonly favoritosService: FavoritosService,
    private readonly cartService: CartService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    // Sincronizar con el estado de autenticación
    this.authService.token$.subscribe((token) => {
      if (token) {
        this.cargarFavoritos();
      } else {
        this.productos = [];
        this.cargando = false;
      }
    });

    if (this.authService.isAuthenticated()) {
      this.cargarFavoritos();
    } else {
      this.cargando = false;
    }
  }

  cargarFavoritos(): void {
    this.cargando = true;
    this.favoritosService.obtenerFavoritosCompletos().subscribe({
      next: (productosApi) => {
        this.productos = productosApi.map((p) => this.mapProducto(p));
        this.cargando = false;
      },
      error: (err) => {
        console.error('Error al cargar favoritos completos:', err);
        this.mensaje = 'Ocurrió un error al cargar tus productos favoritos.';
        this.cargando = false;
      }
    });
  }

  toggleFavorito(idProducto: number, event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }
    this.favoritosService.toggleFavorito(idProducto).subscribe({
      next: () => {
        // Recargar la lista inmediatamente al remover de favoritos
        this.cargarFavoritos();
      }
    });
  }

  agregarProducto(producto: ProductoVista): void {
    const productoApi: ProductoApi = {
      idProducto: producto.id,
      nombre: producto.nombre,
      descripcion: producto.descripcion,
      precioVenta: producto.precio,
      fechaCaducidad: producto.fechaCaducidad
    };

    this.cartService.add(productoApi);
    this.mensaje = `${producto.nombre} agregado al carrito.`;

    window.setTimeout(() => {
      if (this.mensaje.includes(producto.nombre)) {
        this.mensaje = '';
      }
    }, 2500);
  }

  isFavorito(idProducto: number): boolean {
    return this.favoritosService.isFavorito(idProducto);
  }

  abrirLogin(): void {
    this.authModalService.open('login');
  }

  private mapProducto(producto: any): ProductoVista {
    const categoriaNombre = producto.categoria?.nombre ?? 'General';
    const categoriaKey = categoriaNombre.toLowerCase();
    const imagen = producto.imgUrl || this.imagenPorCategoria[categoriaKey] || 'assets/img/placeholder-pill.png';
    
    const fechaCad = this.getFecha(producto);
    const descuento = this.cartService.getDiscountInfo(producto);

    return {
      id: producto.idProducto,
      nombre: producto.nombre,
      descripcion: producto.descripcion ?? 'Producto del catálogo FarmaCode',
      precio: producto.precioConDescuento != null ? Number(producto.precioConDescuento) : Number(producto.precioVenta),
      precioOriginal: Number(producto.precioVenta),
      categoriaNombre,
      imagen,
      etiquetaPromo: descuento.message || this.obtenerPromo(producto.nombre, categoriaNombre),
      colorPromo: descuento.message ? 'red' : (categoriaNombre === 'MEDICAMENTOS' ? 'red' : 'orange'),
      fechaCaducidad: fechaCad,
      descuentoInfo: descuento
    };
  }

  private getFecha(producto: any): string {
    const fecha = producto.fechaCaducidad || producto.fecha_caducidad;
    if (!fecha) return '';
    if (Array.isArray(fecha)) {
      const mes = fecha[1] < 10 ? '0' + fecha[1] : fecha[1];
      const dia = fecha[2] < 10 ? '0' + fecha[2] : fecha[2];
      return `${fecha[0]}-${mes}-${dia}`;
    }
    return fecha;
  }

  private obtenerPromo(nombre: string, categoriaNombre: string): string | undefined {
    const nom = nombre.toLowerCase();
    const cat = categoriaNombre.toUpperCase();

    if (cat === 'MEDICAMENTOS') {
      if (nom.includes('paracetamol')) return '2x1';
      if (nom.includes('ibuprofeno')) return '20% dscto';
      if (nom.includes('amoxicilina')) return 'Receta Médica';
      return 'Uso delicado';
    }
    if (cat === 'CUIDADO PERSONAL') {
      if (nom.includes('shampoo') || nom.includes('jabón')) return '3x2';
      return 'Cuidado Diario';
    }
    if (cat === 'BELLEZA') {
      return '15% dscto';
    }
    if (cat === 'BEBÉ') {
      if (nom.includes('pañal') || nom.includes('pañales')) return 'Súper Pack';
      return 'Fórmula Hipoalergénica';
    }
    return undefined;
  }
}
