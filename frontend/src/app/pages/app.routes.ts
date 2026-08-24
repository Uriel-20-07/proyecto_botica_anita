import { Routes } from '@angular/router';
import { InicioComponent } from './inicio';
import { CatalogoComponent } from './catalogo/catalogo';
import { BlogComponent } from './blog/blog.component';

export const routes: Routes = [
  { path: '', component: InicioComponent },
  { path: 'catalogo', component: CatalogoComponent },
  { path: 'blog', component: BlogComponent }
];