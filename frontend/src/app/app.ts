import { Component, OnInit } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar';
import { FooterComponent } from './components/footer/footer';
import { AuthModalComponent } from './components/auth-modal/auth-modal.component';
import { ChatbotComponent } from './components/chatbot/chatbot';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent, AuthModalComponent, ChatbotComponent, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  isDashboard = false;

  constructor(private router: Router) {
    if (typeof window !== 'undefined') {
      this.isDashboard = window.location.pathname.startsWith('/dashboard');
    }
  }

  ngOnInit() {
    // Escuchar cambios de ruta para ocultar navbar/footer en el dashboard
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.isDashboard = event.urlAfterRedirects.startsWith('/dashboard');
      }
    });
  }
}
