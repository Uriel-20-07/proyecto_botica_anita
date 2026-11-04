import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ItemPromo, Promocion, PromocionService } from '../../services/promocion.service';

type EstadoBoton = 'idle' | 'cargando' | 'ok';

/** Imagen de respaldo (caja genérica) cuando el producto no tiene imagen o falla al cargar. */
const IMG_RESPALDO =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120"><rect width="120" height="120" fill="#eef3fb"/>' +
      '<rect x="34" y="40" width="52" height="44" rx="6" fill="#c9d8f0"/><rect x="34" y="34" width="52" height="12" rx="4" fill="#9db8e3"/></svg>'
  );

/** Cada cuántos milisegundos avanza solo el carrusel. */
const INTERVALO_AUTOPLAY_MS = 4500;
/** Duración de la animación de deslizamiento (debe coincidir con el CSS). */
const DURACION_TRANSICION_MS = 450;

/**
 * Carrusel de promociones del inicio.
 * Muestra las promociones que entrega el backend (/api/promociones); avanza solo en bucle
 * infinito, se detiene al pasar el mouse, y cada promoción puede verse en detalle ("Ver más")
 * y agregarse completa al carrito con un solo clic.
 */
@Component({
  selector: 'app-promo-carousel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './promo-carousel.html',
  styleUrl: './promo-carousel.css'
})
export class PromoCarouselComponent implements OnInit, OnDestroy {
  promociones: Promocion[] = [];
  cargando = true;

  /** Cuántas tarjetas caben a la vez (3 en escritorio, 2 en tablet, 1 en móvil). */
  visibles = 3;
  /** Posición de la primera tarjeta visible; puede llegar a promociones.length (zona de clones). */
  indice = 0;
  /** Se usa para saltar de la zona de clones al inicio sin que se vea la animación. */
  sinTransicion = false;
  /** El autoplay se detiene mientras el cliente tiene el mouse o el foco sobre el carrusel. */
  pausado = false;

  /** Promoción abierta en el detalle ("Ver más"). */
  detalle: Promocion | null = null;

  estado: Record<string, EstadoBoton> = {};
  errores: Record<string, string> = {};

