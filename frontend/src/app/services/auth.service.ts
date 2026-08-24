import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/**
 * Servicio de autenticación para usuarios clientes.
 * Providedín: 'root' → singleton compartido en toda la aplicación.
 * 
 * Gestiona:
 * - Registro y login de usuarios.
 * - Almacenamiento persistente del token JWT y datos del usuario en localStorage.
 * - Estado reactivo del usuario actual mediante BehaviorSubject (observable).
 * - Recuperación y cambio de contraseña.
 * 
 * Al inicializar, restaura la sesión desde localStorage si existía una sesión previa.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  /** URL base de la API de autenticación en el backend Spring Boot. */
  private apiUrl = `${environment.apiUrl}/api/auth`;

  /**
   * BehaviorSubject que mantiene el estado del usuario actual.
   * BehaviorSubject emite el último valor a cualquier suscriptor nuevo.
   * null = sin sesión activa.
   */
  private currentUserSubject = new BehaviorSubject<any>(null);

  /** Observable público del usuario actual. Los componentes se suscriben para reaccionar a cambios. */
  public currentUser$ = this.currentUserSubject.asObservable();

  /** BehaviorSubject que mantiene el token JWT actual. null = no autenticado. */
  private tokenSubject = new BehaviorSubject<string | null>(null);

  /** Observable público del token JWT. */
  public token$ = this.tokenSubject.asObservable();

  /**
   * Constructor: restaura la sesión desde localStorage si existe.
   * Esto permite que el usuario siga autenticado al recargar la página.
   *
   * @param http cliente HTTP de Angular para hacer peticiones al backend.
   */
  constructor(private http: HttpClient) {
    // Recuperar datos del localStorage al iniciar la aplicación
    const token = localStorage.getItem('token');
    const usuario = localStorage.getItem('usuario');
    
    if (token) {
      this.tokenSubject.next(token);      // Restaurar token
    }
    if (usuario) {
      this.currentUserSubject.next(JSON.parse(usuario)); // Restaurar datos del usuario
    }
  }

  /**
   * Registra un nuevo usuario en el sistema.
   * El email de bienvenida con cupón se envía automáticamente desde el backend.
   *
   * @param email    correo electrónico único del usuario.
   * @param password contraseña (mínimo 6 caracteres).
   * @param nombre   nombre del usuario.
   * @param apellido apellido del usuario.
   * @param telefono número de teléfono (opcional).
   * @returns Observable con la respuesta del backend (id, email, nombre, apellido).
   */
  registro(
    email: string,
    password: string,
    nombre: string,
    apellido: string,
    telefono: string
  ): Observable<any> {
    return this.http.post(`${this.apiUrl}/registro`, {
      email,
      password,
      nombre,
      apellido,
      telefono
    });
  }

  /**
   * Autentica al usuario y guarda el token JWT en localStorage.
   * 
   * Usa el operador `tap` de RxJS para ejecutar efectos secundarios
   * (guardar en localStorage y actualizar los BehaviorSubjects) sin
   * transformar la respuesta del observable.
   *
   * @param email    correo del usuario.
   * @param password contraseña del usuario.
   * @returns Observable con { token, usuario } del backend.
   */
  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, { email, password }).pipe(
      tap((response: any) => {
        if (response.token) {
          // Persistir el token y datos del usuario en localStorage
          localStorage.setItem('token', response.token);
          localStorage.setItem('usuario', JSON.stringify(response.usuario));
          // Actualizar los observables para notificar a los componentes suscritos
          this.tokenSubject.next(response.token);
          this.currentUserSubject.next(response.usuario);
        }
      })
    );
  }

  /**
   * Cierra la sesión del usuario.
   * Elimina el token y los datos del usuario de localStorage y resetea los BehaviorSubjects.
   * El CartService escucha el cambio en token$ y limpiará el carrito automáticamente.
   */
  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    this.tokenSubject.next(null);
    this.currentUserSubject.next(null);
  }

  /**
   * Solicita al backend que inicie el flujo de recuperación de contraseña.
   * El backend enviará un email con un enlace de reset si el email existe.
   *
   * @param email correo del usuario que olvidó su contraseña.
   * @returns Observable con el mensaje de respuesta del backend.
   */
  solicitarRecuperacion(email: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/solicitar-recuperacion`, { email });
  }

  /**
   * Valida si un token de recuperación de contraseña es válido.
   * Se usa al cargar la página de reset para verificar el enlace del email.
   *
   * @param token token UUID del enlace de recuperación.
   * @returns Observable con { valido: true } o error.
   */
  validarToken(token: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/validar-token/${token}`);
  }

  /**
   * Cambia la contraseña del usuario usando el token de recuperación.
   *
   * @param token          token UUID del enlace de recuperación.
   * @param nuevaContraseña nueva contraseña en texto plano.
   * @returns Observable con mensaje de éxito o error.
   */
  cambiarContraseña(token: string, nuevaContraseña: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/cambiar-contrasena`, {
      token,
      nuevaContraseña
    });
  }

  /**
   * Obtiene el token JWT actual directamente desde localStorage.
   * Se usa en otros servicios (CartService) para incluirlo en los headers HTTP.
   *
   * @returns el token JWT o null si no hay sesión activa.
   */
  getToken(): string | null {
    return localStorage.getItem('token');
  }

  /**
   * Verifica si el usuario está autenticado (tiene token en localStorage).
   * Nota: no valida si el token ha expirado; eso lo hace el backend.
   *
   * @returns true si hay un token guardado, false en caso contrario.
   */
  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  /**
   * Retorna los datos del usuario actualmente autenticado.
   * Obtiene el valor actual del BehaviorSubject (sin necesidad de suscribirse).
   *
   * @returns objeto con los datos del usuario (id, email, nombre, apellido) o null.
   */
  getCurrentUser(): any {
    return this.currentUserSubject.value;
  }
}
