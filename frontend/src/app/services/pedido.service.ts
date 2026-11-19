import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { finalize, shareReplay, tap } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

/**
 * Servicio para gestionar las peticiones HTTP relacionadas con los pedidos del usuario.
 * ProvidedIn: 'root' (Singleton compartido).
 *
 * Conecta con el endpoint /api/pedidos del backend. Requiere token JWT.
 *
 * Mantiene una caché en memoria del último listado para que el historial, el
 * perfil y el seguimiento reutilicen los mismos datos sin repetir la llamada.
 */
@Injectable({
  providedIn: 'root'
})
export class PedidoService {
  /** URL base para los endpoints de pedidos */
  private readonly apiUrl = `${environment.apiUrl}/api/pedidos`;

  /** Tiempo durante el cual el listado se considera fresco. */
  private readonly CACHE_TTL_MS = 60_000;

  private cachePedidos: any[] | null = null;
  private cacheTimestamp = 0;
  /** Token con el que se llenó la caché (evita mezclar datos entre usuarios). */
  private cacheToken: string | null = null;
  /** Petición en curso para deduplicar llamadas simultáneas. */
  private peticionEnCurso: Observable<any[]> | null = null;
  /** Generación de la caché: descarta respuestas de peticiones ya invalidadas. */
  private cacheVersion = 0;

  /**
   * @param http cliente HTTP de Angular.
   * @param authService servicio de autenticación para obtener el token JWT actual.
   */
  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  /**
   * Obtiene la lista de pedidos del usuario autenticado actual.
   *
   * Incluye cabecera (id, fecha, estado, total) y la lista de detalles
   * (producto, cantidad, precio histórico).
   *
   * Si hay datos en caché vigentes devuelve el mismo listado sin llamar al
   * backend. Usa `forzar = true` para refrescar tras un pago o cambio de estado.
   *
   * @returns Observable con array de pedidos del usuario.
   */
  obtenerPedidos(forzar = false): Observable<any[]> {
    const token = this.authService.getToken();
    const ahora = Date.now();

    if (this.cacheToken !== token) {
      this.limpiarCache();
      this.cacheToken = token;
    }

    if (!forzar && this.cachePedidos && (ahora - this.cacheTimestamp) < this.CACHE_TTL_MS) {
      return of(this.cachePedidos);
    }

    if (this.peticionEnCurso) {
      return this.peticionEnCurso;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    const version = this.cacheVersion;

    this.peticionEnCurso = this.http.get<any[]>(this.apiUrl, { headers }).pipe(
      tap(data => {
        // Si la caché se invalidó mientras la petición estaba en vuelo
        // (por ejemplo tras un pago), descartamos la respuesta vieja.
        if (version !== this.cacheVersion) return;
        this.cachePedidos = Array.isArray(data) ? data : [];
        this.cacheTimestamp = Date.now();
        this.cacheToken = token;
      }),
      finalize(() => {
        this.peticionEnCurso = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );

    return this.peticionEnCurso;
  }

  /** Devuelve el listado cacheado (aunque esté vencido) para pintar de inmediato. */
  pedidosDesdeCache(): any[] | null {
    const token = this.authService.getToken();
    if (this.cacheToken !== token) {
      this.limpiarCache();
      this.cacheToken = token;
      return null;
    }
    return this.cachePedidos;
  }

  /** Busca un pedido en la caché en memoria (para abrir el seguimiento al instante). */
  pedidoDesdeCache(idPedido: number): any | null {
    const pedidos = this.pedidosDesdeCache();
    if (!pedidos) return null;
    return pedidos.find((p: any) => p?.idPedido === idPedido) ?? null;
  }

  /**
   * Invalida la caché. Se llama tras registrar un pago para que el historial
   * muestre el pedido recién creado.
   */
  invalidarCache(): void {
    this.limpiarCache();
  }

  private limpiarCache(): void {
    this.cachePedidos = null;
    this.cacheTimestamp = 0;
    this.peticionEnCurso = null;
    this.cacheVersion++;
  }

  /**
   * Actualiza las coordenadas de entrega del pedido (cuando el usuario
   * ajusta manualmente el marcador en el mapa). Persiste en el backend
   * para que el email "en camino" use la ubicación corregida.
   */
  actualizarUbicacion(idPedido: number, lat: number, lng: number): Observable<any> {
    const token = this.authService.getToken();
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
    return this.http.put<any>(`${this.apiUrl}/${idPedido}/ubicacion`, { latitud: lat, longitud: lng }, { headers });
  }
}
