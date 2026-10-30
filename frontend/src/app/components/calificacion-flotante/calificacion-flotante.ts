import { Component, Inject, OnDestroy, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { CalificacionService, CalificacionPendiente } from '../../services/calificacion.service';

/**
 * Aviso flotante para que el cliente califique su pedido (1 a 5 estrellas y comentario opcional).
 *
 * Aparece cuando un pedido del cliente está "Listo para recojo" o "Completado" y todavía no fue
 * calificado. Si el cliente elige "Ahora no", no se vuelve a mostrar para ese pedido.
 */
@Component({
  selector: 'app-calificacion-flotante',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './calificacion-flotante.html',
  styleUrls: ['./calificacion-flotante.css'],
})
export class CalificacionFlotanteComponent implements OnInit, OnDestroy {
  readonly estrellasOpciones = [1, 2, 3, 4, 5];
  private readonly etiquetas = ['', 'Muy mala', 'Mala', 'Regular', 'Buena', 'Excelente'];

  visible = false;
  pendiente: CalificacionPendiente | null = null;

  estrellas = 0;
  hover = 0;
  comentario = '';
  enviando = false;
  gracias = false;
  error = '';

  private userSub?: Subscription;
  private timer?: ReturnType<typeof setInterval>;

  constructor(
    private authService: AuthService,
    private calificacionService: CalificacionService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object,
  ) {}

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    // Al iniciar sesión (o al recargar con sesión activa) se consulta si hay algo por calificar
    this.userSub = this.authService.currentUser$.subscribe((user) => {
      if (user) {
        this.consultar();
      } else {
        this.cerrarSinGuardar();
      }
    });

    // El estado del pedido cambia solo en el servidor; se vuelve a consultar cada minuto
    this.timer = setInterval(() => this.consultar(), 60_000);
  }

  ngOnDestroy(): void {
    this.userSub?.unsubscribe();
    if (this.timer) clearInterval(this.timer);
  }

  get codigoPedido(): string {
    return '#' + String(this.pendiente?.idPedido ?? '').padStart(6, '0');
  }

  get etiquetaActual(): string {
    return this.etiquetas[this.hover || this.estrellas] || '';
  }

  get esRecojo(): boolean {
    return this.pendiente?.estado === 'LISTO_RECOJO';
  }

  elegir(n: number): void {
    this.estrellas = n;
    this.error = '';
  }

  enviar(): void {
    if (!this.pendiente?.idPedido || this.enviando) return;
    if (this.estrellas < 1) {
      this.error = 'Elige de 1 a 5 estrellas';
      return;
    }
    this.enviando = true;
    this.error = '';
    this.calificacionService.enviar(this.pendiente.idPedido, this.estrellas, this.comentario).subscribe({
      next: () => {
        this.enviando = false;
        this.gracias = true;
        setTimeout(() => this.cerrarSinGuardar(), 3500);
      },
      error: (err) => {
        this.enviando = false;
        if (err?.status === 409) {
          // Ya estaba calificado (por ejemplo, desde otro dispositivo)
          this.cerrarSinGuardar();
          return;
        }
        this.error = err?.error?.error || 'No pudimos enviar tu calificación. Intenta de nuevo.';
      },
    });
  }

  /** "Ahora no": se oculta y no se vuelve a preguntar por este pedido. */
  omitir(): void {
    if (this.pendiente?.idPedido) {
      try {
        localStorage.setItem(this.claveOmitido(this.pendiente.idPedido), '1');
      } catch { /* sin almacenamiento: solo se oculta */ }
    }
    this.cerrarSinGuardar();
  }

  private consultar(): void {
    if (this.visible || this.enviando || this.gracias) return;
    if (!this.authService.getToken()) return;
    // No interrumpir mientras el cliente está pagando
    if (this.router.url.startsWith('/pago')) return;

    this.calificacionService.obtenerPendiente().subscribe({
      next: (r) => {
        if (r?.pendiente && r.idPedido && !this.fueOmitido(r.idPedido)) {
          this.pendiente = r;
          this.estrellas = 0;
          this.hover = 0;
          this.comentario = '';
          this.error = '';
          this.gracias = false;
          this.visible = true;
        }
      },
      error: () => { /* si falla, se reintenta en la próxima consulta */ },
    });
  }

  private cerrarSinGuardar(): void {
    this.visible = false;
    this.gracias = false;
    this.pendiente = null;
  }

  private claveOmitido(idPedido: number): string {
    return `calificacion_omitida_${idPedido}`;
  }

  private fueOmitido(idPedido: number): boolean {
    try {
      return localStorage.getItem(this.claveOmitido(idPedido)) === '1';
    } catch {
      return false;
    }
  }
}
