import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CartService } from '../../services/cart.service';
import { CatalogoService, CategoriaApi, ProductoApi, ConteosApi, FiltroCatalogo } from '../../services/catalogo.service';
import { FavoritosService } from '../../services/favoritos.service';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';
import { Subject, takeUntil, debounceTime } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../services/toast.service';
import { PromoCarouselComponent } from '../../components/promo-carousel/promo-carousel';

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
  subcategoriaId?: number | null;
  marcaId?: number | null;
  marcaNombre?: string | null;
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
  agotado?: boolean;
}

/**
 * Componente del catálogo.
 *
 * La barra de búsqueda con autocomplete de Algolia vive en el navbar
 * (ver NavbarComponent). Este componente:
 * - Recibe los query params `?q=`, `?categoria=` y `?marca=` desde la URL.
 * - Pide la página al backend con TODOS los filtros activos (categoría,
 *   subcategorías, marcas, búsqueda, ofertas, precio y orden): el backend
 *   filtra sobre los 400+ productos y recién después pagina de 30 en 30.
 * - Muestra los contadores del menú lateral con GET /api/productos/conteos
 *   (conteos reales de la BD, no de la página cargada).
 * - Resalta el término buscado en los nombres de los resultados.
 */
@Component({
  selector: 'app-catalogo',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, PromoCarouselComponent],
  templateUrl: './catalogo.html',
  styleUrls: ['./catalogo.css']
})
export class CatalogoComponent implements OnInit, OnDestroy {
   // Flag para mostrar/ocultar todo el flujo de recetas (ver environment.mostrarRecetas)
  readonly mostrarRecetas = environment.mostrarRecetas ?? false;
  // ─── Estado principal ─────────────────────────────────────────────────────
  categorias: CategoriaApi[] = [];
  /** Productos de la página actual, ya filtrados por el backend. */
  productosFiltrados: ProductoVista[] = [];
  categoriaSeleccionadaId: number | null = null;
  /** Subcategorías marcadas con casilla (vacío = se muestran todas las de la categoría). */
  subcategoriasSeleccionadas: number[] = [];
  /** Marcas marcadas con casilla (vacío = todas). */
  marcasSeleccionadas: number[] = [];
  busquedaMarca = '';
  busqueda = '';
  cargando = true;
  mensaje = '';

  // ─── Paginación del catálogo (30 productos por página) ────────────────────
  /** Índice de la página actual (base 0). */
  paginaActual = 0;
  /** Productos por página (el backend los devuelve de a 30). */
  readonly tamanoPagina = 30;
  /** Total de páginas disponibles en el backend. */
  totalPaginas = 1;
  /** Total de productos que cumplen los filtros (sin paginar). */
  totalElementos = 0;
  /** Para inicializar el slider de precio solo con la primera carga. */
  private precioMaximoInicializado = false;

  /** Contadores reales del menú lateral (subcategorías y marcas) + precio máximo global. */
  conteos: ConteosApi = { subcategorias: [], marcas: [], precioMaximo: 0 };
  /** Id de la última petición: descarta respuestas que llegaron tarde. */
  private peticionId = 0;
  /** Dispara la recarga (en página 0) cuando se suelta el slider de precio. */
  private readonly precioChange$ = new Subject<void>();

  // ─── Filtros avanzados ────────────────────────────────────────────────────
  ordenamiento: 'relevancia' | 'precio_asc' | 'precio_desc' | 'nombre_asc' = 'relevancia';
  precioMaximo = 500;
  precioMaximoTotal = 500;
  soloOfertas = false;

  // ─── Internos ─────────────────────────────────────────────────────────────
  private queryBusqueda = '';
  private queryCategoria = '';
  private queryMarca = '';
  private destroy$ = new Subject<void>();

