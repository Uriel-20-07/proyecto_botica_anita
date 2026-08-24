import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, catchError, of } from 'rxjs';

/**
 * Interfaz que representa un resultado de búsqueda de Algolia.
 * Corresponde a los campos indexados en AlgoliaService.java del backend.
 */
export interface AlgoliaProducto {
  objectID: string;
  idProducto: number;
  nombre: string;
  descripcion?: string;
  precioVenta: number;
  precioConDescuento?: number;
  descuentoPorcentaje?: number;
  categoriaId?: number;
  categoriaNombre?: string;
  imgUrl?: string;
  stock?: number;
  fechaCaducidad?: any;
  fecha_caducidad?: any;
}

/**
 * Opciones para la búsqueda en Algolia.
 */
export interface AlgoliaBusquedaOpciones {
  hitsPerPage?: number;        // Resultados por página (default: 20)
  page?: number;               // Página a obtener (default: 0)
  categoriaId?: number;        // Filtrar por ID de categoría
  precioMax?: number;          // Filtrar por precio máximo
}

/**
 * Servicio Angular para consultar Algolia directamente desde el frontend.
 *
 * ⚠️ IMPORTANTE: Usa SOLO la Search API Key (pública).
 *    NUNCA uses la Admin API Key en el frontend.
 *
 * Configura tus claves en environment.ts:
 *   algolia: { appId: 'TU_APP_ID', searchKey: 'TU_SEARCH_KEY', index: 'productos' }
 */
@Injectable({
  providedIn: 'root'
})
export class AlgoliaService {

  // ── Reemplaza estos valores con los de tu dashboard de Algolia ─────────────
  private readonly APP_ID = 'V7WHMO8KTN';
  private readonly SEARCH_KEY = '785601020d9a743651f95d2ba0424b99';   // Search-only key (pública)
  private readonly INDEX = 'productos';
  // ──────────────────────────────────────────────────────────────────────────

  private get apiUrl(): string {
    return `https://${this.APP_ID}-dsn.algolia.net/1/indexes/${this.INDEX}/query`;
  }

  constructor(private readonly http: HttpClient) {}

  /**
   * Realiza una búsqueda en Algolia con el término y opciones indicados.
   *
   * @param termino  texto a buscar (puede tener errores tipográficos).
   * @param opciones opciones de paginación y filtros.
   * @returns Observable con la lista de productos encontrados.
   */
  buscar(termino: string, opciones: AlgoliaBusquedaOpciones = {}): Observable<AlgoliaProducto[]> {
    const { hitsPerPage = 20, page = 0, categoriaId, precioMax } = opciones;

    // Construir filtros de Algolia
    const filtros: string[] = [];
    if (categoriaId !== undefined) {
      filtros.push(`categoriaId:${categoriaId}`);
    }
    if (precioMax !== undefined) {
      filtros.push(`precioVenta <= ${precioMax}`);
    }

    const body = {
      query: termino,
      hitsPerPage,
      page,
      ...(filtros.length > 0 && { filters: filtros.join(' AND ') }),
      // Atributos para resaltado automático
      attributesToHighlight: ['nombre', 'descripcion'],
      // Ordenar por relevancia de Algolia por defecto
      typoTolerance: true  // Tolerancia a errores tipográficos habilitada
    };

    return this.http.post<{ hits: AlgoliaProducto[] }>(
      this.apiUrl,
      body,
      {
        headers: {
          'X-Algolia-Application-Id': this.APP_ID,
          'X-Algolia-API-Key': this.SEARCH_KEY,
          'Content-Type': 'application/json'
        }
      }
    ).pipe(
      map((response) => response.hits),
      catchError((error) => {
        console.error('[Algolia] Error en búsqueda:', error);
        return of([]); // Retorna lista vacía si Algolia falla (fallback al backend local)
      })
    );
  }
}
