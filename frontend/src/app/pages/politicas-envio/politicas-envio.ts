import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CONTACTO } from '../soporte/contacto';

/**
 * Página pública de soporte. Reutiliza el diseño de la página de Términos y Condiciones.
 * Los datos de contacto salen de ../soporte/contacto.ts.
 */
@Component({
  selector: 'app-politicas-envio',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './politicas-envio.html',
  styleUrls: ['../terminos/terminos.css']
})
export class PoliticasEnvioComponent {
  readonly c = CONTACTO;
  readonly fechaActualizacion = '4 de noviembre de 2026';
}
