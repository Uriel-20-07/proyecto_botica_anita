import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { ProductoApi } from './catalogo.service';
import { AuthService } from './auth.service';
import { AuthModalService } from './auth-modal.service';
import { environment } from '../../environments/environment';

/**
 * Interfaz que representa un ítem (línea) dentro del carrito de compras.
 * Refleja la estructura de DetalleCarrito del backend.
 */
export interface CartItem {
  idDetalleCarrito?: number;  // ID del detalle en BD (opcional: no existe antes de guardar)
  producto: ProductoApi;       // Datos completos del producto
  cantidad: number;            // Unidades del producto en el carrito
}

/**
 * Servicio del carrito de compras. Singleton compartido en toda la app.
 * 
 * Funcionalidades:
 * - Sincroniza el carrito con el backend (cuando el usuario está autenticado).
 * - Mantiene el estado reactivo del carrito con BehaviorSubject.
 * - Persiste el carrito en localStorage como caché local.
 * - Escucha cambios de autenticación para cargar/limpiar el carrito.
 * - Si el usuario no está autenticado al intentar agregar, abre el modal de login.
 * 
 * Estrategia de sincronización:
 * - Autenticado → las operaciones van al backend (fuente de verdad).
 * - No autenticado → el carrito se limpia (no hay carrito local sin login).
 */
@Injectable({
  providedIn: 'root'
})
export class CartService {
  /** URL base de la API del carrito en el backend. */
  private readonly apiUrl = `${environment.apiUrl}/api/carrito`;

  /** Clave usada para persistir el carrito en localStorage. */
  private readonly storageKey = 'carrito';

  /** BehaviorSubject con la lista actual de ítems del carrito. */
  private readonly itemsSubject = new BehaviorSubject<CartItem[]>([]);

  /** Observable público al que los componentes se suscriben para mostrar el carrito. */
  readonly items$ = this.itemsSubject.asObservable();

