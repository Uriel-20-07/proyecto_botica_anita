import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { ProductoApi } from './catalogo.service';
import { AuthService } from './auth.service';
import { AuthModalService } from './auth-modal.service';
import { ToastService } from './toast.service';
import { environment } from '../../environments/environment';

/** Producto cuya reserva de 2 h venció y volvió al stock. */
export interface ItemExpirado {
  idProducto: number;
  nombre: string;
  cantidad: number;
  fechaExpiracion?: string;
  mensaje?: string;
}

/** Presentación (blíster, caja...) guardada en una línea del carrito (viene de DetalleCarrito del backend). */
export interface PresentacionLinea {
  idPresentacion: number;
  etiqueta: string;
  unidades: number;             // unidades base (tabletas) que descuenta UNA presentación
  precioVenta?: number | null;  // precio propio; null = usa el precio del producto (la caja)
  esPredeterminada?: boolean;
}

/** Presentación lista para el selector de la tienda: GET /api/productos/{id}/presentaciones. */
export interface PresentacionApi {
  idPresentacion: number;
  etiqueta: string;
  unidades: number;
  precio: number;          // lo que paga el cliente por UNA unidad (ya con la oferta del producto)
  precioRegular: number;   // precio sin oferta (para mostrarlo tachado)
  esPredeterminada: boolean;
  disponible: boolean;     // false si el stock no alcanza ni para una unidad de esta presentación
  maxPorPedido: number;    // máximo que se puede pedir de una vez (stock y límite de 10)
}

/**
 * Interfaz que representa un ítem (línea) dentro del carrito de compras.
 * Refleja la estructura de DetalleCarrito del backend.
 */
export interface CartItem {
  idDetalleCarrito?: number;  // ID del detalle en BD (opcional: no existe antes de guardar)
  producto: ProductoApi;       // Datos completos del producto
  cantidad: number;            // Unidades del producto en el carrito
  presentacion?: PresentacionLinea | null; // null/undefined: producto sin presentaciones
  estado?: 'ACTIVA' | 'EXPIRADA' | 'CONFIRMADA' | 'LIBERADA';
  fechaCreacion?: string;      // UTC
  fechaExpiracion?: string;    // UTC: 2 h después de entrar al carrito
  segundosRestantes?: number;  // calculado por el backend al responder
  reservaVencida?: boolean;    // calculado por el backend al responder
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
    private readonly authModalService: AuthModalService,
    private readonly toastService: ToastService
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
   * Aplica los detalles devueltos por el servidor SIN alterar el orden en pantalla.
   *
   * Estrategia:
   * - Conserva el orden actual de los ítems locales que siguen en el servidor
   *   (solo se actualiza su cantidad/objeto).
   * - Descarta los ítems que el servidor ya no devuelve (quitados/vaciado).
   * - Agrega al final los ítems nuevos (agregados desde otra vista), en el orden
   *   en que el servidor los envía.
   *
   * Así, aunque el backend reordene su respuesta, la lista no "salta" de posición.
   */
  private aplicarDetallesDelServidor(detalles: CartItem[]): void {
    // Una línea = producto + presentación (una caja y un blíster del mismo producto son dos líneas).
    const pendientes = new Map<string, CartItem>();
    for (const detalle of detalles) {
      if (detalle?.producto?.idProducto != null) pendientes.set(this.claveLinea(detalle), detalle);
    }

    const ordenados: CartItem[] = [];
    for (const item of this.itemsSubject.value) {
      const clave = this.claveLinea(item);
      if (item?.producto?.idProducto != null && pendientes.has(clave)) {
        ordenados.push(pendientes.get(clave)!);
        pendientes.delete(clave);
      }
    }
    // Ítems nuevos (no estaban en pantalla): al final, en orden del servidor.
    for (const detalle of pendientes.values()) ordenados.push(detalle);

    this.itemsSubject.next(ordenados);
    this.saveItemsToStorage(ordenados);
  }

  /** Identidad de una línea del carrito: producto + presentación. */
  claveLinea(item: CartItem): string {
    return `${item?.producto?.idProducto ?? 0}:${item?.presentacion?.idPresentacion ?? 0}`;
  }

  /**
   * Procesa la respuesta completa del carrito:
   * - Avisa al usuario si alguna reserva de 2 h venció.
   * - Actualiza la lista de ítems.
   */
  private procesarRespuestaCarrito(carrito: any): void {
    if (!carrito) return;
    this.notificarExpirados(carrito.expirados);
    if (carrito.detalles) {
      this.aplicarDetallesDelServidor(carrito.detalles);
    }
  }

