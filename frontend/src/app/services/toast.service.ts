import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private contenedor: HTMLElement | null = null;

  // ── Control del contador del carrito (se actualiza solo cuando llega la bolita) ──
  private retenciones = 0;
  private nodoBadge: Node | null = null;
  private textoVisible = '';
  private textoReal = '';
  private observador: MutationObserver | null = null;

  mostrar(mensaje: string, tipo: 'exito' | 'error' = 'exito', duracionMs = 2500): void {
    if (typeof document === 'undefined') return;

    if (!this.contenedor) {
      this.contenedor = document.createElement('div');
      Object.assign(this.contenedor.style, {
        position: 'fixed',
        bottom: '24px',
        left: '50%',
        transform: 'translateX(-50%)',
        zIndex: '99999',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '10px',
        pointerEvents: 'none'
      });
      document.body.appendChild(this.contenedor);
    }

    // Congelar el contador del carrito hasta que la bolita llegue
    let liberado = false;
    const liberar = () => {
      if (liberado) return;
      liberado = true;
      this.liberarContador();
    };
    if (tipo === 'exito' && this.retenerContador()) {
      // Seguro: si algo falla, el contador se libera igual
      setTimeout(liberar, duracionMs + 4000);
    } else {
      liberado = true; // no se retuvo nada, no hay que liberar
    }

    const color = tipo === 'exito' ? '#16a34a' : '#dc2626';
    const toast = document.createElement('div');
    toast.textContent = (tipo === 'exito' ? '✓ ' : '✕ ') + mensaje;
    Object.assign(toast.style, {
      background: color,
      color: '#fff',
      padding: '12px 18px',
      borderRadius: '8px',
      fontSize: '14px',
      fontWeight: '600',
      boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
      opacity: '0',
      transform: 'translateY(10px)',
      transition: 'opacity 0.25s ease, transform 0.25s ease'
    });

    this.contenedor.appendChild(toast);

    requestAnimationFrame(() => {
      toast.style.opacity = '1';
      toast.style.transform = 'translateY(0)';
    });

    setTimeout(() => this.salir(toast, tipo, liberar), duracionMs);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Contador del carrito
  // ─────────────────────────────────────────────────────────────────────────

  /** Congela el número del circulito en su valor actual. Devuelve false si no pudo. */
  private retenerContador(): boolean {
    const badge = document.querySelector('[data-cart-badge]') as HTMLElement | null;
    if (!badge || !badge.firstChild || typeof MutationObserver === 'undefined') return false;

    if (this.retenciones === 0) {
      const nodo = badge.firstChild;
      this.nodoBadge = nodo;
      this.textoVisible = nodo.nodeValue ?? '';
      this.textoReal = this.textoVisible;

      // Cuando Angular escriba el número nuevo, lo guardamos y dejamos el viejo a la vista
      this.observador = new MutationObserver(() => {
        const actual = nodo.nodeValue ?? '';
        if (actual !== this.textoVisible) {
          this.textoReal = actual;
          nodo.nodeValue = this.textoVisible;
        }
      });
      this.observador.observe(badge, { characterData: true, subtree: true });
    }

    this.retenciones++;
    return true;
  }

  /** Muestra el número nuevo en el circulito (cuando llega la bolita). */
  private liberarContador(): void {
    if (this.retenciones === 0) return;
    this.retenciones--;
    const nodo = this.nodoBadge;
    if (!nodo) return;

    if (this.retenciones === 0) {
      this.observador?.disconnect();
      this.observador = null;
      nodo.nodeValue = this.textoReal;
      this.nodoBadge = null;
    } else {
      // Hay otras bolitas en camino: subir de a uno por cada llegada
      const siguiente = Math.min(Number(this.textoVisible) + 1, Number(this.textoReal));
      if (!isNaN(siguiente)) {
        this.textoVisible = String(siguiente);
        nodo.nodeValue = this.textoVisible;
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Animación de salida
  // ─────────────────────────────────────────────────────────────────────────

  /** Salida del aviso: éxito → se vuelve bolita y viaja al carrito; error → solo se desvanece. */
  private salir(toast: HTMLElement, tipo: 'exito' | 'error', liberar: () => void): void {
    const badge = document.querySelector('[data-cart-badge]') as HTMLElement | null;

    if (tipo !== 'exito' || !badge || typeof toast.animate !== 'function') {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 300);
      liberar();
      return;
    }

    const r = toast.getBoundingClientRect();
    const tam = 16;
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;

    // La bolita nace con la misma forma y posición del aviso
    const orbe = document.createElement('div');
    Object.assign(orbe.style, {
      position: 'fixed',
      left: r.left + 'px',
      top: r.top + 'px',
      width: r.width + 'px',
      height: r.height + 'px',
      background: 'radial-gradient(circle at 35% 35%, #86efac, #16a34a)',
      borderRadius: '8px',
      zIndex: '99999',
      pointerEvents: 'none'
    });
    document.body.appendChild(orbe);
    toast.remove();

    // Fase 1: el aviso se encoge hasta ser una bolita brillante
    const fase1 = orbe.animate(
      [
        {
          left: r.left + 'px',
          top: r.top + 'px',
          width: r.width + 'px',
          height: r.height + 'px',
          borderRadius: '8px',
          boxShadow: '0 0 0 0 rgba(34,197,94,0)'
        },
        {
          left: cx - tam / 2 + 'px',
          top: cy - tam / 2 + 'px',
          width: tam + 'px',
          height: tam + 'px',
          borderRadius: '50%',
          boxShadow: '0 0 14px 4px rgba(34,197,94,0.65)'
        }
      ],
      { duration: 280, easing: 'ease-in', fill: 'forwards' }
    );

    fase1.onfinish = () => this.volar(orbe, cx, cy, tam, badge, liberar);
  }

  /** Fase 2: la bolita viaja en curva hasta el carrito dejando una estela. */
  private volar(
    orbe: HTMLElement,
    x0: number,
    y0: number,
    tam: number,
    badge: HTMLElement,
    liberar: () => void
  ): void {
    const b = badge.getBoundingClientRect();
    const x2 = b.left + b.width / 2;
    const y2 = b.top + b.height / 2;

    // Punto de control: hace que la bolita suba primero y luego gire hacia el carrito
    const x1 = x0;
    const y1 = y2;

    const duracion = 800;
    const inicio = performance.now();
    let ultimaEstela = 0;

    const paso = (ahora: number) => {
      const t = Math.min((ahora - inicio) / duracion, 1);
      const e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; // ease in-out

      const x = (1 - e) * (1 - e) * x0 + 2 * (1 - e) * e * x1 + e * e * x2;
      const y = (1 - e) * (1 - e) * y0 + 2 * (1 - e) * e * y1 + e * e * y2;

      // La bolita se achica un poco al acercarse
      const escala = 1 - 0.35 * e;
      orbe.style.left = x - tam / 2 + 'px';
      orbe.style.top = y - tam / 2 + 'px';
      orbe.style.transform = `scale(${escala})`;

      // Estela: un puntito que se desvanece cada ~25 ms
      if (ahora - ultimaEstela > 25) {
        ultimaEstela = ahora;
        this.dejarEstela(x, y, tam * escala);
      }

      if (t < 1) {
        requestAnimationFrame(paso);
      } else {
        orbe.remove();
        liberar();               // ← aquí recién sube el número del circulito
        this.destellarBadge(badge);
      }
    };

    requestAnimationFrame(paso);
  }

  /** Puntito verde que se encoge y desaparece, formando la cola de cometa. */
  private dejarEstela(x: number, y: number, tam: number): void {
    const punto = document.createElement('div');
    Object.assign(punto.style, {
      position: 'fixed',
      left: x - tam / 2 + 'px',
      top: y - tam / 2 + 'px',
      width: tam + 'px',
      height: tam + 'px',
      borderRadius: '50%',
      background: '#22c55e',
      opacity: '0.55',
      zIndex: '99998',
      pointerEvents: 'none'
    });
    document.body.appendChild(punto);

    const anim = punto.animate(
      [
        { transform: 'scale(1)', opacity: 0.55 },
        { transform: 'scale(0.1)', opacity: 0 }
      ],
      { duration: 380, easing: 'ease-out', fill: 'forwards' }
    );
    anim.onfinish = () => punto.remove();
  }

  /** El circulito late en verde, lanza una onda y vuelve a su naranja original. */
  private destellarBadge(badge: HTMLElement): void {
    const b = badge.getBoundingClientRect();

    // Onda que se expande desde el circulito
    const onda = document.createElement('div');
    Object.assign(onda.style, {
      position: 'fixed',
      left: b.left + 'px',
      top: b.top + 'px',
      width: b.width + 'px',
      height: b.height + 'px',
      borderRadius: '999px',
      border: '2px solid #22c55e',
      zIndex: '99998',
      pointerEvents: 'none'
    });
    document.body.appendChild(onda);
    const animOnda = onda.animate(
      [
        { transform: 'scale(1)', opacity: 0.9 },
        { transform: 'scale(2.6)', opacity: 0 }
      ],
      { duration: 550, easing: 'ease-out', fill: 'forwards' }
    );
    animOnda.onfinish = () => onda.remove();

    // Latido verde del circulito
    badge.style.transition = 'background 0.25s ease, transform 0.25s cubic-bezier(.34,1.56,.64,1), box-shadow 0.25s ease';
    badge.style.background = '#16a34a';
    badge.style.transform = 'scale(1.35)';
    badge.style.boxShadow = '0 0 0 4px rgba(34,197,94,0.3)';

    setTimeout(() => {
      badge.style.background = '';   // vuelve al naranja definido en navbar.css
      badge.style.transform = '';
      badge.style.boxShadow = '';
    }, 650);

    setTimeout(() => {
      badge.style.transition = '';
    }, 1050);
  }
}