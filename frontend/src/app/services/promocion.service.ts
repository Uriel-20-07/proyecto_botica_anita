import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, concat, toArray, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { CartItem, CartService } from './cart.service';

/** Producto que forma parte de una promoción y cuántas unidades se necesitan. */
export interface ItemPromo {
  idProducto: number;
  nombre: string;
  imgUrl?: string;
  precioUnitario: number;
  cantidad: number;
}

/**
 * Promoción tal como la entrega el backend (GET /api/promociones).
 * Los precios son por UNA vez la promoción (ej. 3 pañales, o el combo completo).
 */
export interface Promocion {
  id: string;
  titulo: string;
  etiqueta: string;
  descripcion: string;
  unidadesGratis: number;
  porcentaje: number;
  items: ItemPromo[];
  precioNormal: number;
  precioPromo: number;
  ahorro: number;
}

export interface PromoAplicada {
  id: string;
  titulo: string;
  veces: number;
  descuento: number;
}

/**
 * Servicio de promociones.
 *
 * El descuento no se guarda en ningún lado: se DERIVA del contenido del carrito.
 * Si el cliente quita un producto de la promoción (o baja la cantidad) y ya no se
 * cumple la regla, el descuento desaparece solo. El backend aplica las mismas
 * reglas al cobrar (PromocionService.java), así que lo que se ve es lo que se paga.
 */
@Injectable({ providedIn: 'root' })
export class PromocionService {
  private readonly url = `${environment.apiUrl}/api/promociones`;

  /** Reglas de todas las promociones (sin filtrar por stock), para calcular el carrito. */
  private readonly reglasSubject = new BehaviorSubject<Promocion[]>([]);
  readonly reglas$ = this.reglasSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly cartService: CartService
  ) {}

  /** Promociones que se pueden ofrecer ahora (todos sus productos con stock). */
  getDisponibles(): Observable<Promocion[]> {
    return this.http.get<Promocion[]>(this.url);
  }

  /** Carga las reglas para el cálculo del carrito (se llama desde carrito y pago). */
  cargarReglas(): Observable<Promocion[]> {
    return this.http
      .get<Promocion[]>(this.url, { params: { reglas: 'true' } })
      .pipe(tap((promos) => this.reglasSubject.next(promos)));
  }

  /**
   * Agrega al carrito todos los productos de la promoción, en orden, uno tras otro
   * (el backend guarda el carrito por usuario, así que no conviene hacerlo en paralelo).
   */
  agregarAlCarrito(promo: Promocion): Observable<any[]> {
    return concat(
      ...promo.items.map((item) => this.cartService.addWithQty(item.idProducto, item.cantidad))
    ).pipe(toArray());
  }

  /**
   * Descuento total por promociones según lo que hay AHORA en el carrito.
   * Misma lógica que PromocionService.calcularDescuento del backend.
   */
  calcularDescuento(items: CartItem[]): { total: number; aplicadas: PromoAplicada[] } {
    const cantidades = new Map<number, number>();
    for (const it of items) {
      if (this.cartService.getDiscountInfo(it.producto).isExpired) continue;
      const id = it.producto.idProducto;
      cantidades.set(id, (cantidades.get(id) ?? 0) + it.cantidad);
    }

    let total = 0;
    const aplicadas: PromoAplicada[] = [];

    for (const promo of this.reglasSubject.value) {
      let veces = Number.MAX_SAFE_INTEGER;
      for (const item of promo.items) {
        veces = Math.min(veces, Math.floor((cantidades.get(item.idProducto) ?? 0) / item.cantidad));
      }
      if (veces <= 0) continue; // falta un producto o no alcanza la cantidad: la promo se pierde

      const descuento = Math.round(promo.ahorro * veces * 100) / 100;
      total += descuento;
      aplicadas.push({ id: promo.id, titulo: promo.titulo, veces, descuento });
    }

    return { total: Math.round(total * 100) / 100, aplicadas };
  }
}
