import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AuthModalService } from '../../../services/auth-modal.service';

@Component({
  selector: 'app-vendedor-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './vendedor.component.html',
  styleUrl: './vendedor.component.css'
})
export class VendedorDashboardComponent implements OnInit {
  adminUser: any = null;
  productos: any[] = [];
  searchProducto = '';

  constructor(
    private adminService: AdminService, 
    private router: Router,
    private authModalService: AuthModalService
  ) {}

  ngOnInit(): void {
    if (!this.adminService.isAuthenticated() || !this.adminService.isSeller()) {
      this.router.navigate(['/']);
      this.authModalService.open('login');
      return;
    }

    this.adminUser = this.adminService.getCurrentUser();
    this.cargarInventario();
  }

  cargarInventario(): void {
    this.adminService.getProductos().subscribe({
      next: (data: any[]) => {
        this.productos = data;
      },
      error: (err: any) => console.error(err)
    });
  }

  get filteredProductos(): any[] {
    if (!this.searchProducto) return this.productos;
    const query = this.searchProducto.toLowerCase();
    return this.productos.filter(p => 
      p.nombre.toLowerCase().includes(query) ||
      (p.descripcion && p.descripcion.toLowerCase().includes(query))
    );
  }

  logout(): void {
    this.adminService.logout();
    this.router.navigate(['/']);
    this.authModalService.open('login');
  }
}
