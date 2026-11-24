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

  // Bloquea el checkout si algún producto venció o su reserva de 24 h expiró.
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
    return items;
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
   */
  trackByItem(index: number, item: CartItem): number {
    return item?.producto?.idProducto ?? index;
  }

  /**
   * true mientras se pueda sumar una unidad más de este ítem:
   * máximo 10 por producto (regla de negocio) y con stock disponible.
   */
  puedeAumentar(item: CartItem): boolean {
    if (!item) return false;
    if (item.cantidad >= 10) return false;
    if (this.cartService.estaReservaVencida(item)) return false;
    const disponible = this.cartService.stockVendible(item.producto);
    return disponible == null || disponible > 0;
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

  increase(productoId: number): void {
    const item = this.getItems().find((entry) => entry.producto.idProducto === productoId);
    if (!item || !this.puedeAumentar(item)) return;
    this.cartService.add(item.producto);
  }

  decrease(productoId: number): void {
    this.cartService.decrease(productoId);
  }

  remove(productoId: number): void {
    this.cartService.remove(productoId);
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
