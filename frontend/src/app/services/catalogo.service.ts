import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/**
 * Interfaz que representa una marca de producto en la API.
 * Las marcas son opcionales en los productos (campo imgUrl también opcional).
 */
export interface MarcaApi {
  idMarca: number;
  nombre: string;
  imgUrl?: string; // URL de imagen de la marca (opcional)
}

/**
 * Interfaz que representa una categoría de productos en la API.
 * Ejemplo: "Analgésicos", "Antibióticos", "Vitaminas", etc.
 */
export interface SubcategoriaApi {
  idSubcategoria: number;
  nombre: string;
  orden?: number;
}

export interface CategoriaApi {
  idCategoria: number;
  nombre: string;
  subcategorias?: SubcategoriaApi[]; // Subcategorías de la categoría (ordenadas)
}

/**
 * Interfaz que representa un producto del catálogo según la respuesta de la API.
 * Algunos campos son opcionales ya que el backend podría no enviarlos siempre.
 */
export interface ProductoApi {
  idProducto: number;
  nombre: string;
  descripcion?: string;      // Descripción del producto (opcional)
  precioVenta: number;
  precioOferta?: number;     // Precio de oferta (menor al regular), null si no aplica
  enOferta?: boolean;        // true si el producto está en oferta actualmente
  categoria?: CategoriaApi | null; // Categoría del producto (null si no tiene)
  subcategoria?: SubcategoriaApi | null; // Subcategoría del producto (null si no tiene)
  marca?: MarcaApi | null; // Marca del producto (null si no tiene)
  imgUrl?: string;           // URL de la imagen del producto (opcional)
  fechaCaducidad?: any;
  fecha_caducidad?: any;
  precioConDescuento?: number;
  descuentoPorcentaje?: number;
  agotado?: boolean;         // true si no hay stock físico o el producto está vencido
  stock?: number;            // stock físico (incluye unidades reservadas)
  stockEnCarrito?: number;   // unidades apartadas por carritos activos
  stockDisponible?: number;  // stock que realmente se puede vender (stock - stockEnCarrito)
  agotadoOnline?: boolean;   // true si no hay stock disponible para la venta online
  estadoStock?: 'DISPONIBLE' | 'PARCIALMENTE_RESERVADO' | 'RESERVADO' | 'AGOTADO' | 'VENCIDO';
  /** Línea con tallas a la que pertenece (null/undefined = producto sin variantes). */
  grupoVariantes?: string | null;
  /** Nombre de la línea completa, p. ej. "Pañales Huggies Active Sec". */
  grupoNombre?: string | null;
  /** Talla de este producto dentro de su línea ("G", "XG"...). */
  varianteEtiqueta?: string | null;
  /** true = es el producto que da la cara por su línea en el catálogo. */
  esRepresentante?: boolean;
}

/** Una talla de una línea de producto (GET /api/productos/{id}/variantes). Cada talla es un producto con su propio stock. */
export interface VarianteApi {
  idProducto: number;
  etiqueta: string;
  nombre: string;
  precio: number;         // lo que paga el cliente (con oferta)
  precioRegular: number;  // sin oferta
  disponible: boolean;    // false si esa talla está agotada o vencida
  imgUrl?: string | null;
}

/**
 * Respuesta paginada del endpoint GET /api/productos/paginado.
 * Reproduce la estructura del DTO PageDTO del backend.
 */
export interface PaginaApi<T> {
  content: T[];        // productos de la página actual (30)
  totalPages: number;  // total de páginas
  totalElements: number; // total de productos (sin paginar)
  number: number;      // índice de la página actual (base 0)
  size: number;        // tamaño de página solicitado
}

/**
 * Filtros del catálogo. Se envían como query params al backend, que filtra
 * sobre TODOS los productos y recién después pagina de 30 en 30.
 * Todos los campos son opcionales: si no se envían, no filtran nada.
 */
export interface FiltroCatalogo {
  categoriaId?: number | null;
  subcategoriaIds?: number[];
  marcaIds?: number[];
  search?: string;
  enOferta?: boolean;
  precioMax?: number;
  /** relevancia | precio_asc | precio_desc | nombre_asc */
  orden?: string;
}

/** Conteo de productos de una subcategoría o marca. */
export interface ConteoApi {
  id: number;
  nombre: string;
  cantidad: number;
}

/**
 * Respuesta de GET /api/productos/conteos: contadores reales del menú
 * lateral (calculados en la BD, no sobre la página cargada) y el precio
 * más alto del catálogo para el slider de precio.
 */
export interface ConteosApi {
  subcategorias: ConteoApi[];
  marcas: ConteoApi[];
  precioMaximo: number;
}

