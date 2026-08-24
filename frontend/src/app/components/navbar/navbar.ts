import { AsyncPipe, DecimalPipe } from '@angular/common';
import { Component, HostListener, OnDestroy } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { AuthModalService } from '../../services/auth-modal.service';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { ProductoApi } from '../../services/catalogo.service';
import { AlgoliaService, AlgoliaProducto } from '../../services/algolia.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [AsyncPipe, DecimalPipe, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css'
})
export class NavbarComponent implements OnDestroy {
  readonly currentUser$;
  readonly cartCount$;
  dropdownAbierto = false;
  categorias = [
    'Todo',
    'MEDICAMENTOS',
    'CUIDADO PERSONAL',
    'BELLEZA',
    'BEBÉ',
    'VITAMINAS / SUPLEMENTOS',
    'EQUIPOS MÉDICOS'
  ];
  categoriaSeleccionada = 'Todo';
  terminoBusqueda = '';

  // ─── Algolia: autocomplete ────────────────────────────────────────────────
  sugerencias: AlgoliaProducto[] = [];
  mostrarSugerencias = false;

  private busquedaSubject = new Subject<string>();
  private destroy$ = new Subject<void>();

  constructor(
    private readonly authModalService: AuthModalService,
    private readonly authService: AuthService,
    private readonly cartService: CartService,
    private readonly algoliaService: AlgoliaService,
    private readonly router: Router
  ) {
    this.currentUser$ = this.authService.currentUser$;
    this.cartCount$ = this.cartService.items$;

    // Debounce de 350ms para el autocomplete de Algolia
    this.busquedaSubject.pipe(
      debounceTime(350),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe((termino) => {
      if (termino.length >= 2) {
        this.buscarSugerenciasAlgolia(termino);
      } else {
        this.sugerencias = [];
        this.mostrarSugerencias = false;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  openLogin(): void {
    this.authModalService.open('login');
  }

  openRegister(): void {
    this.authModalService.open('registro');
  }

  goToPerfil(): void {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/perfil']);
    } else {
      this.authModalService.open('login');
    }
  }

  buscarCatalogo(): void {
    const queryParams: Record<string, string> = {};

    if (this.terminoBusqueda.trim()) {
      queryParams['q'] = this.terminoBusqueda.trim();
    }

    if (this.categoriaSeleccionada !== 'Todo') {
      queryParams['categoria'] = this.categoriaSeleccionada;
    }

    this.router.navigate(['/catalogo'], { queryParams });
  }

  seleccionarCategoria(cat: string): void {
    const queryParams: Record<string, string> = {};

    if (cat !== 'Todo') {
      queryParams['categoria'] = cat;
    }

    this.router.navigate(['/catalogo'], { queryParams });
    this.dropdownAbierto = false;
  }

  toggleDropdown(event: Event): void {
    event.stopPropagation();
    this.dropdownAbierto = !this.dropdownAbierto;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    this.dropdownAbierto = false;

    const target = event.target as HTMLElement;
    if (!target.closest('.fc-search-wrapper')) {
      this.mostrarSugerencias = false;
    }
  }

  // ─── Búsqueda con Algolia ─────────────────────────────────────────────────

  /**
   * Llamado en cada tecla del input (para el autocomplete).
   */
  onBusquedaInput(): void {
    this.mostrarSugerencias = true;
    this.busquedaSubject.next(this.terminoBusqueda);
  }

  /**
   * Consulta a Algolia para obtener sugerencias de autocompletado.
   */
  private buscarSugerenciasAlgolia(termino: string): void {
    this.algoliaService.buscar(termino, { hitsPerPage: 5 }).subscribe({
      next: (resultados) => {
        this.sugerencias = resultados;
        this.mostrarSugerencias = resultados.length > 0;
      },
      error: () => {
        this.sugerencias = [];
      }
    });
  }

  /**
   * El usuario seleccionó una sugerencia del dropdown: navega al catálogo
   * con esa búsqueda para ver el producto junto con resultados relacionados.
   */
  seleccionarSugerencia(sugerencia: AlgoliaProducto): void {
    this.terminoBusqueda = sugerencia.nombre;
    this.mostrarSugerencias = false;
    this.sugerencias = [];
    this.router.navigate(['/catalogo/producto'], { queryParams: { producto: sugerencia.idProducto } });
  }

  /**
   * Agrega un producto directamente al carrito desde el dropdown de sugerencias,
   * sin necesidad de navegar al catálogo.
   *
   * Se detiene la propagación del evento click para que NO se dispare
   * seleccionarSugerencia() (que navegaría al catálogo).
   *
   * @param sugerencia producto de Algolia a agregar.
   * @param event evento click del botón "+".
   */
  agregarDesdeSugerencia(sugerencia: AlgoliaProducto, event: MouseEvent): void {
    event.stopPropagation();

    const productoApi: ProductoApi = {
      idProducto: sugerencia.idProducto,
      nombre: sugerencia.nombre,
      descripcion: sugerencia.descripcion ?? '',
      precioVenta: sugerencia.precioVenta,
      categoria: sugerencia.categoriaId
        ? { idCategoria: sugerencia.categoriaId, nombre: sugerencia.categoriaNombre ?? 'General' }
        : null
    };

    this.cartService.add(productoApi);

    // Cerrar el dropdown tras agregar
    this.mostrarSugerencias = false;
    this.sugerencias = [];
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }

  handleCargarReceta(event: Event): void {
    event.preventDefault();
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/cargar-receta']);
    } else {
      this.authModalService.open('login');
    }
  }

  getCartCount(counts: Array<{ cantidad: number }> | null): number {
    return counts?.reduce((total, item) => total + item.cantidad, 0) ?? 0;
  }

  getCartTotal(): string {
    return this.cartService.getTotal().toFixed(2);
  }

  hasDiscount(sug: AlgoliaProducto): boolean {
    return this.cartService.getDiscountInfo(sug).percentage > 0;
  }

  getFinalPrice(sug: AlgoliaProducto): number {
    return this.cartService.getFinalPrice(sug);
  }
}