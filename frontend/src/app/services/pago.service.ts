import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

/**
 * Servicio para procesar pagos enviando los datos al backend.
 * Singleton compartido (providedIn: 'root').
 * * Conecta con los endpoints del backend Spring Boot bajo /api/pago.
 * NOTA: Todas las peticiones a /api/pago/* requieren autenticación JWT.
 */
@Injectable({
  providedIn: 'root'
})
export class PagoService {
  
  /**
   * URL base del controlador de pagos.
   */
  private readonly baseUrl = `${environment.apiUrl}/api/pago`;

  /**
   * @param http cliente HTTP de Angular.
   * @param authService servicio de autenticación para obtener el token.
   */
  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  /**
   * Genera los headers con el Token JWT del usuario autenticado.
   */
  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  /**
   * NUEVO: Solicita a Spring Boot que cree un PaymentIntent en Stripe.
   * El backend calculará el monto real y devolverá un 'client_secret'.
   * * @param datosPago objeto con monto, moneda, cupón, etc.
   * @returns Observable con { clientSecret: "pi_..." }
   */
  crearPaymentIntent(datosPago: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/create-payment-intent`, datosPago, { headers: this.getHeaders() });
  }

  /**
   * ORIGINAL: Envía los datos finales al backend para asentar la orden.
   * * El backend:
   * 1. Verifica autenticación.
   * 2. Valida carrito y cupones.
   * 3. Crea el pedido.
   * 4. Vacía el carrito.
   * * @param datosPago objeto con método de pago y cupón.
   * @returns Observable con mensaje de éxito o error.
   */
  procesarPago(datosPago: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/procesar`, datosPago, { headers: this.getHeaders() });
  }

  /**
   * Valida un cupón de descuento en el backend.
   * 
   * @param codigo Código del cupón.
   * @returns Observable con los datos del cupón si es válido, o error.
   */
  validarCupon(codigo: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/validar-cupon/${codigo}`, { headers: this.getHeaders() });
  }
}