/**
 * Servicio para consumir la API del catálogo de productos, categorías y marcas.
 * Singleton compartido en toda la aplicación (providedIn: 'root').
 * 
 * Conecta con los endpoints públicos del backend:
 * - GET /api/productos           → lista de todos los productos.
 * - GET /api/productos/paginado  → catálogo paginado + filtros (30 por página).
 * - GET /api/productos/conteos   → conteos por subcategoría/marca + precio máximo.
 * - GET /api/categorias          → lista de categorías disponibles.
 * - GET /api/marcas              → lista de marcas disponibles.
 */
@Injectable({
  providedIn: 'root'
})
export class CatalogoService {
  /** URL del endpoint de productos del backend. */
  private readonly productosUrl = `${environment.apiUrl}/api/productos`;

  /** URL del endpoint de categorías del backend. */
  private readonly categoriasUrl = `${environment.apiUrl}/api/categorias`;

  /** URL del endpoint de marcas del backend. */
  private readonly marcasUrl = `${environment.apiUrl}/api/marcas`;

  /**
   * @param http cliente HTTP de Angular para realizar las peticiones GET.
   */
  constructor(private readonly http: HttpClient) {}

  /**
   * Obtiene la lista completa de productos del catálogo.
   * Se mantiene para componentes que necesitan todo el catálogo
   * (carrousel, detalle, marcas). La página del catálogo usa
   * {@link getProductosPaginado}.
   *
   * @returns Observable con array de ProductoApi.
   */
  getProductos(): Observable<ProductoApi[]> {
    return this.http.get<ProductoApi[]>(this.productosUrl);
  }

  /** Un producto por ID (cualquier talla, haya o no dado la cara en el catálogo). */
  getProducto(id: number): Observable<ProductoApi> {
    return this.http.get<ProductoApi>(`${this.productosUrl}/${id}`);
  }

  /** Tallas de la línea del producto, de menor a mayor. Lista vacía si el producto no tiene variantes. */
  getVariantes(id: number): Observable<VarianteApi[]> {
    return this.http
      .get<VarianteApi[]>(`${this.productosUrl}/${id}/variantes`)
      .pipe(catchError(() => of([] as VarianteApi[])));
  }

  /**
   * Obtiene una página del catálogo (30 productos por defecto) ya filtrada
   * por el backend: los filtros se aplican sobre todos los productos y
   * recién después se pagina, así el listado no depende de lo que haya
   * cargado en memoria.
   *
   * @param page índice de página, base 0.
   * @param size cantidad de productos por página.
   * @param filtros filtros activos (categoría, subcategorías, marcas,
   *                búsqueda, ofertas, precio máximo y orden).
   * @returns Observable con la página (content, totalPages, totalElements, number, size).
   */
  getProductosPaginado(page = 0, size = 30, filtros: FiltroCatalogo = {}): Observable<PaginaApi<ProductoApi>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));

    if (filtros.categoriaId != null) {
      params = params.set('categoriaId', String(filtros.categoriaId));
    }
    for (const id of filtros.subcategoriaIds ?? []) {
      params = params.append('subcategoriaId', String(id));
    }
    for (const id of filtros.marcaIds ?? []) {
      params = params.append('marcaId', String(id));
    }
    if (filtros.search && filtros.search.trim()) {
      params = params.set('search', filtros.search.trim());
    }
    if (filtros.enOferta) {
      params = params.set('enOferta', 'true');
    }
    if (filtros.precioMax != null) {
      params = params.set('precioMax', String(filtros.precioMax));
    }
    if (filtros.orden && filtros.orden !== 'relevancia') {
      params = params.set('orden', filtros.orden);
    }

    return this.http.get<PaginaApi<ProductoApi>>(`${this.productosUrl}/paginado`, { params });
  }

  /**
   * Conteos reales del catálogo (productos por subcategoría y por marca)
   * calculados en la BD, más el precio más alto del catálogo.
   *
   * @param categoriaId categoría opcional para acotar los conteos de marcas.
   * @returns Observable con los conteos y el precio máximo global.
   */
  getConteos(categoriaId?: number | null): Observable<ConteosApi> {
    let params = new HttpParams();
    if (categoriaId != null) {
      params = params.set('categoriaId', String(categoriaId));
    }
    return this.http.get<ConteosApi>(`${this.productosUrl}/conteos`, { params });
  }

  /**
   * Obtiene la lista de categorías de productos disponibles.
   * Se usa para los filtros del catálogo y del carrito.
   *
   * @returns Observable con array de CategoriaApi.
   */
  getCategorias(): Observable<CategoriaApi[]> {
    return this.http.get<CategoriaApi[]>(this.categoriasUrl);
  }

  /**
   * Obtiene la lista de marcas de productos disponibles.
   * Se usa en la página de Marcas para mostrar las marcas disponibles.
   *
   * @returns Observable con array de MarcaApi.
   */
  getMarcas(): Observable<MarcaApi[]> {
    return this.http.get<MarcaApi[]>(this.marcasUrl);
  }
}