import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Página pública "Términos y Condiciones de Uso".
 *
 * IMPORTANTE: la versión de abajo debe coincidir con AuthService.VERSION_TERMINOS
 * (backend). Si cambias el contenido de los términos, sube ambos números para que
 * quede registrado qué versión aceptó cada usuario al registrarse.
 */
@Component({
  selector: 'app-terminos',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './terminos.html',
  styleUrls: ['./terminos.css']
})
export class TerminosComponent {
  readonly version = '1.0';
  readonly fechaActualizacion = '22 de octubre de 2026';
}