  /**
   * Muestra un aviso por cada reserva que venció y volvió al stock.
   * El backend borra esas líneas al responder, así que solo se avisa una vez.
   */
  private notificarExpirados(expirados: ItemExpirado[] | null | undefined): void {
    if (!Array.isArray(expirados) || expirados.length === 0) return;

    if (expirados.length === 1) {
      const e = expirados[0];
      this.toastService.mostrar(
        `La reserva de "${e.nombre}" venció y volvió al stock.`,
        'error',
        5000
      );
      return;
    }

    const nombres = expirados.map((e) => e.nombre).join(', ');
    this.toastService.mostrar(
      `Las reservas de ${nombres} vencieron y volvieron al stock.`,
      'error',
      5500
    );
  }

  /**
   * Vuelve a pedir el carrito al servidor. Sirve para que el backend
   * elimine las reservas vencidas y avise al usuario.
   */
  refrescarCarrito(): void {
    if (!this.authService.isAuthenticated()) return;
    this.cargarCarritoServidor();
  }

  /**
   * Carga el carrito desde el servidor y actualiza el estado local.
   * Se llama automáticamente al iniciar sesión.
   * En caso de error (backend no disponible), usa el carrito almacenado en localStorage.
   */
  private cargarCarritoServidor(): void {
    this.http.get<any>(this.apiUrl, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        this.procesarRespuestaCarrito(carrito);
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
  add(producto: ProductoApi, onSuccess?: () => void, idPresentacion?: number | null): void {
    // Verificar autenticación antes de operar
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login'); // Abrir modal de login
      return;
    }

    const url = `${this.apiUrl}/agregar?idProducto=${producto.idProducto}&cantidad=1${this.paramPresentacion(idPresentacion)}`;
    this.http.post<any>(url, {}, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        this.procesarRespuestaCarrito(carrito);
        onSuccess?.();
      },
      error: (err) => this.manejarErrorCarrito(err, 'agregar el producto al carrito')
    });
  }

  /**
   * Maneja los errores del carrito de forma visible para el usuario.
   * - 401/403: el token venció o es inválido (el JWT dura 1 hora) → se cierra la sesión
   *   y se abre el login, en lugar de fallar en silencio.
   * - Otros errores: se avisa que no se pudo completar la acción.
   */
  private manejarErrorCarrito(err: any, accion: string): void {
    console.error(`Error al ${accion}:`, err);
    const mensajeBackend = err?.error?.error || err?.error?.mensaje;

    if (err?.status === 401 || err?.status === 403) {
      this.authService.logout();
      this.authModalService.open('login');
      this.toastService.mostrar('Tu sesión venció. Inicia sesión nuevamente para continuar.', 'error', 5000);
    } else if (err?.status === 0) {
      this.toastService.mostrar(
        'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución (puerto 8080).',
        'error',
        5000
      );
    } else if (mensajeBackend) {
      this.toastService.mostrar(String(mensajeBackend), 'error', 5000);
    } else {
      this.toastService.mostrar(`No se pudo ${accion}. Intenta nuevamente.`, 'error', 4000);
    }
  }

  /**
   * Agrega un producto al carrito especificando cantidad.
   *
   * A diferencia de add(), este método NO maneja el error internamente:
   * devuelve el Observable para que el consumidor (el chatbot) decida
   * cómo mostrarlo. En caso de no estar autenticado, abre el modal de
   * login y devuelve un Observable que emite un error controlado.
   *
   * @param idProducto ID del producto a agregar.
   * @param cantidad   número de unidades a agregar.
   * @returns Observable con el carrito actualizado si la operación tiene éxito.
   */
  addWithQty(idProducto: number, cantidad: number, idPresentacion?: number | null): Observable<any> {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return throwError(() => ({ status: 401, mensaje: 'Debes iniciar sesión para agregar productos al carrito.' }));
    }

    const url = `${this.apiUrl}/agregar?idProducto=${idProducto}&cantidad=${cantidad}${this.paramPresentacion(idPresentacion)}`;
    return this.http.post<any>(url, {}, { headers: this.getHeaders() }).pipe(
      tap((carrito) => {
        this.procesarRespuestaCarrito(carrito);
      })
    );
  }

  /**
   * Disminuye en 1 la cantidad de un producto en el carrito.
   * Si la cantidad llega a 0, el backend elimina la línea del carrito.
   *
   * @param productoId ID del producto a disminuir.
   */
  decrease(productoId: number, idPresentacion?: number | null): void {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }

    const url = `${this.apiUrl}/disminuir?idProducto=${productoId}${this.paramPresentacion(idPresentacion)}`;
    this.http.post<any>(url, {}, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        this.procesarRespuestaCarrito(carrito);
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
  remove(productoId: number, idPresentacion?: number | null): void {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('login');
      return;
    }

    const url = `${this.apiUrl}/eliminar?idProducto=${productoId}${this.paramPresentacion(idPresentacion)}`;
    this.http.delete<any>(url, { headers: this.getHeaders() }).subscribe({
      next: (carrito) => {
        this.procesarRespuestaCarrito(carrito);
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
        this.procesarRespuestaCarrito(carrito);
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

  /**
   * Cantidad actual de un producto específico dentro del carrito.
   * Usado por el detalle del producto para aplicar el límite de 10 unidades
   * por producto restando lo que ya se agregó.
   *
   * @param idProducto ID del producto a consultar.
   * @returns unidades de ese producto ya presentes en el carrito (0 si no está).
   */
  cantidadEnCarrito(idProducto: number, idPresentacion?: number | null): number {
    if (idProducto == null) return 0;
    return this.itemsSubject.value.reduce((total, item) => {
      if (item?.producto?.idProducto !== idProducto) return total;
      // Con presentación elegida solo cuenta la línea de esa presentación (el límite de 10 es por línea).
      if (idPresentacion != null && item.presentacion?.idPresentacion !== idPresentacion) return total;
      return total + (Number(item.cantidad) || 0);
    }, 0);
  }

  /** Parámetro opcional de URL con la presentación elegida. */
  private paramPresentacion(idPresentacion?: number | null): string {
    return idPresentacion != null ? `&idPresentacion=${idPresentacion}` : '';
  }

  /**
   * Presentaciones (blíster, caja...) de un producto para el selector. Lista vacía = el producto se vende
   * normalmente, sin presentaciones. Si la consulta falla también devuelve lista vacía.
   */
  getPresentaciones(idProducto: number): Observable<PresentacionApi[]> {
    return this.http
      .get<PresentacionApi[]>(`${environment.apiUrl}/api/productos/${idProducto}/presentaciones`)
      .pipe(catchError(() => of([] as PresentacionApi[])));
  }

  /** Precio SIN oferta de UNA unidad de la línea (el de su presentación o, si no tiene, el del producto). */
  precioRegularDeLinea(item: CartItem): number {
    const propio = item?.presentacion?.precioVenta;
    return propio != null ? Number(propio) : Number(item?.producto?.precioVenta) || 0;
  }

  /**
   * Precio que paga el cliente por UNA unidad de la línea. Igual que el backend: sin precio propio se usa el
   * del producto (con su oferta); con precio propio (blíster) se le aplica el mismo % de descuento del producto.
   */
  precioFinalDeLinea(item: CartItem): number {
    const producto: any = item?.producto;
    const propio = item?.presentacion?.precioVenta;
    if (propio == null) return this.getFinalPrice(producto);
    const venta = Number(producto?.precioVenta) || 0;
    const conDescuento = producto?.precioConDescuento != null ? Number(producto.precioConDescuento) : null;
    const hayOferta = venta > 0 && conDescuento != null && conDescuento < venta;
    if (!hayOferta) return Number(propio);
    return Math.round(Number(propio) * (conDescuento! / venta) * 100) / 100;
  }

  /** Etiqueta de la presentación de la línea ("Blíster x10") o texto vacío si no tiene. */
  etiquetaDeLinea(item: CartItem): string {
    return item?.presentacion?.etiqueta ?? '';
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
      message = 'AGOTADO';
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

  /** Convierte una fecha del backend a Date, interpretando las fechas sin zona como UTC. */
  private parseFechaUtc(valor: any): Date | null {
    if (!valor) return null;

    if (Array.isArray(valor)) {
      const [anio, mes, dia, hora = 0, minuto = 0, segundo = 0] = valor;
      if (anio == null || mes == null || dia == null) return null;
      return new Date(Date.UTC(anio, mes - 1, dia, hora, minuto, segundo));
    }

    let texto = String(valor).trim();
    if (!texto) return null;
    // LocalDateTime del backend no trae zona: se interpreta como UTC.
    if (!/[zZ]$|[+-]\d{2}:?\d{2}$/.test(texto)) {
      texto += 'Z';
    }
    const fecha = new Date(texto);
    return isNaN(fecha.getTime()) ? null : fecha;
  }

  /** Segundos que le quedan a la reserva de un ítem (0 si ya venció). */
  getSegundosRestantes(item: CartItem): number {
    if (!item) return 0;
    const expiracion = this.parseFechaUtc(item.fechaExpiracion);
    if (!expiracion) {
      return Math.max(0, Number(item.segundosRestantes) || 0);
    }
    return Math.max(0, Math.floor((expiracion.getTime() - Date.now()) / 1000));
  }

  /** true si la reserva de 2 h de este ítem ya venció. */
  estaReservaVencida(item: CartItem): boolean {
    if (!item) return false;
    if (item.estado === 'EXPIRADA' || item.reservaVencida === true) return true;
    const expiracion = this.parseFechaUtc(item.fechaExpiracion);
    return expiracion != null && expiracion.getTime() <= Date.now();
  }

  /** Texto corto para la cuenta regresiva: "23h 59m", "12m 05s" o "Reserva vencida". */
  formatearTiempoRestante(segundos: number): string {
    if (segundos <= 0) return 'Reserva vencida';
    const horas = Math.floor(segundos / 3600);
    const minutos = Math.floor((segundos % 3600) / 60);
    const segs = segundos % 60;
    const dosDigitos = (n: number) => (n < 10 ? '0' + n : String(n));
    if (horas > 0) return `${horas}h ${dosDigitos(minutos)}m`;
    if (minutos > 0) return `${minutos}m ${dosDigitos(segs)}s`;
    return `${segs}s`;
  }

  /** Unidades que se pueden vender ahora (stock físico menos reservas). */
  stockVendible(producto: any): number | null {
    if (!producto) return null;
    if (producto.stockDisponible != null) {
      return Math.max(0, Number(producto.stockDisponible));
    }
    if (producto.stock != null) {
      return Math.max(0, Number(producto.stock));
    }
    return null;
  }

  /**
   * Agotado para la venta online: sin stock disponible o vencido.
   * Si el backend envía "agotadoOnline" se usa ese valor (ya descuenta reservas).
   */
  estaAgotado(producto: any): boolean {
    if (!producto) return false;
    if (typeof producto.agotadoOnline === 'boolean') return producto.agotadoOnline;
    if (producto.stockDisponible != null) {
      return Number(producto.stockDisponible) <= 0 || this.getDiscountInfo(producto).isExpired;
    }
    if (producto.agotado === true) return true;
    if (producto.stock != null && Number(producto.stock) <= 0) return true;
    return this.getDiscountInfo(producto).isExpired;
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
      return total + this.precioFinalDeLinea(item) * item.cantidad;
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
   * Sanea automáticamente los ítems guardados por versiones antiguas de la app
   * (sin `presentacion`, con campos faltantes o basura): normaliza cada línea,
   * descarta las irreparables (sin producto) y reescribe la versión limpia.
   *
   * @returns array de CartItem del localStorage, o [] si está vacío o inválido.
   */
  private loadItemsFromStorage(): CartItem[] {
    const stored = localStorage.getItem(this.storageKey);
    if (!stored) return [];
    try {
      const parsed: unknown = JSON.parse(stored);
      if (!Array.isArray(parsed)) return [];

      const saneados: CartItem[] = [];
      for (const raw of parsed) {
        const item = this.sanearItemAlmacenado(raw);
        if (item) saneados.push(item);
      }

      // Si algo cambió respecto a lo guardado, persistimos la versión normalizada
      // para que la próxima lectura ya sea limpia.
      if (JSON.stringify(saneados) !== JSON.stringify(parsed)) {
        this.saveItemsToStorage(saneados);
      }
      return saneados;
    } catch {
      return []; // Si el JSON está corrupto, retornar carrito vacío
    }
  }

  /**
   * Normaliza una línea leída de localStorage (formatos viejos de la app).
   * Devuelve null si la línea no tiene reparación posible (sin producto).
   */
  private sanearItemAlmacenado(raw: unknown): CartItem | null {
    if (!raw || typeof raw !== 'object') return null;
    const bruto = raw as Partial<CartItem> & Record<string, unknown>;
    const producto = bruto.producto as ProductoApi | null | undefined;
    if (!producto || (producto as { idProducto?: unknown }).idProducto == null) return null;

    const cantidad = Number(bruto.cantidad);
    const presentacion = bruto.presentacion as PresentacionLinea | null | undefined;
    const tienePresentacion =
      !!presentacion && typeof presentacion === 'object' && presentacion.idPresentacion != null;

    return {
      ...bruto,
      producto,
      cantidad: Number.isFinite(cantidad) && cantidad > 0 ? Math.floor(cantidad) : 1,
      // Ítems viejos sin presentación: queda undefined = producto en su unidad base.
      presentacion: tienePresentacion ? presentacion! : undefined,
    };
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