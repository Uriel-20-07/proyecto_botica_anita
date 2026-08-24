import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AdminService {
  private apiUrl = `${environment.apiUrl}/api/admin`;
  private adminTokenSubject = new BehaviorSubject<string | null>(null);
  public adminToken$ = this.adminTokenSubject.asObservable();
  private adminUserSubject = new BehaviorSubject<any>(null);
  public adminUser$ = this.adminUserSubject.asObservable();

  constructor(private http: HttpClient) {
    if (typeof window !== 'undefined') {
      const token = localStorage.getItem('adminToken');
      const user = localStorage.getItem('adminUser');
      if (token) {
        this.adminTokenSubject.next(token);
      }
      if (user) {
        this.adminUserSubject.next(JSON.parse(user));
      }
    }
  }

  private getHeaders() {
    let token = this.adminTokenSubject.value;
    if (!token && typeof window !== 'undefined') {
      token = localStorage.getItem('adminToken');
    }
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
  }

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/auth/login`, { email, password }).pipe(
      tap((response: any) => {
        if (response.token) {
          if (typeof window !== 'undefined') {
            localStorage.setItem('adminToken', response.token);
            localStorage.setItem('adminUser', JSON.stringify(response.usuario));
          }
          this.adminTokenSubject.next(response.token);
          this.adminUserSubject.next(response.usuario);
        }
      }),
    );
  }

  logout(): void {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('adminToken');
      localStorage.removeItem('adminUser');
    }
    this.adminTokenSubject.next(null);
    this.adminUserSubject.next(null);
  }

  getProductos(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/productos`, { headers: this.getHeaders() });
  }

  actualizarStock(id: number, stock: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/productos/${id}/stock`, { stock }, { headers: this.getHeaders() });
  }

  actualizarOferta(id: number, enOferta: boolean, precioOferta?: number): Observable<any> {
    const body: any = { enOferta };
    if (enOferta && precioOferta != null) {
      body.precioOferta = precioOferta;
    }
    return this.http.put(`${this.apiUrl}/productos/${id}/oferta`, body, { headers: this.getHeaders() });
  }


  getVentas(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/ventas`, { headers: this.getHeaders() });
  }

  getStats(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/ventas/stats`, { headers: this.getHeaders() });
  }

  isAdmin(): boolean {
    const user = this.adminUserSubject.value;
    return user && user.rol === 'admin';
  }

  isSeller(): boolean {
    const user = this.adminUserSubject.value;
    return user && user.rol === 'vendedor';
  }

  isAuthenticated(): boolean {
    return !!(
      this.adminTokenSubject.value ||
      (typeof window !== 'undefined' && localStorage.getItem('adminToken'))
    );
  }

  getCurrentUser(): any {
    if (this.adminUserSubject.value) {
      return this.adminUserSubject.value;
    }
    if (typeof window !== 'undefined') {
      const user = localStorage.getItem('adminUser');
      return user ? JSON.parse(user) : null;
    }
    return null;
  }

  getLotes(productoId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/productos/${productoId}/lotes`, { headers: this.getHeaders() });
  }

  agregarLote(productoId: number, loteData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/productos/${productoId}/lotes`, loteData, { headers: this.getHeaders() });
  }

  eliminarLote(loteId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/productos/lotes/${loteId}`, { headers: this.getHeaders() });
  }

  getTopProductos(rango: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/ventas/top-productos?rango=${rango}`, { headers: this.getHeaders() });
  }

  getReportes(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/reportes`, { headers: this.getHeaders() });
  }

  getTopProductosFiltrados(dia: string | null, mes: string | null): Observable<any[]> {
    let url = `${this.apiUrl}/reportes/top-productos-filtrados`;
    const params: string[] = [];
    if (dia) params.push(`dia=${dia}`);
    if (mes) params.push(`mes=${mes}`);
    if (params.length > 0) {
      url += `?${params.join('&')}`;
    }
    return this.http.get<any[]>(url, { headers: this.getHeaders() });
  }

  getTopProductosPorDistrito(distrito: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/reportes/top-productos-distrito?distrito=${encodeURIComponent(distrito)}`, { headers: this.getHeaders() });
  }

  getUsuarios(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/usuarios`, { headers: this.getHeaders() });
  }

  lanzarCupon(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/cupones/lanzar`, payload, { headers: this.getHeaders() });
  }
}
