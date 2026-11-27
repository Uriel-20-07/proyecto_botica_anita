import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CartService, CartItem } from '../../services/cart.service';
import { PromocionService, PromoAplicada } from '../../services/promocion.service';

@Component({
  selector: 'app-carrito',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './carrito.html',
  styleUrl: './carrito.css',
})
export class CarritoComponent implements OnInit, OnDestroy {
  private readonly imagenPorCategoria: Record<string, string> = {
    medicamentos: 'assets/img/producto1.png',
    'cuidado personal': 'assets/img/producto2.png',
    belleza: 'assets/img/producto3.png',
    bebé: 'assets/img/producto4.png',
    'vitaminas / suplementos': 'assets/img/producto1.png',
    'equipo médicos': 'assets/img/producto2.png',
    'equipos médicos': 'assets/img/producto2.png',
  };

  // Bloquea el checkout si algún producto venció o su reserva de 2 h expiró.
  hasExpiredProducts = false;

  /** Evita pedir el carrito varias veces por el mismo vencimiento. */
  private refrescoSolicitado = false;

  /** Actualiza la cuenta regresiva cada segundo. */
  private temporizador?: ReturnType<typeof setInterval>;

  constructor(
    private readonly cartService: CartService,
    private readonly router: Router,
    private readonly promocionService: PromocionService,
  ) {
    // Reglas de las promociones para calcular el descuento del carrito
    this.promocionService.cargarReglas().subscribe();
  }

  ngOnInit(): void {
    this.actualizarEstadoExpiracion();
    this.temporizador = setInterval(() => this.actualizarEstadoExpiracion(), 1000);
  }

  ngOnDestroy(): void {
    if (this.temporizador) {
      clearInterval(this.temporizador);
    }
  }

  /**
   * MÉTODO AUXILIAR: Captura la fecha sin importar si Spring Boot
   * la envía como fechaCaducidad (camelCase) o fecha_caducidad (snake_case).
   */
/**
   * MÉTODO AUXILIAR ROBUSTO: Captura la fecha y la formatea correctamente
   * sin importar cómo la envíe Spring Boot.
   */
  getFecha(producto: any): string {
    const fecha = producto.fechaCaducidad || producto.fecha_caducidad;
    if (!fecha) return '';

    // Si Spring Boot lo envía como arreglo [Año, Mes, Día]
    if (Array.isArray(fecha)) {
      const mes = fecha[1] < 10 ? '0' + fecha[1] : fecha[1];
      const dia = fecha[2] < 10 ? '0' + fecha[2] : fecha[2];
      return `${fecha[0]}-${mes}-${dia}`;
    }
    
    // Si ya es un texto normal '2026-07-10' o similar
    return fecha;
  }

  getItems() {
    const items = this.cartService.getItems();
    // Se recalcula también en el temporizador, para reflejar el vencimiento en vivo.
    this.hasExpiredProducts = items.some(
      (item) =>
        this.cartService.estaReservaVencida(item) ||
        this.getDiscountInfo(item.producto).isExpired,
    );
    return this.agruparPorProducto(items);
  }

  /**
   * Deja juntas las líneas de un mismo producto (por ejemplo, su blíster y su caja), en el orden en que
   * apareció el producto en el carrito y con la presentación más chica primero. Así se ve la diferencia
   * de presentación y de precio una al lado de la otra.
   */
  private agruparPorProducto(items: CartItem[]): CartItem[] {
    const primeraPosicion = new Map<number, number>();
    items.forEach((item, i) => {
      const id = item?.producto?.idProducto;
      if (id != null && !primeraPosicion.has(id)) primeraPosicion.set(id, i);
    });
    return items
      .map((item, i) => ({ item, i }))
      .sort((a, b) => {
        const pa = primeraPosicion.get(a.item?.producto?.idProducto as number) ?? a.i;
        const pb = primeraPosicion.get(b.item?.producto?.idProducto as number) ?? b.i;
        if (pa !== pb) return pa - pb;
        const ua = a.item.presentacion?.unidades ?? 1;
        const ub = b.item.presentacion?.unidades ?? 1;
        return ua !== ub ? ua - ub : a.i - b.i;
      })
      .map((x) => x.item);
  }

  /** Revisa reservas vencidas y pide al backend que libere las que correspondan. */
  private actualizarEstadoExpiracion(): void {
    const items = this.cartService.getItems();
    const hayReservaVencida = items.some((item) => this.cartService.estaReservaVencida(item));

    this.hasExpiredProducts =
      hayReservaVencida || items.some((item) => this.getDiscountInfo(item.producto).isExpired);

    if (hayReservaVencida && !this.refrescoSolicitado) {
      // El backend libera el stock, borra la línea y devuelve la lista de expirados.
      this.refrescoSolicitado = true;
      this.cartService.refrescarCarrito();
    } else if (!hayReservaVencida) {
      this.refrescoSolicitado = false;
    }
  }

  /** Segundos restantes de la reserva de este ítem. */
  getTiempoRestante(item: CartItem): number {
    return this.cartService.getSegundosRestantes(item);
  }

  /** Texto de la cuenta regresiva ("23h 59m", "12m 05s"...). */
  getTiempoTexto(item: CartItem): string {
    return this.cartService.formatearTiempoRestante(this.getTiempoRestante(item));
  }

  /** true si la reserva ya venció (la línea se eliminará al refrescar). */
  esReservaVencida(item: CartItem): boolean {
    return this.cartService.estaReservaVencida(item);
  }