  private temporizador: ReturnType<typeof setInterval> | null = null;
  private normalizador: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly promocionService: PromocionService) {}

  ngOnInit(): void {
    this.calcularVisibles();
    this.promocionService.getDisponibles().subscribe({
      next: (promos) => {
        this.promociones = promos;
        this.cargando = false;
        this.iniciarAutoplay();
      },
      error: () => {
        this.promociones = [];
        this.cargando = false;
      }
    });
  }

  ngOnDestroy(): void {
    this.detenerAutoplay();
    if (this.normalizador) clearTimeout(this.normalizador);
  }

  // ── Carrusel infinito ───────────────────────────────────────────────────────

  @HostListener('window:resize')
  calcularVisibles(): void {
    if (typeof window === 'undefined') return;
    const ancho = window.innerWidth;
    this.visibles = ancho >= 1000 ? 3 : ancho >= 700 ? 2 : 1;
    if (this.promociones.length > 0) this.indice = this.indice % this.promociones.length;
  }

  /** Tarjetas que realmente se muestran a la vez (no más que las promociones que hay). */
  get visiblesEfectivos(): number {
    return Math.max(1, Math.min(this.visibles, this.promociones.length));
  }

  /** Promociones + una copia de las primeras, para que el bucle no tenga saltos visibles. */
  get slides(): Promocion[] {
    if (this.promociones.length < 2) return this.promociones;
    return this.promociones.concat(this.promociones.slice(0, this.visiblesEfectivos));
  }

  /** Punto activo de los indicadores. */
  get posicionActiva(): number {
    return this.promociones.length ? this.indice % this.promociones.length : 0;
  }

  siguiente(): void {
    const n = this.promociones.length;
    if (n < 2 || this.indice >= n) return; // ya está deslizando hacia las copias
    this.indice++;
    this.programarNormalizacion();
  }

  anterior(): void {
    const n = this.promociones.length;
    if (n < 2) return;
    if (this.indice === 0) {
      // Salta (sin animación) a las copias del final y desde ahí retrocede con animación
      this.sinTransicion = true;
      this.indice = n;
      setTimeout(() => {
        this.sinTransicion = false;
        this.indice = n - 1;
      }, 30);
    } else if (this.indice < n) {
      this.indice--;
    }
  }

  irA(posicion: number): void {
    this.indice = posicion;
  }

  /** Al terminar de llegar a las copias, vuelve a la posición 0 sin animación. */
  private programarNormalizacion(): void {
    if (this.normalizador) clearTimeout(this.normalizador);
    this.normalizador = setTimeout(() => {
      if (this.indice >= this.promociones.length) {
        this.sinTransicion = true;
        this.indice = 0;
        setTimeout(() => (this.sinTransicion = false), 30);
      }
    }, DURACION_TRANSICION_MS);
  }

  // ── Autoplay ────────────────────────────────────────────────────────────────

  private iniciarAutoplay(): void {
    if (typeof window === 'undefined' || this.promociones.length < 2) return;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    this.detenerAutoplay();
    this.temporizador = setInterval(() => {
      const pestanaVisible = typeof document === 'undefined' || !document.hidden;
      if (!this.pausado && !this.detalle && pestanaVisible) this.siguiente();
    }, INTERVALO_AUTOPLAY_MS);
  }

  private detenerAutoplay(): void {
    if (this.temporizador) {
      clearInterval(this.temporizador);
      this.temporizador = null;
    }
  }

  // ── Textos de cada promoción ────────────────────────────────────────────────

  /** "3x2" para llevar-y-pagar-menos, "-15%" para descuentos porcentuales. */
  insignia(p: Promocion): string {
    if (p.unidadesGratis > 0) {
      const unidades = p.items.reduce((acc, i) => acc + i.cantidad, 0);
      return `${unidades}x${unidades - p.unidadesGratis}`;
    }
    return `-${Math.round(p.porcentaje)}%`;
  }

  /** Cantidad total de unidades que incluye UNA vez la promoción. */
  unidades(p: Promocion): number {
    return p.items.reduce((acc, i) => acc + i.cantidad, 0);
  }

  /** Promoción de un solo producto tipo 3x2 (los pañales): se muestran sus 3 paquetes. */
  esPaquete(p: Promocion): boolean {
    return p.unidadesGratis > 0 && p.items.length === 1;
  }

  /** Cuántas veces se dibuja la imagen de un producto: 3 en el 3x2, 1 en el resto. */
  copias(p: Promocion, item: ItemPromo): number[] {
    return Array.from({ length: this.esPaquete(p) ? Math.min(item.cantidad, 3) : 1 }, (_, i) => i);
  }

  imagen(url?: string): string {
    return url || IMG_RESPALDO;
  }

  imagenRota(evento: Event): void {
    (evento.target as HTMLImageElement).src = IMG_RESPALDO;
  }

  // ── Detalle (Ver más) ───────────────────────────────────────────────────────

  verMas(p: Promocion): void {
    this.detalle = p;
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  @HostListener('document:keydown.escape')
  alEscape(): void {
    this.cerrarDetalle();
  }

  // ── Agregar al carrito ──────────────────────────────────────────────────────

  agregar(p: Promocion): void {
    if (this.estado[p.id] === 'cargando') return;
    this.estado[p.id] = 'cargando';
    this.errores[p.id] = '';

    this.promocionService.agregarAlCarrito(p).subscribe({
      next: () => {
        this.estado[p.id] = 'ok';
        setTimeout(() => (this.estado[p.id] = 'idle'), 2200);
      },
      error: (err) => {
        this.estado[p.id] = 'idle';
        // 401: no hay sesión; el CartService ya abrió el modal de login.
        if (err?.status === 401) return;
        this.errores[p.id] =
          err?.error?.error || 'No pudimos agregar la promoción. Inténtalo nuevamente.';
      }
    });
  }

  textoBoton(p: Promocion): string {
    switch (this.estado[p.id]) {
      case 'cargando':
        return 'Agregando…';
      case 'ok':
        return '¡Agregada!';
      default:
        return 'Agregar al carrito';
    }
  }
}
