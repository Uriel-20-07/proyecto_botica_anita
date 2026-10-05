import { Routes } from '@angular/router';
import { InicioComponent } from './inicio';
import { CatalogoComponent } from './catalogo/catalogo';
import { BlogComponent } from './blog/blog.component';
import { SeguimientoPedidoComponent } from '../pages/seguimiento-pedido/seguimiento-pedido';

export const routes: Routes = [
  { path: '', component: InicioComponent },
  { path: 'catalogo', component: CatalogoComponent },
  { path: 'blog', component: BlogComponent },
  
  // 📌 DEBE ESTAR ANTES DEL **
  { path: 'seguimiento-pedido', component: SeguimientoPedidoComponent },
  { path: 'seguimiento-pedido/:id', component: SeguimientoPedidoComponent },
  { path: '**', redirectTo: '' }
];