  /** true cuando queda menos de 1 hora de reserva (para resaltar el aviso). */
  reservaPorVencer(item: CartItem): boolean {
    const segundos = this.getTiempoRestante(item);
    return segundos > 0 && segundos <= 3600;
  }

  /**
   * Identidad estable de cada fila del carrito para *ngFor.
   * Como getItems() retorna un arreglo nuevo en cada detección de cambios,
   * sin trackBy Angular destruiría y recrearía todos los nodos (y cualquier
   * reorden del servidor movería los productos de sitio).
   *
   * IMPORTANTE: Angular invoca esta función como función suelta (el `this` NO es
   * el componente), por lo que aquí no puede usarse `this.cartService`. La clave
   * se calcula en línea con el mismo formato de CartService.claveLinea().
   */
  trackByItem(index: number, item?: CartItem | null): string {
    // Producto + presentación: una caja y un blíster del mismo producto son filas distintas.
    const idProducto = item?.producto?.idProducto;
    if (idProducto == null) {
      // Ítem inválido o vacío (p. ej. entradas viejas en localStorage): clave única por posición.
      return `item-${index}`;
    }
    return `${idProducto}:${item?.presentacion?.idPresentacion ?? 0}`;
  }

  /**
   * true mientras se pueda sumar una unidad más de este ítem:
   * máximo 10 por producto (regla de negocio) y con stock disponible.
   */
  puedeAumentar(item: CartItem): boolean {
    if (!item) return false;
    if (item.cantidad >= 10) return false;
    if (this.cartService.estaReservaVencida(item)) return false;
    // El stock está en unidades base: una caja de 100 necesita 100 disponibles, un blíster de 10 necesita 10.
    const disponible = this.cartService.stockVendible(item.producto);
    const unidades = item.presentacion?.unidades ?? 1;
    return disponible == null || disponible >= unidades;
  }

  /** Etiqueta de la presentación de la línea ("Blíster x10"), vacía si no tiene. */
  etiqueta(item: CartItem): string {
    return this.cartService.etiquetaDeLinea(item);
  }

  /** true si la línea es un blíster (presentación que no es la caja predeterminada). */
  esBlister(item: CartItem): boolean {
    const p = item?.presentacion;
    if (!p) return false;
    // Sin el dato de "predeterminada", un precio propio también identifica al blíster (la caja usa el del producto).
    return p.esPredeterminada === false || (p.esPredeterminada == null && p.precioVenta != null);
  }

  /** Unidades sueltas (tabletas, cápsulas...) que trae UNA presentación; 0 si la línea no es una presentación múltiple. */
  unidadesPorPresentacion(item: CartItem): number {
    const u = item?.presentacion?.unidades ?? 1;
    return u > 1 ? u : 0;
  }

  /** Parámetros para abrir el detalle del producto de la línea (misma URL que usa el catálogo). */
  paramsDetalle(item: CartItem): { producto: number } {
    return { producto: item?.producto?.idProducto ?? 0 };
  }

  /** Precio de UNA unidad de la línea (con oferta y presentación). */
  precioLinea(item: CartItem): number {
    return this.cartService.precioFinalDeLinea(item);
  }

  /** Precio regular de UNA unidad de la línea (sin oferta), para mostrarlo tachado. */
  precioRegularLinea(item: CartItem): number {
    return this.cartService.precioRegularDeLinea(item);
  }

  getProductImage(producto: any): string {
    if (producto.imgUrl) return producto.imgUrl;
    const cat = producto.categoria?.nombre?.toLowerCase() || 'general';
    return this.imagenPorCategoria[cat] || 'assets/img/placeholder-pill.png';
  }

  /**
   * Obtiene la información de descuento precalculada por la base de datos.
   */
  getDiscountInfo(producto: any): {
    percentage: number;
    message: string;
    isExpired: boolean;
  } {
    return this.cartService.getDiscountInfo(producto);
  }

  /**
   * Obtiene el precio final unitario del producto con el descuento aplicado.
   */
  getFinalPrice(producto: any): number {
    return this.cartService.getFinalPrice(producto);
  }

  /** Promociones que se cumplen con lo que hay ahora en el carrito (se pierden solas al quitar un producto). */
  getPromosAplicadas(): PromoAplicada[] {
    return this.promocionService.calcularDescuento(this.cartService.getItems()).aplicadas;
  }

  getDescuentoPromos(): number {
    return this.promocionService.calcularDescuento(this.cartService.getItems()).total;
  }

  getSubtotal(): number {
    return Math.max(0, this.cartService.getTotal() - this.getDescuentoPromos());
  }

  getCount(): number {
    return this.cartService.getCount();
  }

  increase(item: CartItem): void {
    if (!item || !this.puedeAumentar(item)) return;
    this.cartService.add(item.producto, undefined, item.presentacion?.idPresentacion ?? null);
  }

  decrease(item: CartItem): void {
    this.cartService.decrease(item.producto.idProducto, item.presentacion?.idPresentacion ?? null);
  }

  remove(item: CartItem): void {
    this.cartService.remove(item.producto.idProducto, item.presentacion?.idPresentacion ?? null);
  }

  clear(): void {
    this.cartService.clear();
  }

  pagar(): void {
    // Regla estricta: No avanza a pago si hay items caducados o si está vacío
    if (this.getCount() === 0 || this.hasExpiredProducts) return;
    this.router.navigate(['/pago']);
  }
}
