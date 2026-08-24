import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CartService } from '../../services/cart.service';
import { CatalogoService, CategoriaApi, ProductoApi } from '../../services/catalogo.service';
import { AlgoliaService, AlgoliaProducto } from '../../services/algolia.service';
import { FavoritosService } from '../../services/favoritos.service';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';
import { Subject, takeUntil } from 'rxjs';

/**
 * Interfaz interna para representar un producto adaptado a la vista del catálogo.
 */
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
  requiereReceta?: boolean;
}

/**
 * Componente del catálogo.
 *
 * La barra de búsqueda con autocomplete de Algolia vive ahora en el navbar
 * (ver NavbarComponent). Este componente:
 * - Recibe el término de búsqueda vía query param `?q=` y lo consulta en
 *   Algolia para mostrar los resultados correspondientes.
 * - Mantiene los filtros propios del catálogo: categoría, precio máximo
 *   y ordenamiento (relevancia, precio asc/desc, nombre A-Z).
 * - Resalta el término buscado en los nombres de los resultados.
 */
@Component({
  selector: 'app-catalogo',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './catalogo.html',
  styleUrls: ['./catalogo.css']
})
export class CatalogoComponent implements OnInit, OnDestroy {

  // ─── Estado principal ─────────────────────────────────────────────────────
  categorias: CategoriaApi[] = [];
  productos: ProductoVista[] = [];
  productosFiltrados: ProductoVista[] = [];
  categoriaSeleccionadaId: number | null = null;
  busqueda = '';
  cargando = true;
  mensaje = '';

  // ─── Filtros avanzados ────────────────────────────────────────────────────
  ordenamiento: 'relevancia' | 'precio_asc' | 'precio_desc' | 'nombre_asc' = 'relevancia';
  precioMaximo = 500;
  precioMaximoTotal = 500;
  soloOfertas = false;

