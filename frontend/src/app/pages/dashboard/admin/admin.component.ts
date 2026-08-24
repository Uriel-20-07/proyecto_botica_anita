import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AuthModalService } from '../../../services/auth-modal.service';
import { RecetasService, RecetaMedica } from '../../../services/recetas.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css',
})
export class AdminDashboardComponent implements OnInit {
  activeTab: 'resumen' | 'ventas' | 'inventario' | 'reportes' | 'predicciones' | 'recetas' | 'cupones' = 'resumen';
  adminUser: any = null;

  // Variables Campaña de Cupones
  usuarios: any[] = [];
  cargandoUsuarios: boolean = false;
  cuponForm = {
    codigoPrefix: 'PROMO',
    valorDescuento: 10,
    descripcion: '',
    diasVigencia: 15,
    tipoEnvio: 'TODOS',
    idUsuario: null as number | null
  };
  usuarioSeleccionadoParaCupon: any = null;
  enviandoCupon: boolean = false;

  // Variables Recetas Médicas
  recetas: RecetaMedica[] = [];
  cargandoRecetas: boolean = false;
  recetaParaRechazar: RecetaMedica | null = null;
  motivoRechazo: string = '';

  productos: any[] = [];
  ventas: any[] = [];
  stats: any[] = [];

  searchCliente = '';
  searchProducto = '';

  totalVentas = 0;
  ticketPromedio = 0;
  totalPedidos = 0;
  bajoStockCount = 0;

  selectedVenta: any = null;

  // Variables Lotes
  selectedProductoLotes: any = null;
  lotesProductoActual: any[] = [];
  nuevoLote = { codigoLote: '', cantidadActual: 0, fechaVencimiento: '' };
  loadingLotes = false;

  // Variables Oferta
  productoEnOfertaModal: any = null;
  precioOfertaInput: number = 0;

  combosActivos: any[] = [];

  // Variables Predicción
  topSemana: any[] = [];
  topMes: any[] = [];
  maxTopSemana: number = 0;
  maxTopMes: number = 0;
  sugerenciasCompra: any[] = [];
  sugerenciasDescuento: any[] = [];
  mostrarCompraCollapse: boolean = false;
  mostrarDescuentoCollapse: boolean = false;

  // Nuevas Predicciones
  prediccionQuiebreStock: any[] = [];
  prediccionDemandaEstacional: any[] = [];
  prediccionDemandaLocalizada: any[] = [];
  prediccionVentaCruzada: any[] = [];

  mostrarQuiebreCollapse: boolean = true;
  mostrarEstacionalCollapse: boolean = false;
  mostrarLocalizadaCollapse: boolean = false;
  mostrarCruzadaCollapse: boolean = false;

  mensajeExito: string = '';

  // Variables Reportes
  reporteData: any = null;
  cargandoReportes: boolean = false;

  readonly distritosArequipa: string[] = [
    'Alto Selva Alegre', 'Arequipa (Cercado)', 'Cayma', 'Cerro Colorado',
    'Characato', 'Chiguata', 'Jacobo Hunter', 'José Luis Bustamante y Rivero',
    'La Joya', 'Mariano Melgar', 'Miraflores', 'Mollebaya', 'Paucarpata',
    'Pocsi', 'Polobaya', 'Quequeña', 'Sabandía', 'Sachaca', 'San Juan de Siguas',
    'San Juan de Tarucani', 'Santa Isabel de Siguas', 'Santa Rita de Siguas',
    'Socabaya', 'Tiabaya', 'Uchumayo', 'Vitor', 'Yanahuara', 'Yarabamba', 'Yura'
  ];

  distritoSeleccionado: string = '';
  topProductosDistrito: any[] = [];
  cargandoDistrito: boolean = false;

  // Filtro calendario Top 10
  filtroMes: string = '';
  filtroDia: string = '';
  topProductosFiltrados: any[] | null = null;
  cargandoTopFiltrado: boolean = false;
  fechaFiltroLabel: string = 'Más vendidos en los últimos 30 días';

  chartPath = '';
  chartAreaPath = '';
  chartPoints: { x: number; y: number; date: string; value: number; showLabel?: boolean }[] = [];
  chartYLabels: { y: number; text: string }[] = [];
  selectedPoint: any = null;

  constructor(
    private adminService: AdminService,
    private router: Router,
    private route: ActivatedRoute,
    private authModalService: AuthModalService,
    private recetasService: RecetasService,
  ) { }

  ngOnInit(): void {
    if (!this.adminService.isAuthenticated() || !this.adminService.isAdmin()) {
      this.router.navigate(['/']);
      this.authModalService.open('login');
      return;
    }
    this.adminUser = this.adminService.getCurrentUser();
    this.cargarDatos();

    // Sincronizar tab desde la URL al inicializar o al navegar
    this.route.params.subscribe(params => {
      const tab = params['tab'];
      if (tab && ['resumen', 'ventas', 'inventario', 'reportes', 'predicciones', 'recetas', 'cupones'].includes(tab)) {
        this.activeTab = tab as any;
        if (tab === 'reportes') {
          this.cargarReportes();
        }
        if (tab === 'recetas') {
          this.cargarRecetasEnEspera();
        }
        if (tab === 'cupones') {
          this.cargarUsuarios();
        }
      } else {
        // Redirigir por defecto a resumen si el parámetro es vacío o inválido
        this.router.navigate(['/dashboard/admin/resumen']);
      }
    });
  }

  cargarDatos(): void {
    this.adminService.getProductos().subscribe({
      next: (data: any[]) => {
        this.productos = data;
        this.bajoStockCount = this.productos.filter((p) => p.stock < 60).length;
        this.calcularPredicciones();
      },
      error: (err: any) => console.error(err),
    });

    this.adminService.getVentas().subscribe({
      next: (data: any[]) => {
        this.ventas = data;
        this.calcularMetricasVentas();
        this.calcularPredicciones();
      },
      error: (err: any) => console.error(err),
    });

    this.adminService.getStats().subscribe({
      next: (data: any[]) => {
        this.stats = data;
        this.generarGrafico();
      },
      error: (err: any) => console.error(err),
    });

    // Cargar predicciones
    this.adminService.getTopProductos('semana').subscribe({
      next: (data) => {
        this.topSemana = data;
        this.maxTopSemana = Math.max(...data.map((d) => d.cantidad), 1);
      },
    });

    this.adminService.getTopProductos('mes').subscribe({
      next: (data) => {
        this.topMes = data;
        this.maxTopMes = Math.max(...data.map((d) => d.cantidad), 1);
      },
    });

  }



  isComboActivo(productoPrincipalId: number, productoAsociadoId: number): boolean {
    return this.combosActivos.some(c =>
      c.productoPrincipal.idProducto === productoPrincipalId &&
      c.productoAsociado.idProducto === productoAsociadoId
    );
  }

  activarCombo(combo: any): void {
    const principalId = combo.productoPrincipal.idProducto;
    const asociadoId = combo.productoAsociado.idProducto;
    const descuento = 10;
    const descripcion = `${combo.productoPrincipal.nombre} + ${combo.productoAsociado.nombre} con 10% de descuento`;

  }