  private normalizarTexto(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase();
}
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
    private readonly favoritosService: FavoritosService,
    private readonly authService: AuthService,
    private readonly authModalService: AuthModalService,
    private readonly toastService: ToastService
  ) {}

  ngOnInit(): void {
    // El slider de precio dispara muchas veces mientras se arrastra:
    // se agrupan en una sola recarga al dejar de moverlo.
    this.precioChange$
      .pipe(debounceTime(400), takeUntil(this.destroy$))
      .subscribe(() => this.recargar());

    // Suscripción a query params (búsqueda, categoría y marca llegan desde el navbar)
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      this.queryBusqueda = params.get('q') ?? '';
      this.queryCategoria = params.get('categoria') ?? '';
      this.queryMarca = params.get('marca') ?? '';
      this.cargarDatos();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─── Carga de datos desde el backend ─────────────────────────────────────

  /**
   * Carga inicial: categorías (necesarias para traducir ?categoria= de la URL
   * a un id), luego aplica los filtros de la URL, los contadores del menú
   * lateral y la primera página del catálogo.
   */
  cargarDatos(): void {
    this.cargando = true;
    this.catalogoService.getCategorias().subscribe({
      next: (categorias) => {
        this.categorias = categorias;
        this.aplicarStateDesdeUrl();
        this.cargarConteos();
        this.cargarPagina(0);
      },
      error: () => {
        this.mensaje = 'No se pudieron cargar las categorías.';
        this.cargando = false;
      }
    });
  }

  /**
   * Contadores reales del menú lateral (subcategorías, marcas) y precio
   * máximo del catálogo. Se piden al backend para que los números no
   * dependan de la página de 30 productos cargada en memoria.
   */
  private cargarConteos(): void {
    this.catalogoService.getConteos(this.categoriaSeleccionadaId).subscribe({
      next: (conteos) => {
        this.conteos = conteos;
        const maximoGlobal = Math.max(1, Math.ceil(conteos.precioMaximo || 500));
        const topeAnterior = this.precioMaximoTotal;
        this.precioMaximoTotal = maximoGlobal;
        // El slider sigue al tope salvo que el usuario lo haya bajado antes.
        if (!this.precioMaximoInicializado || this.precioMaximo >= topeAnterior) {
          this.precioMaximo = this.precioMaximoTotal;
          this.precioMaximoInicializado = true;
        }
      },
      error: () => {
        // Sin conteos se mantienen los valores por defecto de la vista.
      }
    });
  }

  /** Filtros activos, tal como los espera el backend. */
  private construirFiltros(): FiltroCatalogo {
    const filtros: FiltroCatalogo = {
      categoriaId: this.categoriaSeleccionadaId,
      subcategoriaIds: this.subcategoriasSeleccionadas,
      marcaIds: this.marcasSeleccionadas,
      orden: this.ordenamiento
    };
    if (this.busqueda.trim()) {
      filtros.search = this.busqueda.trim();
    }
    if (this.soloOfertas) {
      filtros.enOferta = true;
    }
    // Solo se envía el tope si el usuario lo bajó: si está en el máximo,
    // no filtra nada (así no se esconden productos más caros).
    if (this.precioMaximo < this.precioMaximoTotal) {
      filtros.precioMax = this.precioMaximo;
    }
    return filtros;
  }

  /**
   * Pide una página del catálogo al backend con los filtros activos.
   * El backend filtra sobre TODOS los productos y recién después pagina,
   * así que cambiar de filtro o de página siempre ve el listado correcto.
   *
   * @param page índice de página (base 0).
   */
  private cargarPagina(page: number): void {
    const peticion = ++this.peticionId;
    this.cargando = true;
    this.catalogoService
      .getProductosPaginado(page, this.tamanoPagina, this.construirFiltros())
      .subscribe({
        next: (pagina) => {
          if (peticion !== this.peticionId) return; // respuesta obsoleta
          this.paginaActual = pagina.number;
          this.totalPaginas = Math.max(1, pagina.totalPages);
          this.totalElementos = pagina.totalElements;
          const items = pagina.content.map((p) => this.mapProducto(p));
          this.productosFiltrados = items;
          this.cargando = false;
        },
        error: () => {
          if (peticion !== this.peticionId) return;
          this.mensaje = 'No se pudieron cargar los productos.';
          this.cargando = false;
        }
      });
  }

  /**
   * Cualquier cambio de filtro reinicia la paginación en la página 0
   * y vuelve a pedir el listado al backend.
   */
  private recargar(): void {
    this.cargarPagina(0);
    this.scrollAlInicio();
  }

  // ─── Controles de paginación ──────────────────────────────────────────────

  /**
   * Cambia de página: pide al backend los siguientes 30 productos y
   * sube suavemente al inicio de la lista.
   */
  cambiarPagina(page: number): void {
    if (this.cargando || page < 0 || page >= this.totalPaginas || page === this.paginaActual) {
      return;
    }
    this.cargarPagina(page);
    this.scrollAlInicio();
  }

  paginaAnterior(): void {
    this.cambiarPagina(this.paginaActual - 1);
  }

  paginaSiguiente(): void {
    this.cambiarPagina(this.paginaActual + 1);
  }

  /** Números de página visibles: ventana de 5 alrededor de la actual. */
  get paginasVisibles(): number[] {
    const total = this.totalPaginas;
    const actual = this.paginaActual + 1; // la vista es base 1
    let inicio = Math.max(1, actual - 2);
    let fin = Math.min(total, inicio + 4);
    inicio = Math.max(1, fin - 4);
    const paginas: number[] = [];
    for (let i = inicio; i <= fin; i++) {
      paginas.push(i);
    }
    return paginas;
  }

  /**
   * La paginación la calcula el backend para cualquier consulta (con o sin
   * texto buscado), así que solo se oculta si no hay resultados o hay una sola página.
   */
  get mostrarPaginacion(): boolean {
    return !this.cargando && this.productosFiltrados.length > 0 && this.totalPaginas > 1;
  }

  /** Texto del indicador: "Página X de Y". */
  get paginaActualTexto(): number {
    return this.paginaActual + 1;
  }

  private scrollAlInicio(): void {
    if (typeof window !== 'undefined' && window.scrollTo) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  // ─── Filtros (todo se manda al backend, en página 0) ─────────────────────

  /** Mueve el slider de precio: la recarga llega agrupada (debounce). */
  onPrecioInput(): void {
    this.precioChange$.next();
  }

  seleccionarCategoria(idCategoria: number | null): void {
    this.categoriaSeleccionadaId = idCategoria;
    this.subcategoriasSeleccionadas = [];
    this.marcasSeleccionadas = [];
    // Los conteos del menú lateral dependen de la categoría elegida.
    this.cargarConteos();
    this.recargar();
  }

  /** Marca o desmarca una subcategoría (casilla) y vuelve a pedir la página 0. */
  toggleSubcategoria(id: number): void {
    const i = this.subcategoriasSeleccionadas.indexOf(id);
    if (i >= 0) {
      this.subcategoriasSeleccionadas = this.subcategoriasSeleccionadas.filter((s) => s !== id);
    } else {
      this.subcategoriasSeleccionadas = [...this.subcategoriasSeleccionadas, id];
    }
    this.recargar();
  }

  limpiarSubcategorias(): void {
    this.subcategoriasSeleccionadas = [];
    this.recargar();
  }

  estaSubcategoriaSeleccionada(id: number): boolean {
    return this.subcategoriasSeleccionadas.includes(id);
  }

  /**
   * Cantidad de productos de una subcategoría, según los conteos reales
   * que devuelve GET /api/productos/conteos (no según la página cargada).
   */
  contarPorSubcategoria(id: number): number {
    return this.conteos.subcategorias.find((c) => c.id === id)?.cantidad ?? 0;
  }

  // ─── Filtro por marca ─────────────────────────────────────────────────────

  /**
   * Marcas con productos en la categoría elegida (o en todo el catálogo),
   * con su conteo real. Solo se filtra aquí el texto del buscador de marcas,
   * que es una lista corta y no necesita viajar al backend.
   */
  get marcasDisponibles(): { id: number; nombre: string; cantidad: number }[] {
    const termino = this.normalizarTexto(this.busquedaMarca);
    return this.conteos.marcas
      .filter((m) => !termino || this.normalizarTexto(m.nombre).includes(termino))
      .sort((a, b) => b.cantidad - a.cantidad || a.nombre.localeCompare(b.nombre));
  }

  trackByMarca(_: number, m: { id: number }): number {
    return m.id;
  }

  /** Marca o desmarca una marca (casilla) y vuelve a pedir la página 0. */
  toggleMarca(id: number): void {
    this.marcasSeleccionadas = this.marcasSeleccionadas.includes(id)
      ? this.marcasSeleccionadas.filter((m) => m !== id)
      : [...this.marcasSeleccionadas, id];
    this.recargar();
  }

  limpiarMarcas(): void {
    this.marcasSeleccionadas = [];
    this.recargar();
  }

  estaMarcaSeleccionada(id: number): boolean {
    return this.marcasSeleccionadas.includes(id);
  }

  aplicarOrdenamiento(): void {
    this.recargar();
  }

  toggleSoloOfertas(): void {
    this.soloOfertas = !this.soloOfertas;
    this.recargar();
  }

  /** "Ver ofertas" del carrusel: limpia todo y deja solo productos en oferta. */
  verOfertas(): void {
    this.busqueda = '';
    this.categoriaSeleccionadaId = null;
    this.subcategoriasSeleccionadas = [];
    this.marcasSeleccionadas = [];
    this.queryBusqueda = '';
    this.queryCategoria = '';
    this.queryMarca = '';
    this.soloOfertas = true;
    this.precioMaximo = this.precioMaximoTotal;
    this.cargarConteos();
    this.recargar();
  }

  limpiarFiltros(): void {
    this.busqueda = '';
    this.categoriaSeleccionadaId = null;
    this.subcategoriasSeleccionadas = [];
    this.marcasSeleccionadas = [];
    this.busquedaMarca = '';
    this.queryBusqueda = '';
    this.queryCategoria = '';
    this.queryMarca = '';
    this.ordenamiento = 'relevancia';
    this.precioMaximo = this.precioMaximoTotal;
    this.soloOfertas = false;
    this.cargarConteos();
    this.recargar();
  }

  hayFiltrosActivos(): boolean {
    return !!(
      this.busqueda ||
      this.categoriaSeleccionadaId !== null ||
      this.subcategoriasSeleccionadas.length > 0 ||
      this.marcasSeleccionadas.length > 0 ||
      this.precioMaximo < this.precioMaximoTotal ||
      this.ordenamiento !== 'relevancia' ||
      this.soloOfertas
    );
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
    if (producto.agotado) {
      return;
    }
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

    this.cartService.add(productoApi, () => {
      this.toastService.mostrar(`${producto.nombre} agregado al carrito`);   // <-- NUEVA
      this.mensaje = `${producto.nombre} agregado al carrito.`;

      window.setTimeout(() => {
        if (this.mensaje.includes(producto.nombre)) {
          this.mensaje = '';
        }
      }, 2500);
    });
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
      descripcion: producto.descripcion ?? 'Producto del catálogo BoticaAnita',
      precio: producto.precioConDescuento != null ? Number(producto.precioConDescuento) : Number(producto.precioVenta),
      precioOriginal: Number(producto.precioVenta),
      categoriaNombre,
      subcategoriaId: producto.subcategoria?.idSubcategoria ?? null,
      marcaId: producto.marca?.idMarca ?? null,
      marcaNombre: producto.marca?.nombre ?? null,
      imagen,
      etiquetaPromo: descuento.message || this.obtenerPromo(producto.nombre, categoriaNombre),
      colorPromo: descuento.message ? 'red' : (categoriaNombre === 'MEDICAMENTOS' ? 'red' : 'orange'),
      fechaCaducidad: fechaCad,
      descuentoInfo: descuento,
      requiereReceta: this.requiereRecetaMedica(producto.nombre),
      agotado: this.cartService.estaAgotado(producto)
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
   * Lee `?q=`, `?categoria=` y `?marca=` de la URL (provistos por el navbar)
   * y los deja cargados en el estado de filtros. No pide productos: eso lo
   * hace `cargarPagina(0)` justo después, mandando estos filtros al backend.
   */
  private aplicarStateDesdeUrl(): void {
    this.busqueda = this.queryBusqueda;
    this.subcategoriasSeleccionadas = [];

    // Marca que llega desde la página de Marcas (?marca=ID)
    const idMarca = Number(this.queryMarca);
    this.marcasSeleccionadas = this.queryMarca && !isNaN(idMarca) ? [idMarca] : [];

    if (this.queryCategoria) {
      const categoriaNormalizada = this.normalizarTexto(this.queryCategoria);
      const encontrada = this.categorias.find(
        (c) => this.normalizarTexto(c.nombre) === categoriaNormalizada
      );
      this.categoriaSeleccionadaId = encontrada?.idCategoria ?? null;
    } else {
      this.categoriaSeleccionadaId = null;
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
