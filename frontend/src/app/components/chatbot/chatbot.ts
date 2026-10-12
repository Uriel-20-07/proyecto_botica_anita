import { Component, OnInit, OnDestroy, Inject, PLATFORM_ID, ViewChild, ElementRef } from '@angular/core';
import { isPlatformBrowser, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { CartService, CartItem } from '../../services/cart.service';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';
import { environment } from '../../../environments/environment';

interface ChatMessage {
  role: 'user' | 'model';
  content: string;
  functionCall?: any;
  /**
   * Presente solo en mensajes del modelo que ofrecen una confirmación
   * con una cantidad alternativa (Cuando el backend rechaza por límite
   * o stock pero sugiere un máximo). Contiene la cantidad a confirmar.
   */
  confirmacion?: { idProducto: number; cantidad: number };
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrls: ['./chatbot.css']
})
export class ChatbotComponent implements OnInit, OnDestroy {
  @ViewChild('scrollContainer') private scrollContainer!: ElementRef;

  isOpen = false;
  isLoading = false;
  userInput = '';
  messages: ChatMessage[] = [];

  private readonly chatApiUrl = `${environment.apiUrl}/api/chatbot/consultar`;

  constructor(
    @Inject(PLATFORM_ID) private platformId: Object,
    private readonly http: HttpClient,
    private readonly router: Router,
    private readonly cartService: CartService,
    private readonly authService: AuthService,
    private readonly authModalService: AuthModalService
  ) { }

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      // Inicializar el chat con el mensaje de bienvenida
      this.messages.push({
        role: 'model',
        content: '¡Hola! 👋 Soy SofIA, la asistente virtual de BoticaAnita. ¿En qué te puedo asesorar hoy? Puedo buscar medicamentos en el catálogo, agregar productos al carrito y guiarte por la página.'
      });
    }
  }

  ngOnDestroy() {
    // Limpieza si es necesario
  }

  toggleChat() {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('registro'); // Abre el modal de registro
      return;
    }
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.scrollToBottom();
    }
  }

  sendMessage(event: Event) {
    event.preventDefault();
    if (!this.userInput.trim() || this.isLoading) return;

    const userText = this.userInput.trim();
    this.userInput = '';

    // Añadir mensaje del usuario al historial local
    this.messages.push({
      role: 'user',
      content: userText
    });

    this.scrollToBottom();
    this.isLoading = true;

    // Llamar al endpoint del backend
    this.http.post<any>(this.chatApiUrl, { messages: this.messages }).subscribe({
      next: (response) => {
        this.isLoading = false;

        if (response) {
          // 1. Verificar si hay un comando de llamada a función
          if (response.functionCall) {
            const funcCall = response.functionCall;
            const name = funcCall.name;
            const args = funcCall.args;

            if (name === 'agregarAlCarrito') {
              const idProducto = args.idProducto;
              const cantidad = args.cantidad || 1;
              this.ejecutarAgregarAlCarrito(idProducto, cantidad);
            } else if (name === 'redirigir') {
              const ruta = args.ruta;
              this.router.navigate([ruta]);
              this.messages.push({
                role: 'model',
                content: 'Entendido. Te estoy redirigiendo...'
              });
              this.isOpen = false; // Cerrar chat al redirigir
            }
          } else {
            // 2. Respuesta de texto convencional
            this.messages.push({
              role: 'model',
              content: this.formatContent(response.content)
            });
          }
        } else {
          this.messages.push({
            role: 'model',
            content: 'Lo siento, no he recibido una respuesta válida. Por favor, vuelve a intentarlo.'
          });
        }
        this.scrollToBottom();
      },
      error: (err) => {
        this.isLoading = false;
        console.error('Error al consultar chatbot:', err);
        this.messages.push({
          role: 'model',
          content: 'Ocurrió un problema de conexión al intentar comunicarme con el asistente. Asegúrate de tener el backend corriendo y configurado.'
        });
        this.scrollToBottom();
      }
    });
  }

  /**
   * Ejecuta la agregación al carrito y reacciona al resultado real del backend.
   *
   * Ya NO se confirma a ciegas: se espera la respuesta. Si el backend acepta,
   * se informa el éxito. Si rechaza (HTTP 400 con { error, maxAdicional }),
   * se muestra el motivo y, cuando maxAdicional > 0, se ofrecen botones para
   * agregar exactamente esa cantidad alternativa.
   */
  private ejecutarAgregarAlCarrito(idProducto: number, cantidad: number): void {
    this.cartService.addWithQty(idProducto, cantidad).subscribe({
      next: () => {
        this.messages.push({
          role: 'model',
          content: '¡Listo! He agregado el producto al carrito de compras.'
        });
        this.scrollToBottom();
      },
      error: (err) => {
        // El backend responde 400 con { error, maxAdicional } para rechazos de negocio.
        const cuerpo = err?.error;
        const mensaje: string = cuerpo?.error || 'No se pudo agregar el producto al carrito.';
        const maxAdicional: number = cuerpo?.maxAdicional ?? 0;

        if (maxAdicional > 0) {
          this.messages.push({
            role: 'model',
            content: mensaje + ' ¿Deseas agregar ' + maxAdicional + ' unidad(es)?',
            confirmacion: { idProducto, cantidad: maxAdicional }
          });
        } else {
          this.messages.push({ role: 'model', content: mensaje });
        }
        this.scrollToBottom();
      }
    });
  }

  /**
   * Confirma y agrega la cantidad alternativa ofrecida por SofIA
   * (la que calculó el backend como máxima permitida).
   */
  confirmarCantidad(msg: ChatMessage): void {
    if (!msg.confirmacion) return;
    const { idProducto, cantidad } = msg.confirmacion;

    // Registrar la decisión del usuario y quitar los botones del mensaje.
    this.messages.push({ role: 'user', content: 'Sí, agregar ' + cantidad + ' unidad(es).' });
    msg.confirmacion = undefined;

    this.ejecutarAgregarAlCarrito(idProducto, cantidad);
  }

  /**
   * Cancela la confirmación ofrecida: no se agrega nada al carrito.
   */
  cancelarConfirmacion(msg: ChatMessage): void {
    if (!msg.confirmacion) return;
    msg.confirmacion = undefined;
    this.messages.push({ role: 'user', content: 'No, gracias.' });
    this.messages.push({ role: 'model', content: 'Entendido, no agregué el producto al carrito.' });
    this.scrollToBottom();
  }

  private formatContent(text: string): string {
    if (!text) return '';
    // Reemplazar saltos de línea por <br>
    let formatted = text.replace(/\n/g, '<br>');
    // Reemplazar **negrita** por <strong>negrita</strong>
    formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    return formatted;
  }

  private scrollToBottom() {
    setTimeout(() => {
      try {
        if (this.scrollContainer) {
          this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
        }
      } catch (err) {
        // Ignorar errores menores de scroll
      }
    }, 100);
  }
}