  // ─── Internos ─────────────────────────────────────────────────────────────
  private queryBusqueda = '';
  private queryCategoria = '';
  private destroy$ = new Subject<void>();

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
    private readonly catalogoService: CatalogoService,
    private readonly cartService: CartService,
    private readonly route: ActivatedRoute,
    private readonly algoliaService: AlgoliaService,
    private readonly favoritosService: FavoritosService,
    private readonly authService: AuthService,
    private readonly authModalService: AuthModalService
  ) {}

  ngOnInit(): void {
    // Suscripción a query params (búsqueda y categoría llegan desde el navbar)
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      this.queryBusqueda = params.get('q') ?? '';
      this.queryCategoria = params.get('categoria') ?? '';
      this.cargarDatos();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─── Carga de datos desde el backend ─────────────────────────────────────

  cargarDatos(): void {
    this.cargando = true;
    this.catalogoService.getCategorias().subscribe({
      next: (categorias) => {
        this.categorias = categorias;
        this.catalogoService.getProductos().subscribe({
          next: (productos) => {
            this.productos = productos.map((p) => this.mapProducto(p));
            // Calcular el precio máximo del catálogo para el slider
            this.precioMaximoTotal = Math.ceil(Math.max(...this.productos.map(p => p.precio), 500));
            this.precioMaximo = this.precioMaximoTotal;
            this.aplicarFiltrosDesdeUrl();
            this.cargando = false;
          },
          error: () => {
            this.mensaje = 'No se pudieron cargar los productos.';
            this.cargando = false;
          }
        });
      },
      error: () => {
        this.mensaje = 'No se pudieron cargar las categorías.';
        this.cargando = false;
      }
    });
  }

  // ─── Búsqueda con Algolia (vía ?q= desde el navbar) ───────────────────────

  /**
   * Ejecuta la búsqueda completa con Algolia para el término actual.
   * Reemplaza la lista de productos visibles con los resultados de Algolia,
   * filtrados por precio y ordenados según el criterio seleccionado.
   */
  buscar(): void {
    if (!this.busqueda.trim()) {
      // Sin texto: mostrar todos los productos locales
      this.productosFiltrados = [...this.productos];
      this.aplicarFiltros();
      return;
    }

    this.cargando = true;
    this.algoliaService.buscar(this.busqueda, {
      hitsPerPage: 100,
      categoriaId: this.categoriaSeleccionadaId ?? undefined
    }).subscribe({
      next: (resultados) => {
        // Convertir AlgoliaProducto → ProductoVista
        const productosAlgolia = resultados.map((hit) => this.mapAlgoliaHit(hit));
        let filtrados = this.filtrarPorPrecio(productosAlgolia);
        if (this.soloOfertas) {
          filtrados = filtrados.filter(p => p.descuentoInfo && p.descuentoInfo.percentage > 0 && !p.descuentoInfo.isExpired);
        }
        this.productosFiltrados = filtrados;
        this.ordenarProductos();
        this.cargando = false;
      },
      error: () => {
        // Fallback: búsqueda local si Algolia falla
        this.aplicarFiltros();
        this.cargando = false;
      }
    });
  }

  // ─── Filtros avanzados ────────────────────────────────────────────────────

  seleccionarCategoria(idCategoria: number | null): void {
    this.categoriaSeleccionadaId = idCategoria;
    // Si hay texto buscado, relanzar búsqueda con filtro de categoría
    if (this.busqueda.trim()) {
      this.buscar();
    } else {
      this.aplicarFiltros();
    }
  }

  aplicarOrdenamiento(): void {
    this.ordenarProductos();
  }

  toggleSoloOfertas(): void {
    this.soloOfertas = !this.soloOfertas;
    if (this.busqueda.trim()) {
      this.buscar();
    } else {
      this.aplicarFiltros();
    }
  }

  verOfertas(): void {
    this.busqueda = '';
    this.categoriaSeleccionadaId = null;
    this.queryBusqueda = '';
    this.queryCategoria = '';
    this.soloOfertas = true;
    this.precioMaximo = this.precioMaximoTotal;
    this.aplicarFiltros();
  }

  limpiarFiltros(): void {
    this.busqueda = '';
    this.categoriaSeleccionadaId = null;
    this.queryBusqueda = '';
    this.queryCategoria = '';
    this.ordenamiento = 'relevancia';
    this.precioMaximo = this.precioMaximoTotal;
    this.soloOfertas = false;
    this.productosFiltrados = [...this.productos];
  }

  hayFiltrosActivos(): boolean {
    return !!(
      this.busqueda ||
      this.categoriaSeleccionadaId !== null ||
      this.precioMaximo < this.precioMaximoTotal ||
      this.ordenamiento !== 'relevancia' ||
      this.soloOfertas
    );
  }

  /**
   * Aplica filtros locales (categoría + precio + ofertas) sobre la lista de productos.
   * Se usa cuando no hay texto de búsqueda (Algolia no es necesario).
   */
  aplicarFiltros(): void {
    const termino = this.busqueda.trim().toLowerCase();

    let filtrados = this.productos.filter((p) => {
      const coincideCategoria =
        this.categoriaSeleccionadaId === null ||
        this.categorias.find((c) => c.idCategoria === this.categoriaSeleccionadaId)?.nombre === p.categoriaNombre;

      const coincideBusqueda =
        !termino ||
        p.nombre.toLowerCase().includes(termino) ||
        p.descripcion.toLowerCase().includes(termino) ||
        p.categoriaNombre.toLowerCase().includes(termino);

      const coincideOferta =
        !this.soloOfertas ||
        (p.descuentoInfo && p.descuentoInfo.percentage > 0 && !p.descuentoInfo.isExpired);

      return coincideCategoria && coincideBusqueda && coincideOferta;
    });

    filtrados = this.filtrarPorPrecio(filtrados);
    this.productosFiltrados = filtrados;
    this.ordenarProductos();
  }

  private filtrarPorPrecio(lista: ProductoVista[]): ProductoVista[] {
    return lista.filter((p) => {
      const precioFinal = p.descuentoInfo && p.descuentoInfo.percentage > 0
        ? p.precio * (1 - p.descuentoInfo.percentage)
        : p.precio;
      return precioFinal <= this.precioMaximo;
    });
  }

  private ordenarProductos(): void {
    switch (this.ordenamiento) {
      case 'precio_asc':
        this.productosFiltrados = [...this.productosFiltrados].sort((a, b) => {
          const precioA = a.descuentoInfo && a.descuentoInfo.percentage > 0 ? a.precio * (1 - a.descuentoInfo.percentage) : a.precio;
          const precioB = b.descuentoInfo && b.descuentoInfo.percentage > 0 ? b.precio * (1 - b.descuentoInfo.percentage) : b.precio;
          return precioA - precioB;
        });
        break;
      case 'precio_desc':
        this.productosFiltrados = [...this.productosFiltrados].sort((a, b) => {
          const precioA = a.descuentoInfo && a.descuentoInfo.percentage > 0 ? a.precio * (1 - a.descuentoInfo.percentage) : a.precio;
          const precioB = b.descuentoInfo && b.descuentoInfo.percentage > 0 ? b.precio * (1 - b.descuentoInfo.percentage) : b.precio;
          return precioB - precioA;
        });
        break;
      case 'nombre_asc':
        this.productosFiltrados = [...this.productosFiltrados].sort((a, b) => a.nombre.localeCompare(b.nombre));
        break;
      default:
        // 'relevancia': el orden lo da Algolia, no se modifica
        break;
    }
  }

  // ─── Resaltado de texto buscado ───────────────────────────────────────────

  /**
   * Envuelve el término buscado en <mark> para resaltarlo en el nombre del producto.
   */
  resaltarTexto(texto: string): string {
    if (!this.busqueda.trim()) return texto;
    const termino = this.busqueda.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return texto.replace(new RegExp(`(${termino})`, 'gi'), '<mark>$1</mark>');
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

  agregarProducto(producto: ProductoVista): void {
    const productoApi: ProductoApi = {
      idProducto: producto.id,
      nombre: producto.nombre,
      descripcion: producto.descripcion,
      precioVenta: producto.precio,
      categoria: this.categorias.find((c) => c.nombre === producto.categoriaNombre) ?? null,
      fechaCaducidad: producto.fechaCaducidad,
      precioConDescuento: producto.descuentoInfo ? (producto.precio * (1 - producto.descuentoInfo.percentage)) : producto.precio,
      descuentoPorcentaje: producto.descuentoInfo ? producto.descuentoInfo.percentage : 0
    };

    this.cartService.add(productoApi);
    this.mensaje = `${producto.nombre} agregado al carrito.`;

    window.setTimeout(() => {
      if (this.mensaje.includes(producto.nombre)) {
        this.mensaje = '';
      }
    }, 2500);
  }

  trackByProductoId(_: number, producto: ProductoVista): number {
    return producto.id;
  }

  // ─── Mappers ──────────────────────────────────────────────────────────────

  private mapProducto(producto: ProductoApi): ProductoVista {
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
      descuentoInfo: descuento,
      requiereReceta: this.requiereRecetaMedica(producto.nombre)
    };
  }

  private mapAlgoliaHit(hit: AlgoliaProducto): ProductoVista {
    const categoriaKey = (hit.categoriaNombre ?? 'general').toLowerCase();
    const imagen = hit.imgUrl || this.imagenPorCategoria[categoriaKey] || 'assets/img/placeholder-pill.png';
    
    const fechaCad = this.getFecha(hit);
    const descuento = this.cartService.getDiscountInfo(hit);

    return {
      id: hit.idProducto,
      nombre: hit.nombre,
      descripcion: hit.descripcion ?? 'Producto del catálogo FarmaCode',
      precio: hit.precioConDescuento != null ? Number(hit.precioConDescuento) : Number(hit.precioVenta),
      precioOriginal: Number(hit.precioVenta),
      categoriaNombre: hit.categoriaNombre ?? 'General',
      imagen,
      etiquetaPromo: descuento.message || this.obtenerPromo(hit.nombre, hit.categoriaNombre ?? ''),
      colorPromo: descuento.message ? 'red' : ((hit.categoriaNombre ?? '').toUpperCase() === 'MEDICAMENTOS' ? 'red' : 'orange'),
      fechaCaducidad: fechaCad,
      descuentoInfo: descuento,
      requiereReceta: this.requiereRecetaMedica(hit.nombre)
    };
  }

  private obtenerPromo(nombre: string, categoriaNombre: string): string | undefined {
    const etiqueta = categoriaNombre.toLowerCase();
    if (etiqueta.includes('medic')) return 'Oferta especial';
    if (nombre.toLowerCase().includes('huggies') || nombre.toLowerCase().includes('pampers')) return 'Más vendido';
    if (etiqueta.includes('belleza')) return 'Nuevo';
    return undefined;
  }

  private requiereRecetaMedica(nombre: string): boolean {
    if (!nombre) return false;
    const n = nombre.toLowerCase();
    return n.includes('clonazepam') || n.includes('losartan') || n.includes('losartán');
  }



  /**
   * Lee `?q=` y `?categoria=` de la URL (provistos por el navbar) y
   * dispara la búsqueda en Algolia o el filtrado local según corresponda.
   */
  private aplicarFiltrosDesdeUrl(): void {
    this.busqueda = this.queryBusqueda;

    if (this.queryCategoria) {
      const categoriaNormalizada = this.queryCategoria.toLowerCase();
      const encontrada = this.categorias.find((c) => c.nombre.toLowerCase() === categoriaNormalizada);
      this.categoriaSeleccionadaId = encontrada?.idCategoria ?? null;
    } else {
      this.categoriaSeleccionadaId = null;
    }

    if (this.busqueda.trim()) {
      // Hay término de búsqueda: usar Algolia
      this.buscar();
    } else {
      // Sin término: filtrado local por categoría/precio
      this.aplicarFiltros();
    }
  }

  isFavorito(idProducto: number): boolean {
    return this.favoritosService.isFavorito(idProducto);
  }

  toggleFavorito(idProducto: number, event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }
    this.favoritosService.toggleFavorito(idProducto).subscribe();
  }
}
