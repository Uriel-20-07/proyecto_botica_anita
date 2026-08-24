import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class FavoritosService {
  private readonly apiUrl = `${environment.apiUrl}/api/favoritos`;
  private readonly favoritesSubject = new BehaviorSubject<number[]>([]);
  readonly favorites$ = this.favoritesSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {
    // Sincronizar automáticamente al iniciar sesión o borrar al cerrar
    this.authService.token$.subscribe((token) => {
      if (token) {
        this.cargarFavoritosIds();
      } else {
        this.favoritesSubject.next([]);
      }
    });
  }

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  /**
   * Carga los IDs de los productos favoritos desde el servidor y los emite.
   */
  cargarFavoritosIds(): void {
    if (!this.authService.isAuthenticated()) return;

    this.http.get<number[]>(`${this.apiUrl}/ids`, { headers: this.getHeaders() }).subscribe({
      next: (ids) => {
        if (ids) {
          this.favoritesSubject.next(ids);
        }
      },
      error: (err) => {
        console.error('Error al cargar IDs de favoritos:', err);
      }
    });
  }

  /**
   * Agrega o elimina un producto de favoritos.
   * Si no está autenticado, no realiza la petición.
   */
  toggleFavorito(idProducto: number): Observable<boolean> {
    const url = `${this.apiUrl}/toggle?idProducto=${idProducto}`;
    return new Observable<boolean>((observer) => {
      if (!this.authService.isAuthenticated()) {
        observer.error('Usuario no autenticado');
        return;
      }

      this.http.post<boolean>(url, {}, { headers: this.getHeaders() }).subscribe({
        next: (isFav) => {
          // Volver a cargar la lista local
          this.cargarFavoritosIds();
          observer.next(isFav);
          observer.complete();
        },
        error: (err) => {
          console.error('Error al hacer toggle de favorito:', err);
          observer.error(err);
        }
      });
    });
  }

  /**
   * Retorna si un ID de producto está en la lista local de favoritos.
   */
  isFavorito(idProducto: number): boolean {
    return this.favoritesSubject.value.includes(idProducto);
  }

  /**
   * Devuelve la lista completa de productos favoritos en detalle.
   */
  obtenerFavoritosCompletos(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl, { headers: this.getHeaders() });
  }
}
