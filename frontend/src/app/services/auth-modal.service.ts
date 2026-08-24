import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/**
 * Tipo que representa los modos disponibles del modal de autenticación.
 * - 'login': formulario de inicio de sesión.
 * - 'registro': formulario de registro de nuevo usuario.
 * - 'recuperar': formulario de recuperación de contraseña.
 */
export type AuthModalMode = 'login' | 'registro' | 'recuperar';

/**
 * Servicio para controlar la visibilidad y el modo del modal de autenticación.
 * Singleton compartido (providedIn: 'root').
 * 
 * Permite abrir/cerrar el modal y cambiar entre sus modos (login, registro, recuperar)
 * desde cualquier parte de la aplicación, incluyendo el CartService que abre
 * el modal de login cuando el usuario intenta agregar al carrito sin sesión.
 * 
 * Utiliza BehaviorSubject para mantener estado reactivo que los componentes
 * pueden observar mediante los Observables públicos.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthModalService {
  /** BehaviorSubject que controla si el modal está abierto (true) o cerrado (false). */
  private readonly openSubject = new BehaviorSubject<boolean>(false);

  /** BehaviorSubject que controla qué formulario muestra el modal (login/registro/recuperar). */
  private readonly modeSubject = new BehaviorSubject<AuthModalMode>('login');

  /** Observable que indica si el modal está abierto. Los componentes se suscriben para mostrar/ocultar el modal. */
  readonly open$ = this.openSubject.asObservable();

  /** Observable del modo actual del modal. El componente modal lo usa para renderizar el formulario correcto. */
  readonly mode$ = this.modeSubject.asObservable();

  /**
   * Abre el modal de autenticación con el modo especificado.
   * Si no se especifica modo, por defecto abre en modo 'login'.
   *
   * @param mode modo del formulario a mostrar ('login' | 'registro' | 'recuperar').
   */
  open(mode: AuthModalMode = 'login'): void {
    this.modeSubject.next(mode);  // Establece el modo antes de abrir
    this.openSubject.next(true);  // Muestra el modal
  }

  /**
   * Cierra el modal de autenticación.
   * No resetea el modo, por lo que si se vuelve a abrir mostrará el mismo modo.
   */
  close(): void {
    this.openSubject.next(false);
  }

  /**
   * Cambia el modo del modal sin abrirlo ni cerrarlo.
   * Se usa para navegar entre formularios dentro del modal abierto
   * (ej: pasar de login a registro, o a recuperar contraseña).
   *
   * @param mode nuevo modo del formulario.
   */
  setMode(mode: AuthModalMode): void {
    this.modeSubject.next(mode);
  }
}