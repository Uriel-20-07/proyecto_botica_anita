import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CatalogoService, MarcaApi } from '../../services/catalogo.service';

// Mapa de categorías por nombre de marca
const CATEGORIA_MAP: Record<string, string> = {
  // Medicamentos
  'ALPHARMA':             'Medicamentos',
  'H&M':                  'Medicamentos',
  'Laboratorio Hormonas': 'Medicamentos',
  'Bayer':                'Medicamentos',
  'Panadol':              'Medicamentos',
  'Dolocorladran':        'Medicamentos',
  'IqFarma':              'Medicamentos',
  'Vick':                 'Medicamentos',
  'ION':                  'Medicamentos',
  'Laboratorio Portugal': 'Medicamentos',
  'Omron':                'Medicamentos',
  'ChoiseMMed':           'Medicamentos',
  'Accu-Chek':            'Medicamentos',
  'Alkofarma':            'Medicamentos',
  'Top Glove':            'Medicamentos',
  'CompMist':             'Medicamentos',
  'BotiquinGenerico':     'Medicamentos',
  'SafetyMed':            'Medicamentos',
  'Enterogermina':        'Medicamentos',

  // Vitaminas
  'Redoxon':              'Vitaminas',
  'Centrum':              'Vitaminas',
  'Natura Made':          'Vitaminas',
  'Caltrate':             'Vitaminas',
  'Bion3':                'Vitaminas',
  'Pharmaton':            'Vitaminas',
  'Genacol':              'Vitaminas',
  'Magnesol':             'Vitaminas',
  'Ferrograd':            'Vitaminas',

  // Dermocosmética
  'Nivea':                'Dermocosmética',
  'Eucerin':              'Dermocosmética',
  'Maybelline New York':  'Dermocosmética',
  'Garnier':              'Dermocosmética',
  'Loreal Paris':         'Dermocosmética',
  'eGo':                  'Dermocosmética',
  'Antonio Banderas':     'Dermocosmética',
  'Neutrogena':           'Dermocosmética',

  // Bebés
  'Huggies':              'Bebés',
  'Pampers':              'Bebés',
  'NAN':                  'Bebés',
  'Enfamil':              'Bebés',
  "Johnson's baby":       'Bebés',
  'Hipoglos':             'Bebés',
  'Philips Avent':        'Bebés',

  // Cuidado personal
  'Axe':                  'Cuidado personal',
  'Rexona':               'Cuidado personal',
  'Head & Shoulders':     'Cuidado personal',
  'Pantene':              'Cuidado personal',
  'Dove':                 'Cuidado personal',
  'Colgate':              'Cuidado personal',
  'Oral B':               'Cuidado personal',
  'Elite':                'Cuidado personal',
  'Nosotras':             'Cuidado personal',
  'Protex':               'Cuidado personal',
};

@Component({
  selector: 'app-marcas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './marcas.html',
  styleUrls: ['./marcas.css']
})
export class MarcasComponent implements OnInit {

  marcas: MarcaApi[] = [];
  marcasFiltradas: MarcaApi[] = [];

  /** Cantidad de productos por marca (id de marca → cantidad). */
  conteoPorMarca: Record<number, number> = {};
  /** Chip de categoría deducido de los productos (para marcas que no están en CATEGORIA_MAP). */
  private categoriaDerivada: Record<number, string> = {};

  categorias: string[] = [
    'Todas',
    'Vitaminas',
    'Dermocosmética',
    'Bebés',
    'Cuidado personal',
    'Medicamentos'
  ];

  categoriaActiva: string = 'Todas';

  constructor(
    private catalogoService: CatalogoService,
    private router: Router
  ) {}

  ngOnInit(): void {
    forkJoin({
      marcas: this.catalogoService.getMarcas(),
      productos: this.catalogoService.getProductos().pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ marcas, productos }) => {
        for (const p of productos ?? []) {
          const id = p.marca?.idMarca;
          if (id == null) continue;
          this.conteoPorMarca[id] = (this.conteoPorMarca[id] ?? 0) + 1;
          if (!this.categoriaDerivada[id]) {
            this.categoriaDerivada[id] = this.chipDeCategoria(p.categoria?.nombre);
          }
        }
        // Solo se muestran las marcas que tienen productos (si aún no llega la marca en los productos, se muestran todas)
        const hayConteo = Object.keys(this.conteoPorMarca).length > 0;
        this.marcas = hayConteo ? marcas.filter((m) => (this.conteoPorMarca[m.idMarca] ?? 0) > 0) : marcas;
        this.marcasFiltradas = this.marcas; // por defecto muestra todas
      },
      error: (err) => {
        console.error('Error al obtener marcas:', err);
      }
    });
  }

  /** Traduce la categoría de la base de datos al chip de esta página. */
  private chipDeCategoria(nombre?: string): string {
    switch ((nombre ?? '').toUpperCase()) {
      case 'MEDICAMENTOS':
      case 'EQUIPOS MÉDICOS':
        return 'Medicamentos';
      case 'CUIDADO PERSONAL':
        return 'Cuidado personal';
      case 'BELLEZA':
        return 'Dermocosmética';
      case 'BEBÉ':
        return 'Bebés';
      case 'VITAMINAS / SUPLEMENTOS':
        return 'Vitaminas';
      default:
        return '';
    }
  }

  filtrarPorCategoria(categoria: string): void {
    this.categoriaActiva = categoria;

    if (categoria === 'Todas') {
      this.marcasFiltradas = this.marcas;
    } else {
      this.marcasFiltradas = this.marcas.filter(marca => {
        const cat = CATEGORIA_MAP[marca.nombre] ?? this.categoriaDerivada[marca.idMarca];
        return cat === categoria;
      });
    }
  }

  filtrarPorMarca(idMarca: number): void {
    this.router.navigate(['/catalogo'], { queryParams: { marca: idMarca } });
  }
}