  /**
   * Constructor: escucha cambios en el token de autenticación para sincronizar el carrito.
   * 
   * Cuando el usuario inicia sesión (token existe): carga el carrito desde el backend.
   * Cuando el usuario cierra sesión (token null): limpia el estado local del carrito.
   */
  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService,
    private readonly authModalService: AuthModalService
  ) {
    // Escuchar cambios de autenticación para cargar o limpiar el carrito
    this.authService.token$.subscribe((token) => {
      if (token) {
        this.cargarCarritoServidor(); // Usuario logueado: sincronizar con backend
      } else {
        this.clearLocalState();       // Usuario deslogueado: limpiar carrito local
      }
    });
  }

  /**
   * Construye los headers HTTP con el token JWT de autorización.
   * Se incluye en todas las peticiones al endpoint /api/carrito (requiere autenticación).
   *
   * @returns HttpHeaders con el header "Authorization: Bearer <token>".
   */
  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  /**
   * Carga el carrito desde el servidor y actualiza el estado local.
   * Se llama automáticamente al iniciar sesión.
   * En caso de error (backend no disponible), usa el carrito almacenado en localStorage.
   */
  private cargarCarritoServidor(): void {
    this.http.get<any>(this.apiUrl, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        if (carrito && carrito.detalles) {
          this.itemsSubject.next(carrito.detalles);          // Actualizar estado reactivo
          this.saveItemsToStorage(carrito.detalles);          // Guardar caché local
        }
      },
      error: (err) => {
        console.error('Error al cargar el carrito del servidor:', err);
        // Fallback: usar los datos del localStorage si el servidor no responde
        this.itemsSubject.next(this.loadItemsFromStorage());
      }
    });
  }

  /**
   * Agrega un producto al carrito del usuario autenticado.
   * 
   * Si el usuario NO está autenticado: abre el modal de login en lugar de agregar.
   * Si el producto ya está en el carrito: el backend incrementa la cantidad en 1.
   * Si es nuevo: el backend crea una nueva línea de detalle.
   *
   * @param producto datos del producto a agregar al carrito.
   */
  add(producto: ProductoApi): void {
    // Verificar autenticación antes de operar
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login'); // Abrir modal de login
      return;
    }

    const url = `${this.apiUrl}/agregar?idProducto=${producto.idProducto}&cantidad=1`;
    this.http.post<any>(url, {}, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        if (carrito && carrito.detalles) {
          this.itemsSubject.next(carrito.detalles);
          this.saveItemsToStorage(carrito.detalles);
        }
      },
      error: (err) => {
        console.error('Error al agregar producto al carrito:', err);
      }
    });
  }

  /**
   * Agrega un producto al carrito especificando cantidad.
   */
  addWithQty(idProducto: number, cantidad: number): void {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }

    const url = `${this.apiUrl}/agregar?idProducto=${idProducto}&cantidad=${cantidad}`;
    this.http.post<any>(url, {}, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        if (carrito && carrito.detalles) {
          this.itemsSubject.next(carrito.detalles);
          this.saveItemsToStorage(carrito.detalles);
        }
      },
      error: (err) => {
        console.error('Error al agregar producto al carrito:', err);
      }
    });
  }

  /**
   * Disminuye en 1 la cantidad de un producto en el carrito.
   * Si la cantidad llega a 0, el backend elimina la línea del carrito.
   *
   * @param productoId ID del producto a disminuir.
   */
  decrease(productoId: number): void {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }

    const url = `${this.apiUrl}/disminuir?idProducto=${productoId}`;
    this.http.post<any>(url, {}, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        if (carrito && carrito.detalles) {
          this.itemsSubject.next(carrito.detalles);
          this.saveItemsToStorage(carrito.detalles);
        }
      },
      error: (err) => {
        console.error('Error al disminuir producto del carrito:', err);
      }
    });
  }

  /**
   * Elimina completamente un producto del carrito (sin importar la cantidad).
   *
   * @param productoId ID del producto a eliminar.
   */
  remove(productoId: number): void {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }

    const url = `${this.apiUrl}/eliminar?idProducto=${productoId}`;
    this.http.delete<any>(url, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        if (carrito && carrito.detalles) {
          this.itemsSubject.next(carrito.detalles);
          this.saveItemsToStorage(carrito.detalles);
        }
      },
      error: (err) => {
        console.error('Error al eliminar producto del carrito:', err);
      }
    });
  }

  /**
   * Vacía completamente el carrito del usuario.
   * Si no está autenticado, simplemente limpia el estado local.
   * Se llama automáticamente después de un pago exitoso.
   */
  clear(): void {
    if (!this.authService.isAuthenticated()) {
      this.clearLocalState(); // Sin sesión: solo limpiar localmente
      return;
    }

    const url = `${this.apiUrl}/vaciar`;
    this.http.delete<any>(url, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        if (carrito && carrito.detalles) {
          this.itemsSubject.next(carrito.detalles);
          this.saveItemsToStorage(carrito.detalles);
        }
      },
      error: (err) => {
        console.error('Error al vaciar el carrito:', err);
      }
    });
  }

  /**
   * Limpia el estado local del carrito (localStorage y BehaviorSubject).
   * Se usa al cerrar sesión para que el carrito no persista entre usuarios.
   */
  private clearLocalState(): void {
    this.saveItemsToStorage([]);
    this.itemsSubject.next([]);
  }

  /**
   * Retorna el total de unidades en el carrito (suma de cantidades de todos los ítems).
   * Se muestra en el ícono del carrito en la navbar.
   *
   * @returns número total de unidades en el carrito.
   */
  getCount(): number {
    return this.itemsSubject.value.reduce((total, item) => total + item.cantidad, 0);
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

  getDiscountInfo(producto: any): {
    percentage: number;
    message: string;
    isExpired: boolean;
  } {
    if (!producto) return { percentage: 0, message: '', isExpired: false };
    
    // Leer valores precalculados de la base de datos
    const percentage = producto.descuentoPorcentaje != null ? Number(producto.descuentoPorcentaje) : 0;
    
    // Verificar si expiró comparando la fecha de caducidad
    const fechaCad = this.getFecha(producto);
    let isExpired = false;
    if (fechaCad) {
      const expDate = new Date(fechaCad);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      expDate.setHours(0, 0, 0, 0);
      isExpired = expDate < today;
    }

    let message = '';
    if (isExpired) {
      message = 'PRODUCTO VENCIDO';
    } else if (percentage === 0.30) {
      message = 'Liquidación 30% dscto.';
    } else if (percentage === 0.10) {
      message = 'Oferta 10% dscto.';
    } else if (percentage === 0.05) {
      message = 'Promo 5% dscto.';
    } else if (percentage > 0) {
      message = `Oferta ${Math.round(percentage * 100)}% dscto.`;
    }

    return { percentage, message, isExpired };
  }

  getFinalPrice(producto: any): number {
    if (!producto) return 0;
    const discountInfo = this.getDiscountInfo(producto);
    if (discountInfo.isExpired) return 0;
    return producto.precioConDescuento != null ? Number(producto.precioConDescuento) : Number(producto.precioVenta);
  }

  /**
   * Calcula el subtotal del carrito (precio × cantidad de cada ítem).
   *
   * @returns suma total del carrito como número.
   */
  getTotal(): number {
    return this.itemsSubject.value.reduce((total, item) => {
      const discountInfo = this.getDiscountInfo(item.producto);
      if (discountInfo.isExpired) return total;
      return total + this.getFinalPrice(item.producto) * item.cantidad;
    }, 0);
  }

  /**
   * Retorna una copia del array de ítems del carrito.
   * Se usa en componentes que necesitan iterar los ítems sin suscribirse.
   *
   * @returns copia del array de CartItem.
   */
  getItems(): CartItem[] {
    return [...this.itemsSubject.value]; // Spread para retornar copia (inmutabilidad)
  }

  /**
   * Carga los ítems del carrito desde localStorage.
   * Usado como fallback cuando el backend no está disponible.
   *
   * @returns array de CartItem del localStorage, o [] si está vacío o inválido.
   */
  private loadItemsFromStorage(): CartItem[] {
    const stored = localStorage.getItem(this.storageKey);
    if (!stored) return [];
    try {
      return JSON.parse(stored) as CartItem[];
    } catch {
      return []; // Si el JSON está corrupto, retornar carrito vacío
    }
  }

  /**
   * Guarda los ítems del carrito en localStorage como JSON.
   *
   * @param items array de CartItem a persistir.
   */
  private saveItemsToStorage(items: CartItem[]): void {
    localStorage.setItem(this.storageKey, JSON.stringify(items));
  }
}