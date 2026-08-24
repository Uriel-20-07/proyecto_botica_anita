import { Routes } from '@angular/router';
import { InicioComponent } from './pages/inicio';
import { BlogComponent } from './pages/blog/blog.component';
import { CatalogoComponent } from './pages/catalogo/catalogo';
import { CarritoComponent } from './pages/carrito/carrito';
import { MarcasComponent } from './pages/marcas/marcas';
import { NosotrosComponent } from './pages/nosotros/nosotros';
import { PerfilComponent } from './pages/perfil/perfil';
import { PagoComponent } from './pages/pago/pago';
import { AdminDashboardComponent } from './pages/dashboard/admin/admin.component';
import { VendedorDashboardComponent } from './pages/dashboard/vendedor/vendedor.component';
import { RecuperarContraseñaComponent } from './components/recuperar-contrasena/recuperar-contrasena.component';
import { PedidosComponent } from './pages/pedidos/pedidos';
import { ProductoDetalleComponent } from './pages/producto-detalle/producto-detalle';
import { FavoritosComponent } from './pages/favoritos/favoritos';
import { SeguimientoPedidoComponent } from './pages/seguimiento-pedido/seguimiento-pedido';
import { CargaRecetaComponent } from './pages/carga-receta/carga-receta.component';


/**
 * Configuración de rutas de la aplicación Angular.
 * 
 * Define el mapa de navegación que conecta URLs con componentes.
 * Se usa con RouterModule en app.config.ts.
 * 
 * Rutas definidas:
 * - /                     → Página de inicio (InicioComponent).
 * - /catalogo             → Catálogo de productos con filtros.
 * - /carrito              → Carrito de compras del usuario.
 * - /pago                 → Proceso de pago (requiere items en el carrito).
 * - /ofertas              → Página de ofertas y promociones.
 * - /marcas               → Marcas disponibles en la farmacia.
 * - /nosotros             → Página institucional "Sobre nosotros".
 * - /perfil               → Perfil del usuario autenticado.
 * - /blog                 → Blog de artículos de salud.
 * - /recuperar-contrasena → Formulario de recuperación de contraseña.
 * - /reset-password/:token → Formulario para establecer nueva contraseña.
 * - /dashboard/admin      → Panel de administración completo.
 * - /dashboard/vendedor   → Panel del vendedor (funciones limitadas).
 * - /login                → Redirige a inicio (login es modal).
 * - /registro             → Redirige a inicio (registro es modal).
 * - /favoritos            → Redirige a inicio (funcionalidad pendiente).
 * - /pedidos              → Redirige a inicio (funcionalidad pendiente).
 * - **                    → Redirige a inicio (ruta no encontrada).
 */
export const routes: Routes = [
  // Dashboards de administración (rutas sin guard, el componente maneja el auth)
  { path: 'dashboard/admin', redirectTo: 'dashboard/admin/resumen', pathMatch: 'full' },
  { path: 'dashboard/admin/:tab', component: AdminDashboardComponent },
  { path: 'dashboard/vendedor', component: VendedorDashboardComponent },

  // Página principal
  { path: '', component: InicioComponent, pathMatch: 'full' },
  
  // Flujo de recetas médicas
  { path: 'cargar-receta', component: CargaRecetaComponent },

  // Páginas del catálogo y comercio
  { path: 'blog', component: BlogComponent },
  { path: 'catalogo', component: CatalogoComponent },
  { path: 'catalogo/producto', component: ProductoDetalleComponent },
  { path: 'catalogo/producto/:id', component: ProductoDetalleComponent },
  { path: 'carrito', component: CarritoComponent },
  { path: 'pago', component: PagoComponent }, // Proceso de checkout

  // Páginas informativas
  { path: 'marcas', component: MarcasComponent },
  { path: 'nosotros', component: NosotrosComponent },

  // Perfil del usuario autenticado
  { path: 'perfil', component: PerfilComponent },

  // Flujo de recuperación de contraseña
  { path: 'recuperar-contrasena', component: RecuperarContraseñaComponent },
  { path: 'reset-password/:token', component: RecuperarContraseñaComponent }, // Token en la URL

  // Rutas que redirigen a inicio (funcionalidades implementadas como modales o pendientes)
  { path: 'login', redirectTo: '', pathMatch: 'full' },     // Login es un modal, no página
  { path: 'registro', redirectTo: '', pathMatch: 'full' },  // Registro es un modal, no página
  { path: 'favoritos', component: FavoritosComponent },
  { path: 'pedidos', component: PedidosComponent },
  { path: 'pedidos/pedido/:id/seguimiento', component: SeguimientoPedidoComponent},

  // Wildcard: cualquier ruta no definida → redirige a inicio
  { path: '**', redirectTo: '', pathMatch: 'full' },
];
