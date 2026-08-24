import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
// Importamos apuntando al nombre real de tus archivos
import { NavbarComponent } from '../components/navbar/navbar';
import { FooterComponent } from '../components/footer/footer';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class AppComponent {
  title = 'frontend';
}