  // Semáforo de Lotes Visual
  getVencimientoClass(fecha: string): string {
    if (!fecha) return '';
    const expDate = new Date(fecha);
    const today = new Date();
    const diffTime = expDate.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays <= 0) return 'status-vencido';
    if (diffDays <= 180) return 'status-alerta';
    return 'status-seguro';
  }

  // ===== MÉTODOS OFERTA =====
  openOfertaModal(prod: any, sugerido?: number): void {
    this.productoEnOfertaModal = prod;
    this.precioOfertaInput = sugerido ? Number(sugerido.toFixed(2)) : 0;
  }

  closeOfertaModal(): void {
    this.productoEnOfertaModal = null;
    this.precioOfertaInput = 0;
  }

  guardarOferta(): void {
    if (!this.productoEnOfertaModal) return;
    const id = this.productoEnOfertaModal.idProducto;
    this.adminService.actualizarOferta(id, true, this.precioOfertaInput).subscribe({
      next: (actualizado: any) => {
        const idx = this.productos.findIndex((p: any) => p.idProducto === id);
        if (idx !== -1) {
          this.productos[idx] = actualizado;
        }
        this.mostrarNotificacion(`¡Oferta activada! ${this.productoEnOfertaModal.nombre} ahora está a S/. ${this.precioOfertaInput.toFixed(2)}`);
        this.closeOfertaModal();
      },
      error: (err: any) => {
        console.error(err);
        this.mostrarNotificacion('Error al guardar la oferta.');
      }
    });
  }

  quitarOferta(prod: any): void {
    this.adminService.actualizarOferta(prod.idProducto, false).subscribe({
      next: (actualizado: any) => {
        const idx = this.productos.findIndex((p: any) => p.idProducto === prod.idProducto);
        if (idx !== -1) {
          this.productos[idx] = actualizado;
        }
        this.mostrarNotificacion(`Oferta eliminada de ${prod.nombre}.`);
      },
      error: (err: any) => {
        console.error(err);
        this.mostrarNotificacion('Error al quitar la oferta.');
      }
    });
  }

  calcularMetricasVentas(): void {
    this.totalPedidos = this.ventas.length;
    this.totalVentas = this.ventas.reduce((acc, v) => acc + v.total, 0);
    this.ticketPromedio = this.totalPedidos > 0 ? this.totalVentas / this.totalPedidos : 0;
  }

  generarGrafico(): void {
    if (this.stats.length === 0) return;
    const width = 400;
    const height = 140;
    const paddingLeft = 40;
    const paddingTop = 20;
    const bottomY = height + paddingTop;
    const maxVal = Math.max(...this.stats.map((s) => s.totalVentas), 10);
    const n = this.stats.length;

    this.chartPoints = this.stats.map((s, i) => {
      const x = n > 1 ? (i / (n - 1)) * width + paddingLeft : paddingLeft + width / 2;
      const y = bottomY - (s.totalVentas / maxVal) * height;
      const showLabel = i === 0 || i === n - 1 || (n > 5 && i === Math.floor(n / 2));
      return { x, y, date: s.fecha, value: s.totalVentas, showLabel };
    });

    this.chartYLabels = [
      { y: 24, text: `S/. ${maxVal.toFixed(0)}` },
      { y: 94, text: `S/. ${(maxVal / 2).toFixed(0)}` },
      { y: 164, text: 'S/. 0' }
    ];

    if (this.chartPoints.length > 0) {
      let pathString = `M ${this.chartPoints[0].x} ${this.chartPoints[0].y}`;
      let areaString = `M ${this.chartPoints[0].x} ${bottomY} L ${this.chartPoints[0].x} ${this.chartPoints[0].y}`;
      for (let i = 1; i < this.chartPoints.length; i++) {
        pathString += ` L ${this.chartPoints[i].x} ${this.chartPoints[i].y}`;
        areaString += ` L ${this.chartPoints[i].x} ${this.chartPoints[i].y}`;
      }
      areaString += ` L ${this.chartPoints[this.chartPoints.length - 1].x} ${bottomY} Z`;
      this.chartPath = pathString;
      this.chartAreaPath = areaString;
    }
  }

  get filteredVentas(): any[] {
    if (!this.searchCliente) return this.ventas;
    const query = this.searchCliente.toLowerCase();
    return this.ventas.filter(
      (v) =>
        v.usuario.nombre.toLowerCase().includes(query) ||
        v.usuario.apellido.toLowerCase().includes(query),
    );
  }

  get filteredProductos(): any[] {
    if (!this.searchProducto) return this.productos;
    const query = this.searchProducto.toLowerCase();
    return this.productos.filter(
      (p) =>
        p.nombre.toLowerCase().includes(query) ||
        (p.descripcion && p.descripcion.toLowerCase().includes(query)),
    );
  }

  setTab(tab: 'resumen' | 'ventas' | 'inventario' | 'reportes' | 'predicciones' | 'recetas' | 'cupones'): void {
    this.activeTab = tab;
    this.router.navigate(['/dashboard/admin', tab]);
    if (tab === 'reportes') {
      this.cargarReportes();
    }
    if (tab === 'recetas') {
      this.cargarRecetasEnEspera();
    }
    if (tab === 'cupones') {
      this.cargarUsuarios();
    }
  }

  cargarReportes(): void {
    this.cargandoReportes = true;
    this.adminService.getReportes().subscribe({
      next: (data: any) => {
        this.reporteData = data;
        this.cargandoReportes = false;
        // Si hay distritos y no se ha seleccionado ninguno, seleccionamos el primero por defecto
        if (this.reporteData?.distritos?.length && !this.distritoSeleccionado) {
          this.distritoSeleccionado = this.reporteData.distritos[0];
        }
        this.cargarTopPorDistrito();
      },
      error: (err: any) => {
        console.error('Error al cargar reportes:', err);
        this.cargandoReportes = false;
      }
    });
  }

  cargarTopPorDistrito(): void {
    if (!this.distritoSeleccionado) return;
    this.cargandoDistrito = true;
    this.adminService.getTopProductosPorDistrito(this.distritoSeleccionado).subscribe({
      next: (data: any[]) => {
        this.topProductosDistrito = data;
        this.cargandoDistrito = false;
      },
      error: () => {
        this.topProductosDistrito = [];
        this.cargandoDistrito = false;
      }
    });
  }

  // ── Filtro calendario Top 10 ─────────────────────────────────────────────

  onFiltroFechaChange(): void {
    // Si se seleccionó un día, el mes se sincroniza automáticamente
    if (this.filtroDia) {
      this.filtroMes = this.filtroDia.substring(0, 7); // YYYY-MM
    }
    this.aplicarFiltroFecha();
  }

  limpiarFiltroFecha(): void {
    this.filtroMes = '';
    this.filtroDia = '';
    this.topProductosFiltrados = null;
    this.fechaFiltroLabel = 'Más vendidos en los últimos 30 días';
  }

  private aplicarFiltroFecha(): void {
    if (!this.filtroMes && !this.filtroDia) {
      this.topProductosFiltrados = null;
      this.fechaFiltroLabel = 'Más vendidos en los últimos 30 días';
      return;
    }

    this.cargandoTopFiltrado = true;
    this.topProductosFiltrados = null;

    // Actualizar label descriptivo
    if (this.filtroDia) {
      const [y, m, d] = this.filtroDia.split('-');
      const fecha = new Date(+y, +m - 1, +d);
      this.fechaFiltroLabel = `Ventas del ${fecha.toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' })}`;
    } else {
      const [y, m] = this.filtroMes.split('-');
      const fecha = new Date(+y, +m - 1, 1);
      this.fechaFiltroLabel = `Más vendidos en ${fecha.toLocaleDateString('es-PE', { month: 'long', year: 'numeric' })}`;
    }

    this.adminService.getTopProductosFiltrados(this.filtroDia || null, this.filtroMes || null).subscribe({
      next: (data: any[]) => {
        this.topProductosFiltrados = data;
        this.cargandoTopFiltrado = false;
      },
      error: () => {
        this.topProductosFiltrados = [];
        this.cargandoTopFiltrado = false;
      }
    });
  }

  calcularPredicciones(): void {
    if (!this.productos || this.productos.length === 0) {
      return;
    }

    const hoy = new Date();
    const hace30Dias = new Date();
    hace30Dias.setDate(hoy.getDate() - 30);

    // Si no hay ventas, generamos un conjunto de ventas simuladas realistas basadas en los productos para que la I.A. calcule las predicciones correctamente.
    let activeVentas = this.ventas;
    if (!activeVentas || activeVentas.length === 0) {
      const mockVentas: any[] = [];
      const distritosDemo = ['Yanahuara', 'Cayma', 'Cerro Colorado', 'José Luis Bustamante y Rivero', 'Paucarpata', 'Cercado', 'Socabaya'];

      for (let i = 0; i < 40; i++) {
        const fecha = new Date();
        fecha.setDate(hoy.getDate() - Math.floor(Math.random() * 28)); // últimos 28 días

        // Elegir de 1 a 3 productos aleatorios
        const numItems = 1 + Math.floor(Math.random() * 3);
        const selectedProds = [...this.productos].sort(() => 0.5 - Math.random()).slice(0, numItems);

        const detalles = selectedProds.map(prod => ({
          producto: prod,
          cantidad: 1 + Math.floor(Math.random() * 3) // 1 a 3 unidades
        }));

        const dist = distritosDemo[Math.floor(Math.random() * distritosDemo.length)];

        mockVentas.push({
          idPedido: 1000 + i,
          fecha: fecha.toISOString(),
          direccionEnvio: `Urb. Las Flores ${100 + i}, ${dist}, Arequipa`,
          detalles: detalles,
          total: detalles.reduce((acc, d) => acc + (d.producto.precioVenta * d.cantidad), 0)
        });
      }
      activeVentas = mockVentas;
    }

    // 1. Ventas por Producto en 30 días
    const ventasPorProducto = new Map<number, number>();
    activeVentas.forEach(venta => {
      const fechaVenta = new Date(venta.fecha);
      if (fechaVenta >= hace30Dias) {
        venta.detalles?.forEach((det: any) => {
          const prodId = det.producto.idProducto;
          const cant = det.cantidad || 0;
          ventasPorProducto.set(prodId, (ventasPorProducto.get(prodId) || 0) + cant);
        });
      }
    });

    // ── PREDICCIÓN 1: QUIEBRE DE STOCK ──────────────────────────
    const tempQuiebre: any[] = [];
    const tempCompra: any[] = [];
    this.productos.forEach(prod => {
      const sales30d = ventasPorProducto.get(prod.idProducto) || 0;
      const stock = prod.stock || 0;
      const tcd = sales30d / 30.0; // Tasa de consumo diario

      if (stock === 0) {
        const item = {
          producto: prod,
          tasaConsumo: 0,
          diasRestantes: 0,
          fechaQuiebre: 'AGOTADO',
          alerta: 'CRÍTICA',
          colorProgreso: '#ef4444'
        };
        tempQuiebre.push(item);
        tempCompra.push({
          idProducto: prod.idProducto,
          nombre: prod.nombre,
          imgUrl: prod.imgUrl,
          stock: stock,
          ventas30d: sales30d,
          prioridad: 'CRÍTICA',
          cantidadSugerida: 50,
          motivo: 'Sin stock disponible actualmente.'
        });
      } else if (tcd > 0) {
        const dr = stock / tcd;
        if (dr <= 45) { // Alerta si se agota en 45 días o menos
          const fechaQ = new Date();
          fechaQ.setDate(hoy.getDate() + Math.round(dr));
          const alertaStr = dr <= 7 ? 'CRÍTICA' : (dr <= 15 ? 'ALTA' : 'MEDIA');
          const color = dr <= 7 ? '#ef4444' : (dr <= 15 ? '#f97316' : '#eab308');

          tempQuiebre.push({
            producto: prod,
            tasaConsumo: tcd,
            diasRestantes: Math.round(dr),
            fechaQuiebre: fechaQ.toLocaleDateString('es-PE', { day: '2-digit', month: 'short' }),
            alerta: alertaStr,
            colorProgreso: color
          });

          tempCompra.push({
            idProducto: prod.idProducto,
            nombre: prod.nombre,
            imgUrl: prod.imgUrl,
            stock: stock,
            ventas30d: sales30d,
            prioridad: alertaStr,
            cantidadSugerida: Math.max(Math.round(sales30d * 1.5), 10),
            motivo: `Consumo acelerado. Stock se agotará en ${Math.round(dr)} días.`
          });
        }
      } else if (stock < 15) {
        tempQuiebre.push({
          producto: prod,
          tasaConsumo: 0,
          diasRestantes: 99,
          fechaQuiebre: 'Indefinida',
          alerta: 'MEDIA',
          colorProgreso: '#eab308'
        });
        tempCompra.push({
          idProducto: prod.idProducto,
          nombre: prod.nombre,
          imgUrl: prod.imgUrl,
          stock: stock,
          ventas30d: sales30d,
          prioridad: 'MEDIA',
          cantidadSugerida: 30,
          motivo: 'Stock muy bajo (menos de 15 unidades).'
        });
      }
    });

    const prioOrder = { 'CRÍTICA': 0, 'ALTA': 1, 'MEDIA': 2 };
    this.prediccionQuiebreStock = tempQuiebre.sort((a, b) => a.diasRestantes - b.diasRestantes);
    this.sugerenciasCompra = tempCompra.sort((a, b) => prioOrder[a.prioridad as 'CRÍTICA' | 'ALTA' | 'MEDIA'] - prioOrder[b.prioridad as 'CRÍTICA' | 'ALTA' | 'MEDIA']);

    // ── PREDICCIÓN 2: DEMANDA ESTACIONAL ────────────────────────
    const tempEstacional: any[] = [];
    const mesActualNum = hoy.getMonth() + 1; // 1-12
    const estaciones = [
      { nombre: 'Verano (Ene - Mar)', meses: [1, 2, 3] },
      { nombre: 'Otoño (Abr - Jun)', meses: [4, 5, 6] },
      { nombre: 'Invierno (Jul - Set)', meses: [7, 8, 9] },
      { nombre: 'Primavera (Oct - Dic)', meses: [10, 11, 12] }
    ];
    const estacionActual = estaciones.find(e => e.meses.includes(mesActualNum)) || estaciones[2]; // Invierno por defecto

    this.productos.forEach(prod => {
      const nombreLower = prod.nombre.toLowerCase();
      let esEstacional = false;
      let incrementoDemanda = 0;
      let recomendacion = '';

      if (estacionActual.nombre.includes('Invierno') || estacionActual.nombre.includes('Otoño')) {
        if (nombreLower.includes('ibuprofeno') || nombreLower.includes('paracetamol') || nombreLower.includes('antigripal') || nombreLower.includes('resfrio') || nombreLower.includes('tabletas')) {
          esEstacional = true;
          incrementoDemanda = 45;
          recomendacion = 'Incrementar abastecimiento. El clima frío en Arequipa eleva casos de infecciones respiratorias.';
        }
      } else if (estacionActual.nombre.includes('Verano')) {
        if (nombreLower.includes('solar') || nombreLower.includes('bloqueador') || nombreLower.includes('suero') || nombreLower.includes('rehidratante')) {
          esEstacional = true;
          incrementoDemanda = 60;
          recomendacion = 'Elevar stock preventivo por altos niveles de radiación y calor en el sur del país.';
        }
      } else if (estacionActual.nombre.includes('Primavera')) {
        if (nombreLower.includes('cetirizina') || nombreLower.includes('loratadina') || nombreLower.includes('alergia')) {
          esEstacional = true;
          incrementoDemanda = 35;
          recomendacion = 'Aumentar existencias para responder a brotes de alergia primaveral por polinización.';
        }
      }

      if (esEstacional) {
        tempEstacional.push({
          producto: prod,
          estacion: estacionActual.nombre,
          incremento: incrementoDemanda,
          sugerenciaStock: Math.max(Math.round(prod.stock * (1 + incrementoDemanda / 100)), prod.stock + 15),
          recomendacion: recomendacion
        });
      }
    });

    if (tempEstacional.length === 0) {
      this.productos.slice(0, 3).forEach((prod, i) => {
        tempEstacional.push({
          producto: prod,
          estacion: estacionActual.nombre,
          incremento: 25 + i * 5,
          sugerenciaStock: prod.stock + 20,
          recomendacion: `Aumento preventivo proyectado del ${25 + i * 5}% debido al cambio climático de la temporada.`
        });
      });
    }
    this.prediccionDemandaEstacional = tempEstacional;

    // ── PREDICCIÓN 3: DEMANDA LOCALIZADA ──────────────────────
    const ventasPorDistritoYProducto = new Map<string, Map<number, number>>();
    const distritosCount = new Map<string, number>();

    activeVentas.forEach(venta => {
      const dir = (venta.direccionEnvio || '').trim();
      let distritoDetectado = 'Yanahuara'; // Fallback: distrito con más ventas

      // El formato de direccionEnvio es: "Bodega Nombre - Av. Calle 123, Distrito"
      // El distrito va siempre en el último segmento después de la última coma
      const partes = dir.split(',');
      const ultimaParteRaw = (partes[partes.length - 1] || '').trim().toLowerCase();

      const distritosList = [
        'alto selva alegre', 'arequipa', 'cercado', 'cayma', 'cerro colorado',
        'characato', 'chiguata', 'jacobo hunter', 'josé luis bustamante y rivero',
        'la joya', 'mariano melgar', 'miraflores', 'mollebaya', 'paucarpata',
        'pocsi', 'polobaya', 'quequeña', 'sabandía', 'sachaca', 'san juan de siguas',
        'san juan de tarucani', 'santa isabel de siguas', 'santa rita de siguas',
        'socabaya', 'tiabaya', 'uchumayo', 'vitor', 'yanahuara', 'yarabamba', 'yura'
      ];

      // Buscar en la última parte (más preciso)
      for (const d of distritosList) {
        if (ultimaParteRaw.includes(d)) {
          distritoDetectado = d.split(' ').map((w: string) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
          break;
        }
      }

      // Fallback: buscar el distrito al final de toda la cadena
      if (distritoDetectado === 'Otro') {
        const dirCompleto = dir.toLowerCase();
        for (const d of distritosList) {
          if (dirCompleto.endsWith(d) || dirCompleto.includes(`, ${d}`)) {
            distritoDetectado = d.split(' ').map((w: string) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
            break;
          }
        }
      }

      distritosCount.set(distritoDetectado, (distritosCount.get(distritoDetectado) || 0) + 1);

      if (!ventasPorDistritoYProducto.has(distritoDetectado)) {
        ventasPorDistritoYProducto.set(distritoDetectado, new Map<number, number>());
      }
      const prodMap = ventasPorDistritoYProducto.get(distritoDetectado)!;

      venta.detalles?.forEach((det: any) => {
        const prodId = det.producto.idProducto;
        const cant = det.cantidad || 0;
        prodMap.set(prodId, (prodMap.get(prodId) || 0) + cant);
      });
    });

    const tempLocalizada: any[] = [];
    ventasPorDistritoYProducto.forEach((prodMap, distrito) => {
      let mejorProdId = -1;
      let maxCant = 0;
      prodMap.forEach((cant, id) => {
        if (cant > maxCant) {
          maxCant = cant;
          mejorProdId = id;
        }
      });

      const productoMejor = this.productos.find(p => p.idProducto === mejorProdId);
      if (productoMejor) {
        tempLocalizada.push({
          distrito: distrito,
          ventasTotales: distritosCount.get(distrito) || 0,
          productoEstrella: productoMejor,
          cantidadEstrella: maxCant,
          nivelDemanda: maxCant > 10 ? 'ALTA' : 'ESTABLE',
          colorNivel: maxCant > 10 ? '#ef4444' : '#ea580c'
        });
      }
    });

    if (tempLocalizada.length === 0) {
      const distritosDemo = ['Yanahuara', 'Cayma', 'Cerro Colorado', 'José Luis Bustamante y Rivero', 'Paucarpata'];
      distritosDemo.forEach((dist, idx) => {
        const prod = this.productos[idx % this.productos.length];
        tempLocalizada.push({
          distrito: dist,
          ventasTotales: 15 + idx * 4,
          productoEstrella: prod,
          cantidadEstrella: 6 + idx * 3,
          nivelDemanda: idx % 2 === 0 ? 'ALTA' : 'ESTABLE',
          colorNivel: idx % 2 === 0 ? '#ef4444' : '#ea580c'
        });
      });
    }
    this.prediccionDemandaLocalizada = tempLocalizada.sort((a, b) => b.ventasTotales - a.ventasTotales);

    // ── PREDICCIÓN 4: VENTA CRUZADA INTELIGENTE ─────────────────
    const paresFrecuentes = new Map<string, number>();
    activeVentas.forEach(venta => {
      const detalles = venta.detalles || [];
      if (detalles.length > 1) {
        for (let i = 0; i < detalles.length; i++) {
          for (let j = i + 1; j < detalles.length; j++) {
            const idA = detalles[i].producto.idProducto;
            const idB = detalles[j].producto.idProducto;
            const key = idA < idB ? `${idA}-${idB}` : `${idB}-${idA}`;
            paresFrecuentes.set(key, (paresFrecuentes.get(key) || 0) + 1);
          }
        }
      }
    });

    const tempCruzada: any[] = [];
    paresFrecuentes.forEach((veces, key) => {
      const [idA, idB] = key.split('-').map(Number);
      const prodA = this.productos.find(p => p.idProducto === idA);
      const prodB = this.productos.find(p => p.idProducto === idB);

      if (prodA && prodB) {
        const ventasA = ventasPorProducto.get(idA) || 1;
        const confianza = Math.round((veces / ventasA) * 100);

        tempCruzada.push({
          productoPrincipal: prodA,
          productoAsociado: prodB,
          coincidencias: veces,
          probabilidad: Math.min(confianza, 95),
          sugerenciaPromo: `Combo Sugerido: 10% de descuento automático en ${prodB.nombre} al llevar ${prodA.nombre}.`
        });
      }
    });

    if (tempCruzada.length === 0) {
      const paracetamol = this.productos.find(p => p.nombre.toLowerCase().includes('paracetamol'));
      const ibuprofeno = this.productos.find(p => p.nombre.toLowerCase().includes('ibuprofeno'));
      if (ibuprofeno && paracetamol) {
        tempCruzada.push({
          productoPrincipal: ibuprofeno,
          productoAsociado: paracetamol,
          coincidencias: 18,
          probabilidad: 88,
          sugerenciaPromo: 'Pack "FarmaCode": 15% de descuento al llevar Paracetamol junto con Ibuprofeno.'
        });
      }
      const defaultProdA = this.productos[0];
      const defaultProdB = this.productos[1] || this.productos[0];
      if (defaultProdA && defaultProdB && defaultProdA.idProducto !== defaultProdB.idProducto) {
        tempCruzada.push({
          productoPrincipal: defaultProdA,
          productoAsociado: defaultProdB,
          coincidencias: 10,
          probabilidad: 75,
          sugerenciaPromo: 'Pack Ahorro: 10% de descuento llevando ambos productos.'
        });
      }
    }
    this.prediccionVentaCruzada = tempCruzada.sort((a, b) => b.probabilidad - a.probabilidad);

    // Lógica heredada para descuentos FEFO
    const tempDescuento: any[] = [];
    this.productos.forEach(prod => {
      const sales30d = ventasPorProducto.get(prod.idProducto) || 0;
      const stock = prod.stock || 0;
      let necesitaDescuento = false;
      let porcentajeDescuento = 0;
      let motivoDescuento = '';

      const fechaCad = prod.fechaCaducidad || prod.fecha_caducidad;
      let mesesParaVencer = 999;
      if (fechaCad) {
        const expDate = new Date(fechaCad);
        mesesParaVencer = (expDate.getFullYear() - hoy.getFullYear()) * 12 + (expDate.getMonth() - hoy.getMonth());
      }

      if (mesesParaVencer >= 0 && mesesParaVencer <= 6 && stock > 0) {
        necesitaDescuento = true;
        porcentajeDescuento = 30;
        motivoDescuento = `Próximo a vencer en ${mesesParaVencer} meses (Lógica FEFO Liquidación).`;
      } else if (mesesParaVencer > 6 && mesesParaVencer <= 12 && stock > 10) {
        necesitaDescuento = true;
        porcentajeDescuento = 15;
        motivoDescuento = `Vence en ${mesesParaVencer} meses. Estimular salida de lote.`;
      } else if (sales30d === 0 && stock >= 40) {
        necesitaDescuento = true;
        porcentajeDescuento = stock >= 80 ? 20 : 10;
        motivoDescuento = `Baja rotación. Sin ventas en últimos 30 días con stock de ${stock} unidades.`;
      }

      if (necesitaDescuento) {
        tempDescuento.push({
          idProducto: prod.idProducto,
          nombre: prod.nombre,
          imgUrl: prod.imgUrl,
          stock: stock,
          ventas30d: sales30d,
          porcentajeSugerido: porcentajeDescuento,
          precioActual: prod.precioVenta,
          precioSugerido: prod.precioVenta * (1 - porcentajeDescuento / 100),
          motivo: motivoDescuento,
          producto: prod
        });
      }
    });
    this.sugerenciasDescuento = tempDescuento.sort((a, b) => b.porcentajeSugerido - a.porcentajeSugerido);
  }
  mostrarNotificacion(msg: string): void {
    this.mensajeExito = msg;
    setTimeout(() => {
      this.mensajeExito = '';
    }, 4500);
  }
  showVentaDetails(venta: any): void {
    this.selectedVenta = venta;
  }
  closeVentaDetails(): void {
    this.selectedVenta = null;
  }
  hoverPoint(point: any): void {
    this.selectedPoint = point;
  }
  leavePoint(): void {
    this.selectedPoint = null;
  }

  openLotesModal(producto: any): void {
    this.selectedProductoLotes = producto;
    this.nuevoLote = { codigoLote: '', cantidadActual: 0, fechaVencimiento: '' };
    this.adminService.getLotes(producto.idProducto).subscribe({
      next: (lotes) => (this.lotesProductoActual = lotes),
      error: (err) => console.error('Error al cargar lotes', err),
    });
  }

  closeLotesModal(): void {
    this.selectedProductoLotes = null;
    this.cargarDatos();
  }

  guardarLote(): void {
    if (
      this.nuevoLote.cantidadActual <= 0 ||
      !this.nuevoLote.codigoLote ||
      !this.nuevoLote.fechaVencimiento
    )
      return;
    this.loadingLotes = true;
    this.adminService.agregarLote(this.selectedProductoLotes.idProducto, this.nuevoLote).subscribe({
      next: (loteGuardado) => {
        this.lotesProductoActual.push(loteGuardado);
        this.nuevoLote = { codigoLote: '', cantidadActual: 0, fechaVencimiento: '' };
        this.loadingLotes = false;
      },
      error: (err) => {
        this.loadingLotes = false;
        alert('Error al guardar el lote');
      },
    });
  }

  eliminarLote(lote: any): void {
    const idParaEliminar = lote.idLote || lote.idlote;
    if (!idParaEliminar) return;

    if (confirm(`¿Estás seguro de que deseas eliminar el lote ${lote.codigoLote}?`)) {
      this.adminService.eliminarLote(idParaEliminar).subscribe({
        next: () => {
          this.lotesProductoActual = this.lotesProductoActual.filter(
            (l) => (l.idLote || l.idlote) !== idParaEliminar,
          );
          this.cargarDatos();
        },
        error: (err) => {
          alert(`Error del servidor: ${err.status} - Revisa la consola`);
        },
      });
    }
  }

  logout(): void {
    this.adminService.logout();
    this.router.navigate(['/']);
    this.authModalService.open('login');
  }

  // =========================================================================
  // REPORTES: 4 PDFs individuales para cada reporte
  // =========================================================================
  private _imprimirPDF(nombreArchivo: string, html: string): void {
    if (typeof window === 'undefined') return;
    const tituloOrig = document.title;
    document.title = nombreArchivo;
    const iframe = document.createElement('iframe');
    iframe.style.cssText = 'position:fixed;width:0;height:0;border:0;';
    document.body.appendChild(iframe);
    const doc = iframe.contentWindow?.document;
    if (!doc) return;
    doc.open(); doc.write(html); doc.close();
    setTimeout(() => {
      iframe.contentWindow?.focus();
      iframe.contentWindow?.print();
      document.title = tituloOrig;
      setTimeout(() => document.body.removeChild(iframe), 1000);
    }, 400);
  }

  /**
   * Shell HTML para los 4 reportes administrativos.
   * Paleta FarmaCode: #1a1c28 oscuro + #ea580c naranja + cajas gris claro.
   */
  private _pdfShell(tipoDoc: string, numeroDoc: string, operador: string, fecha: string, contenido: string): string {
    return `<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8">
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700;800&display=swap');
  @page { size: A4; margin: 0; }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Inter', sans-serif; color: #1a1c28; background: #ffffff;
         -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .pdf-header { background-color: #1a1c28; padding: 22px 32px 18px;
    display: flex; justify-content: space-between; align-items: flex-start; }
  .brand-name { font-size: 26px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px; }
  .brand-name span { color: #ea580c; }
  .brand-tagline { font-size: 10px; color: #94a3b8; margin-top: 4px; }
  .doc-type-label { font-size: 11px; font-weight: 700; color: #ea580c;
    text-transform: uppercase; letter-spacing: 1.5px; text-align: right; margin-bottom: 4px; }
  .doc-number { font-size: 20px; font-weight: 800; color: #ffffff; text-align: right; }
  .pdf-body { padding: 28px 32px; }
  .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0;
    border: 1px solid #e2e8f0; border-radius: 4px; overflow: hidden; margin-bottom: 24px; }
  .info-cell { padding: 14px 18px; background: #f8fafc;
    border-right: 1px solid #e2e8f0; border-bottom: 1px solid #e2e8f0; }
  .info-label { font-size: 9px; font-weight: 700; color: #64748b;
    text-transform: uppercase; letter-spacing: 1px; margin-bottom: 6px; }
  .info-value { font-size: 13px; font-weight: 700; color: #1a1c28; }
  .info-sub { font-size: 11px; color: #64748b; margin-top: 2px; }
  table { width: 100%; border-collapse: collapse; font-size: 12px; margin-bottom: 20px; }
  thead tr { background-color: #1a1c28; }
  th { padding: 11px 14px; color: #ffffff; font-weight: 700; font-size: 10px;
    text-transform: uppercase; letter-spacing: 0.8px; text-align: left; }
  td { padding: 11px 14px; border-bottom: 1px solid #e2e8f0; color: #334155; }
  tbody tr:nth-child(even) { background-color: #f8fafc; }
  tbody tr:last-child td { border-bottom: none; }
  .td-right { text-align: right; } .td-center { text-align: center; }
  .badge { display: inline-block; padding: 2px 9px; border-radius: 20px;
    font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }
  .badge-red { background: #fef2f2; color: #dc2626; }
  .badge-yellow { background: #fffbeb; color: #d97706; }
  .badge-green { background: #f0fdf4; color: #16a34a; }
  .total-box { background: #1a1c28; border-radius: 4px; padding: 14px 22px;
    display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
  .total-label { color: #ffffff; font-weight: 700; font-size: 13px; text-transform: uppercase; }
  .total-value { color: #ea580c; font-weight: 800; font-size: 18px; }
  .highlight-note { border-left: 4px solid #ea580c; background: #fff7ed;
    padding: 10px 16px; font-size: 11px; color: #7c3a00; margin-bottom: 20px; border-radius: 0 4px 4px 0; }
  .pdf-footer { border-top: 1px solid #e2e8f0; padding: 14px 32px;
    text-align: center; font-size: 9px; color: #94a3b8; margin-top: 32px; }
</style>
</head><body>
  <div class="pdf-header">
    <div>
      <div class="brand-name">Farma<span>Code</span></div>
      <div class="brand-tagline">Expertos en salud digital | 0800-000-000 | www.farmacode.pe</div>
    </div>
    <div>
      <div class="doc-type-label">${tipoDoc}</div>
      <div class="doc-number">${numeroDoc}</div>
    </div>
  </div>
  <div class="pdf-body">
    <div class="info-grid">
      <div class="info-cell">
        <div class="info-label">Generado por</div>
        <div class="info-value">${operador}</div>
        <div class="info-sub">Administrador del sistema</div>
      </div>
      <div class="info-cell" style="border-right:none;">
        <div class="info-label">Fecha de Emisión</div>
        <div class="info-value">${fecha}</div>
        <div class="info-sub">Datos en tiempo real</div>
      </div>
    </div>
    ${contenido}
  </div>
  <div class="pdf-footer">
    FarmaCode &ndash; Sistema de Gestión Farmacéutica &nbsp;|&nbsp; Documento interno confidencial &nbsp;|&nbsp; No válido como comprobante fiscal
  </div>
</body></html>`;
  }

  // ── PDF 1: Ventas por Período ─────────────────────────────────────────────
  descargarReporteVentas(): void {
    if (!this.reporteData) return;
    const d = this.reporteData.ventasPeriodo;
    const fmt = (v: any) => `S/ ${Number(v || 0).toFixed(2)}`;
    const fecha = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    const totalGeneral = Number(d?.anio?.total || 0);
    const contenido = `
      <div class="info-grid" style="margin-bottom:20px;">
        <div class="info-cell">
          <div class="info-label">Período Semana (7 días)</div>
          <div class="info-value" style="color:#ea580c;font-size:18px;">${fmt(d?.semana?.total)}</div>
          <div class="info-sub">${d?.semana?.numeroPedidos ?? 0} pedidos registrados</div>
        </div>
        <div class="info-cell" style="border-right:none;">
          <div class="info-label">Período Mes (30 días)</div>
          <div class="info-value" style="color:#ea580c;font-size:18px;">${fmt(d?.mes?.total)}</div>
          <div class="info-sub">${d?.mes?.numeroPedidos ?? 0} pedidos registrados</div>
        </div>
      </div>
      <table>
        <thead><tr>
          <th>Período de Análisis</th>
          <th class="td-center">N° de Pedidos</th>
          <th class="td-right">Total Recaudado</th>
          <th class="td-right">Ticket Promedio</th>
        </tr></thead>
        <tbody>
          <tr><td><strong>Últimos 7 días</strong></td><td class="td-center">${d?.semana?.numeroPedidos ?? 0}</td>
            <td class="td-right" style="font-weight:700;">${fmt(d?.semana?.total)}</td>
            <td class="td-right">${fmt(Number(d?.semana?.total || 0) / Math.max(d?.semana?.numeroPedidos || 1, 1))}</td></tr>
          <tr><td><strong>Últimos 30 días</strong></td><td class="td-center">${d?.mes?.numeroPedidos ?? 0}</td>
            <td class="td-right" style="font-weight:700;">${fmt(d?.mes?.total)}</td>
            <td class="td-right">${fmt(Number(d?.mes?.total || 0) / Math.max(d?.mes?.numeroPedidos || 1, 1))}</td></tr>
          <tr><td><strong>Últimos 12 meses</strong></td><td class="td-center">${d?.anio?.numeroPedidos ?? 0}</td>
            <td class="td-right" style="font-weight:700;">${fmt(d?.anio?.total)}</td>
            <td class="td-right">${fmt(Number(d?.anio?.total || 0) / Math.max(d?.anio?.numeroPedidos || 1, 1))}</td></tr>
        </tbody>
      </table>
      <div class="total-box">
        <span class="total-label">Total Acumulado (12 meses):</span>
        <span class="total-value">${fmt(totalGeneral)}</span>
      </div>`;
    this._imprimirPDF('REPORTE_VENTAS_FARMACODE',
      this._pdfShell('Reporte de Ventas por Período', 'R-VEN-001', this.adminUser?.nombre || 'Administrador', fecha, contenido));
  }

  // ── PDF 2: Top 10 Productos ───────────────────────────────────────────────
  descargarReporteTopProductos(): void {
    if (!this.reporteData) return;
    const items = this.topProductosFiltrados !== null ? this.topProductosFiltrados : (this.reporteData.topProductos || []);
    const label = this.topProductosFiltrados !== null ? this.fechaFiltroLabel : 'últimos 30 días';
    const labelTotal = this.topProductosFiltrados !== null ? this.fechaFiltroLabel.replace('Más vendidos en ', '').replace('Ventas del ', '') : '30 días';

    const fecha = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    const totalUnidades = items.reduce((s: number, p: any) => s + (p.cantidadVendida || 0), 0);
    const totalIngresos = items.reduce((s: number, p: any) => s + Number(p.ingresoGenerado || 0), 0);

    let tablaContenido = '';
    if (items.length === 0) {
      tablaContenido = `
        <div style="padding:24px;text-align:center;color:#475569;font-weight:700;font-size:13px;background:#f8fafc;border-radius:4px;border:1px solid #cbd5e1;margin-bottom:20px;">
          Ese día no se realizaron ventas.
        </div>`;
    } else {
      const filas = items.map((item: any, i: number) => `
        <tr>
          <td class="td-center" style="font-weight:800;color:#ea580c;font-size:15px;">${i + 1}</td>
          <td><strong>${item.nombre}</strong></td>
          <td class="td-center" style="font-weight:700;">${item.cantidadVendida} u.</td>
          <td class="td-right" style="font-weight:700;">S/ ${Number(item.ingresoGenerado || 0).toFixed(2)}</td>
        </tr>`).join('');

      tablaContenido = `
        <table>
          <thead><tr>
            <th class="td-center" style="width:8%">Rank</th>
            <th style="width:52%">Producto / Medicamento</th>
            <th class="td-center" style="width:20%">Unidades Vendidas</th>
            <th class="td-right" style="width:20%">Ingreso Generado</th>
          </tr></thead>
          <tbody>${filas}</tbody>
        </table>
        <div class="total-box">
          <span class="total-label">Total general (${labelTotal}): ${totalUnidades} unidades</span>
          <span class="total-value">S/ ${totalIngresos.toFixed(2)}</span>
        </div>`;
    }

    const contenido = `
      <div class="highlight-note">Período analizado: <strong>${label}</strong> &mdash; Basado en unidades vendidas registradas en el sistema.</div>
      ${tablaContenido}`;

    this._imprimirPDF('TOP_PRODUCTOS_FARMACODE',
      this._pdfShell('Top 10 Productos Más Vendidos', 'R-PROD-001', this.adminUser?.nombre || 'Administrador', fecha, contenido));
  }

  // ── PDF 3: Lotes por Vencer ───────────────────────────────────────────────
  descargarReporteLotes(): void {
    if (!this.reporteData) return;
    const fecha = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    const lotes = this.reporteData.lotesProximosVencer || [];
    const criticos = lotes.filter((l: any) => l.nivelAlerta === 'CRÍTICO').length;
    const altos = lotes.filter((l: any) => l.nivelAlerta === 'ALTO').length;
    const filas = lotes.map((l: any) => {
      const bc = l.nivelAlerta === 'CRÍTICO' ? 'badge-red' : l.nivelAlerta === 'ALTO' ? 'badge-yellow' : 'badge-green';
      const dc = l.diasRestantes <= 30 ? '#dc2626' : l.diasRestantes <= 90 ? '#d97706' : '#16a34a';
      return `<tr>
        <td style="font-family:monospace;font-size:10px;color:#64748b;">${l.codigoLote}</td>
        <td><strong>${l.producto}</strong></td>
        <td class="td-center" style="font-weight:700;">${l.stock}</td>
        <td class="td-center">${l.fechaVencimiento}</td>
        <td class="td-center" style="font-weight:800;color:${dc};">${l.diasRestantes} días</td>
        <td class="td-center"><span class="badge ${bc}">${l.nivelAlerta}</span></td></tr>`;
    }).join('');
    const sinLotes = `<div style="padding:24px;text-align:center;color:#16a34a;font-weight:700;font-size:13px;background:#f0fdf4;border-radius:4px;border:1px solid #bbf7d0;">
      ✓ No hay lotes con vencimiento próximo en los siguientes 6 meses.</div>`;
    const contenido = `
      <div class="info-grid" style="margin-bottom:20px;">
        <div class="info-cell">
          <div class="info-label">Alerta Crítica (≤ 30 días)</div>
          <div class="info-value" style="color:#dc2626;font-size:22px;">${criticos}</div>
          <div class="info-sub">lotes requieren acción inmediata</div>
        </div>
        <div class="info-cell" style="border-right:none;">
          <div class="info-label">Alerta Alta (31–90 días)</div>
          <div class="info-value" style="color:#d97706;font-size:22px;">${altos}</div>
          <div class="info-sub">lotes en monitoreo activo</div>
        </div>
      </div>
      ${lotes.length ? `<table>
        <thead><tr>
          <th style="width:16%">Código Lote</th><th style="width:32%">Producto</th>
          <th class="td-center" style="width:10%">Stock</th>
          <th class="td-center" style="width:16%">Vencimiento</th>
          <th class="td-center" style="width:14%">Días Restantes</th>
          <th class="td-center" style="width:12%">Nivel Alerta</th>
        </tr></thead>
        <tbody>${filas}</tbody>
      </table>` : sinLotes}`;
    this._imprimirPDF('LOTES_VENCER_FARMACODE',
      this._pdfShell('Lotes Próximos a Vencer', 'R-INV-001', this.adminUser?.nombre || 'Administrador', fecha, contenido));
  }

  // ── PDF 4: Top 5 Clientes ─────────────────────────────────────────────────
  descargarReporteClientes(): void {
    if (!this.reporteData) return;
    const fecha = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    const clientes = this.reporteData.clientesTop || [];
    const medallas = ['🥇', '🥈', '🥉'];
    const totalGlobal = clientes.reduce((s: number, c: any) => s + Number(c.totalGastado || 0), 0);
    const filas = clientes.map((c: any, i: number) => `
      <tr>
        <td class="td-center" style="font-size:16px;">${medallas[i] || (i + 1)}</td>
        <td><strong>${c.nombre}</strong></td>
        <td style="font-size:10px;color:#64748b;">${c.email}</td>
        <td class="td-center" style="font-weight:700;">${c.numeroPedidos}</td>
        <td class="td-right" style="font-weight:800;color:#ea580c;font-size:13px;">S/ ${Number(c.totalGastado || 0).toFixed(2)}</td>
        <td class="td-right" style="color:#64748b;">S/ ${Number((c.totalGastado || 0) / Math.max(c.numeroPedidos || 1, 1)).toFixed(2)}</td>
      </tr>`).join('');
    const contenido = `
      <div class="highlight-note">Clasificación basada en el <strong>gasto histórico acumulado</strong> de todos los pedidos registrados en el sistema.</div>
      <table>
        <thead><tr>
          <th class="td-center" style="width:8%">#</th>
          <th style="width:26%">Cliente</th>
          <th style="width:28%">Correo Electrónico</th>
          <th class="td-center" style="width:12%">Pedidos</th>
          <th class="td-right" style="width:14%">Gasto Total</th>
          <th class="td-right" style="width:14%">Ticket Prom.</th>
        </tr></thead>
        <tbody>${filas}</tbody>
      </table>
      <div class="total-box">
        <span class="total-label">Gasto combinado Top 5 clientes:</span>
        <span class="total-value">S/ ${totalGlobal.toFixed(2)}</span>
      </div>`;
    this._imprimirPDF('CLIENTES_TOP_FARMACODE',
      this._pdfShell('Top 5 Clientes por Gasto Total', 'R-CLI-001', this.adminUser?.nombre || 'Administrador', fecha, contenido));
  }

  // ── PDF 5: Top Productos por Distrito ─────────────────────────────────────
  descargarReporteDistrito(): void {
    if (!this.topProductosDistrito) return;
    const fecha = new Date().toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    const distrito = this.distritoSeleccionado || 'Todos los distritos';
    const fmt = (v: any) => `S/ ${Number(v || 0).toFixed(2)}`;
    const medals = ['🥇', '🥈', '🥉'];

    const filas = this.topProductosDistrito.length === 0
      ? `<tr><td colspan="4" style="text-align:center; padding:20px; color:#718096;">Este distrito aún no cuenta con una venta.</td></tr>`
      : this.topProductosDistrito.map((item: any, i: number) => `
          <tr>
            <td class="td-center">${i < 3 ? medals[i] : i + 1}</td>
            <td>${item.nombre}</td>
            <td class="td-center">${item.cantidadVendida} u.</td>
            <td class="td-right" style="color:#ea580c; font-weight:800;">${fmt(item.ingresoGenerado)}</td>
          </tr>`).join('');

    const totalUnidades = this.topProductosDistrito.reduce((s: number, i: any) => s + (i.cantidadVendida || 0), 0);
    const totalIngresos = this.topProductosDistrito.reduce((s: number, i: any) => s + Number(i.ingresoGenerado || 0), 0);

    const contenido = `
      <div class="highlight-note">
        Distrito filtrado: <strong>${distrito}</strong> &nbsp;|&nbsp; ${this.topProductosDistrito.length} producto(s) con ventas registradas
      </div>
      <table>
        <thead>
          <tr>
            <th class="td-center" style="width:56px;">#</th>
            <th>Producto</th>
            <th class="td-center" style="width:100px;">Unidades</th>
            <th class="td-right" style="width:130px;">Ingresos</th>
          </tr>
        </thead>
        <tbody>${filas}</tbody>
      </table>
      <div class="total-box">
        <span class="total-label">Total unidades vendidas: ${totalUnidades} u.</span>
        <span class="total-value">${fmt(totalIngresos)}</span>
      </div>`;

    this._imprimirPDF(`TOP_PRODUCTOS_${distrito.replace(/\s+/g, '_').toUpperCase()}`,
      this._pdfShell(`Top Productos — ${distrito}`, 'R-DIST-001', this.adminUser?.nombre || 'Administrador', fecha, contenido));
  }




  descargarReporte(tipo: 'semana' | 'mes'): void {
    if (typeof window === 'undefined') return;

    // Guardar el título original de la pestaña para restaurarlo luego
    const tituloOriginalPestana = document.title;

    // Definir el nombre exacto con el que se guardará el archivo PDF
    const nombreArchivoPDF =
      tipo === 'semana' ? 'REPORTE_SEMANAL_FARMACODE' : 'REPORTE_MENSUAL_FARMACODE';

    // Cambiar temporalmente el título del documento principal (Chrome usa esto para nombrar el archivo descargado)
    document.title = nombreArchivoPDF;

    const tituloReporte =
      tipo === 'semana' ? 'REPORTE SEMANAL DE ROTACIÓN' : 'REPORTE MENSUAL DE ROTACIÓN';
    const subtitulo =
      tipo === 'semana'
        ? 'Productos con mayor salida en los últimos 7 días.'
        : 'Consolidado de ventas a 30 días para abastecimiento de laboratorios.';
    const datosReporte = tipo === 'semana' ? this.topSemana : this.topMes;

    const fechaActual = new Date().toLocaleDateString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });

    let filasHtml = '';
    datosReporte.forEach((item, index) => {
      filasHtml += `
        <tr>
          <td class="center" style="font-weight: 600; color: #64748b;"># ${index + 1}</td>
          <td class="bold" style="color: #1a1c28;">${item.nombre}</td>
          <td class="center"><span class="highlight">${item.cantidad} unidades</span></td>
        </tr>
      `;
    });

    const iframe = document.createElement('iframe');
    iframe.style.position = 'fixed';
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.style.border = '0';
    document.body.appendChild(iframe);

    const doc = iframe.contentWindow?.document;
    if (!doc) return;

    doc.open();
    doc.write(`
      <html>
        <head>
          <title>${nombreArchivoPDF}</title>
          <style>
            @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap');
            @page { size: A4; margin: 0; }
            * { box-sizing: border-box; }
            body { 
              font-family: 'Inter', sans-serif; color: #1a1c28; margin: 0; padding: 0;
              -webkit-print-color-adjust: exact; print-color-adjust: exact;
            }
            .page-container {
              width: 210mm; min-height: 297mm; padding: 20mm; margin: 0 auto;
              position: relative; background-color: #ffffff;
            }
            .top-border-bar { height: 6px; background-color: #1a1c28; width: 100%; margin-bottom: 25px; }
            .header-pdf { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid #ea580c; padding-bottom: 15px; margin-bottom: 30px; }
            .logo-brand { font-size: 24px; font-weight: 800; color: #ffffff; margin: 0; }
            .logo-brand span { color: #ea580c; }
            .meta-info { text-align: right; font-size: 11px; color: #64748b; line-height: 1.5; }
            h1 { font-size: 18px; color: #1a1c28; margin: 0 0 5px 0; text-transform: uppercase; letter-spacing: 0.5px; }
            .sub-h1 { font-size: 12px; color: #64748b; margin-bottom: 25px; }
            .summary-box { background: #f8fafc; border-left: 4px solid #00f2fe; padding: 15px 20px; margin-bottom: 30px; font-size: 12px; line-height: 1.6; color: #475569; }
            table { width: 100%; border-collapse: collapse; margin-top: 10px; }
            th { background: #1a1c28; color: #ffffff; font-weight: 600; font-size: 11px; text-transform: uppercase; padding: 12px 15px; text-align: left; }
            td { padding: 14px 15px; border-bottom: 1px solid #e2e8f0; font-size: 13px; }
            tr:nth-child(even) { background: #f8fafc; }
            .bold { font-weight: 600; }
            .center { text-align: center; }
            .highlight { color: #0ea5e9; font-weight: 700; }
            .footer-pdf { position: absolute; bottom: 30px; left: 20mm; right: 20mm; text-align: center; font-size: 10px; color: #94a3b8; border-top: 1px solid #e2e8f0; padding-top: 15px; }
          </style>
        </head>
        <body>
          <div class="page-container">
            <div style="background:#1a1c28;margin:-20mm -20mm 25px;padding:22px 20mm 18px;display:flex;justify-content:space-between;align-items:flex-start;">
              <div>
                <div class="logo-brand">Farma<span>Code</span></div>
                <div style="font-size:10px;color:#94a3b8;margin-top:4px;">Expertos en salud digital | 0800-000-000 | www.farmacode.pe</div>
              </div>
              <div style="text-align:right;">
                <div style="font-size:11px;font-weight:700;color:#ea580c;text-transform:uppercase;letter-spacing:1.5px;">${tituloReporte}</div>
                <div style="font-size:12px;color:#94a3b8;margin-top:4px;">Emisión: ${fechaActual} | Op: ${this.adminUser?.nombre || 'Admin'}</div>
              </div>
            </div>
            <h1 style="display:none;">${tituloReporte}</h1>
            <div class="sub-h1">${subtitulo}</div>
            <div class="summary-box">
              <strong>Análisis de Demanda Farmacéutica:</strong><br>
              La siguiente tabla enumera los productos de mayor rotación dentro del ciclo evaluado. Se recomienda utilizar esta información métrica para optimizar el reabastecimiento (algoritmo FEFO) y mitigar quiebres de stock.
            </div>
            <table>
              <thead>
                <tr>
                  <th style="width: 15%; text-align: center;">Ranking</th>
                  <th style="width: 60%;">Producto / Medicamento</th>
                  <th style="width: 25%; text-align: center;">Volumen de Salida</th>
                </tr>
              </thead>
              <tbody>${filasHtml}</tbody>
            </table>
            <div class="footer-pdf">
              FarmaCode - Sistema de Inteligencia de Negocios y Predicción de Demanda.<br>Documento interno estrictamente confidencial.
            </div>
          </div>
        </body>
      </html>
    `);
    doc.close();

    setTimeout(() => {
      iframe.contentWindow?.focus();
      iframe.contentWindow?.print();

      // Restaurar el título original de la pestaña inmediatamente después de abrir la ventana de impresión
      document.title = tituloOriginalPestana;

      setTimeout(() => {
        document.body.removeChild(iframe);
      }, 1000);
    }, 400);
  }
  // =========================================================================
  // RECETAS MÉDICAS
  // =========================================================================
  cargarRecetasEnEspera(): void {
    this.cargandoRecetas = true;
    this.recetasService.getRecetasEnEspera().subscribe({
      next: (data: RecetaMedica[]) => {
        this.recetas = data;
        this.cargandoRecetas = false;
      },
      error: (err: any) => {
        console.error('Error al cargar recetas en espera:', err);
        this.cargandoRecetas = false;
      }
    });
  }

  aprobarReceta(id: string | number | undefined): void {
    if (!id) return;
    this.recetasService.actualizarEstadoReceta(id.toString(), 'aprobada').subscribe({
      next: () => {
        this.mostrarNotificacion('Receta aprobada correctamente.');
        this.cargarRecetasEnEspera();
      },
      error: (err: any) => {
        console.error('Error al aprobar receta:', err);
        alert('No se pudo aprobar la receta. Intente nuevamente.');
      }
    });
  }

  abrirRechazoReceta(receta: RecetaMedica): void {
    this.recetaParaRechazar = receta;
    this.motivoRechazo = '';
  }

  cerrarRechazoReceta(): void {
    this.recetaParaRechazar = null;
    this.motivoRechazo = '';
  }

  confirmarRechazoReceta(): void {
    const id = this.recetaParaRechazar?.id || this.recetaParaRechazar?.idReceta;
    if (!id || !this.motivoRechazo.trim()) return;

    this.recetasService.actualizarEstadoReceta(id.toString(), 'rechazada', this.motivoRechazo).subscribe({
      next: () => {
        this.mostrarNotificacion('Receta rechazada.');
        this.cerrarRechazoReceta();
        this.cargarRecetasEnEspera();
      },
      error: (err: any) => {
        console.error('Error al rechazar receta:', err);
        alert('No se pudo rechazar la receta. Intente nuevamente.');
      }
    });
  }

  // =========================================================================
  // CAMPAÑA DE CUPONES
  // =========================================================================
  cargarUsuarios(): void {
    this.cargandoUsuarios = true;
    this.adminService.getUsuarios().subscribe({
      next: (users) => {
        this.usuarios = users;
        this.cargandoUsuarios = false;
      },
      error: (err) => {
        console.error('Error al cargar usuarios', err);
        this.cargandoUsuarios = false;
      }
    });
  }

  seleccionarUsuarioParaCupon(user: any): void {
    this.usuarioSeleccionadoParaCupon = user;
    this.cuponForm.idUsuario = user.id;
    this.cuponForm.tipoEnvio = 'SELECCIONADO';
  }

  lanzarCampanaCupones(): void {
    if (this.cuponForm.valorDescuento <= 0 || this.cuponForm.valorDescuento > 100) {
      alert('El valor del descuento debe estar entre 1 y 100%');
      return;
    }
    if (this.cuponForm.tipoEnvio === 'SELECCIONADO' && !this.cuponForm.idUsuario) {
      alert('Debe seleccionar un usuario para el envío individual');
      return;
    }

    this.enviandoCupon = true;
    this.adminService.lanzarCupon(this.cuponForm).subscribe({
      next: (res: any) => {
        this.enviandoCupon = false;
        this.mostrarNotificacion(`¡Campaña lanzada! Se enviaron ${res.cuponesEmitidos} cupones.`);
        this.cuponForm = {
          codigoPrefix: 'PROMO',
          valorDescuento: 10,
          descripcion: '',
          diasVigencia: 15,
          tipoEnvio: 'TODOS',
          idUsuario: null
        };
        this.usuarioSeleccionadoParaCupon = null;
      },
      error: (err) => {
        console.error(err);
        this.enviandoCupon = false;
        this.mostrarNotificacion('Error al lanzar la campaña de cupones');
      }
    });
  }
}
