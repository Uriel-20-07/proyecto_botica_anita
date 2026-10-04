import { Component, OnInit, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

// Servicios de tu proyecto
import { CartService } from '../../services/cart.service';
import { PagoService } from '../../services/pago.service';
import { AuthService } from '../../services/auth.service';
import { RecetasService, RecetaMedica } from '../../services/recetas.service';

// Stripe Elements para el diseño profesional
import { loadStripe, Stripe, StripeCardNumberElement, StripeCardExpiryElement, StripeCardCvcElement } from '@stripe/stripe-js';

const EMAILJS_SERVICE_ID  = 'service_rcioayq';
const EMAILJS_TEMPLATE_ID = 'template_stu3jvw';
const EMAILJS_PUBLIC_KEY  = 'HDwamrH2SgIFGUpNw';

@Component({
  selector: 'app-pago',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './pago.html',
  styleUrl: './pago.css'
})
export class PagoComponent implements OnInit, AfterViewChecked {

  metodoSeleccionado: 'TARJETA' | 'YAPE' = 'TARJETA';

  // Variables Receta Checkout
  requiereReceta: boolean = false;
  recetaSubidaId: number | null = null;
  recetaSubidaExito: boolean = false;
  subiendoReceta: boolean = false;
  
  recetaForm = {
    medicoId: '',
    fechaEmision: new Date().toISOString().split('T')[0],
  };
  recetaFile: File | null = null;
  recetaFileName: string = '';
  recetaMensajeError: string = '';
  recetaMensajeExito: string = '';
  subtotal: number = 0;
  descuento: number = 0;
  total: number = 0;
  costoEnvio: number = 0;
  productosCarrito: { producto: any; cantidad: number }[] = [];
  subtotalOriginal: number = 0;
  descuentoTotal: number = 0;
  subtotalBase: number = 0;
  igv: number = 0;

  codigoCupon: string = '';
  cuponAplicado: boolean = false;
  codigoAplicado: string = '';

  mensajeError: string = '';
  mensajeExito: string = '';
  enviandoCodigo: boolean = false;
  pagoExitoso: boolean = false;
  folioGenerado: string = '';
  direccionFinal: string = '';

  yapePaso: 1 | 2 = 1;
  codigoYapeGenerado: string = '';
  correoDestino: string = '';

  readonly distritosLima: string[] = [
    'Miraflores', 'San Isidro', 'Santiago de Surco', 'La Molina',
    'San Borja', 'Jesús María', 'Lince', 'Pueblo Libre',
    'Magdalena del Mar', 'San Miguel', 'Barranco', 'Chorrillos',
    'Surquillo', 'Los Olivos', 'San Martín de Porres', 'Comas',
    'Independencia', 'San Juan de Lurigancho', 'Santa Anita', 'Ate',
  ];

  readonly puntosRecojoPorDistrito: { [key: string]: { nombre: string; direccion: string; referencia: string }[] } = {
    'Miraflores': [
      { nombre: 'Bodega Larco', direccion: 'Av. Larco 880', referencia: 'Frente al óvalo de Miraflores' },
      { nombre: 'Minimarket Kennedy', direccion: 'Av. Diagonal 360', referencia: 'Al lado del parque Kennedy' },
      { nombre: 'Tienda Berlín', direccion: 'Calle Berlín 320', referencia: 'Cerca al malecón' },
    ],
    'San Isidro': [
      { nombre: 'Bodega El Olivar', direccion: 'Calle Los Libertadores 240', referencia: 'Frente al bosque El Olivar' },
      { nombre: 'Minimarket Camino Real', direccion: 'Av. Camino Real 390', referencia: 'Dentro del centro comercial' },
      { nombre: 'Tienda Corpac', direccion: 'Av. Guardia Civil 820', referencia: 'Cerca a la zona empresarial' },
    ],
    'Santiago de Surco': [
      { nombre: 'Bodega Surco Viejo', direccion: 'Av. Ayacucho 640', referencia: 'Frente a la plaza de Surco' },
      { nombre: 'Minimarket El Polo', direccion: 'Av. El Polo 520', referencia: 'Cerca a la universidad' },
      { nombre: 'Tienda Los Precursores', direccion: 'Av. Los Precursores 1050', referencia: 'Al lado del Jockey Plaza' },
    ],
    'La Molina': [
      { nombre: 'Bodega La Molina Centro', direccion: 'Av. La Molina 1165', referencia: 'Frente a la municipalidad' },
      { nombre: 'Minimarket Rinconada', direccion: 'Av. La Rinconada 980', referencia: 'Cerca a la universidad agraria' },
      { nombre: 'Tienda Santa Patricia', direccion: 'Av. Santa Patricia 410', referencia: 'Frente al parque' },
    ],
    'San Borja': [
      { nombre: 'Bodega San Borja Sur', direccion: 'Av. San Borja Sur 890', referencia: 'Frente a la rambla' },
      { nombre: 'Minimarket La Cultura', direccion: 'Av. De la Cultura 400', referencia: 'Cerca a los ministerios' },
      { nombre: 'Tienda Las Artes', direccion: 'Av. Las Artes 560', referencia: 'Al lado del teatro' },
    ],
    'Jesús María': [
      { nombre: 'Bodega Jesús María', direccion: 'Av. Brasil 2450', referencia: 'Frente al mercado San José' },
      { nombre: 'Minimarket Real Plaza', direccion: 'Av. Garcilaso 2300', referencia: 'Dentro del centro comercial' },
      { nombre: 'Tienda Arnaldo Márquez', direccion: 'Av. Arnaldo Márquez 1750', referencia: 'Cerca a la municipalidad' },
    ],
    'Lince': [
      { nombre: 'Bodega Lince Centro', direccion: 'Av. Petit Thouars 2200', referencia: 'Frente al parque Castilla' },
      { nombre: 'Minimarket Risso', direccion: 'Jr. Risso 360', referencia: 'Dentro de la galería' },
      { nombre: 'Tienda Canevaro', direccion: 'Av. Canevaro 900', referencia: 'Cerca al hospital' },
    ],
    'Pueblo Libre': [
      { nombre: 'Bodega Bolívar', direccion: 'Av. Bolívar 1200', referencia: 'Frente a la municipalidad' },
      { nombre: 'Minimarket Sucre', direccion: 'Av. Sucre 1080', referencia: 'Cerca a la cruz del viajero' },
      { nombre: 'Tienda La Mar', direccion: 'Av. La Mar 2275', referencia: 'Al lado del mercado' },
    ],
    'Magdalena del Mar': [
      { nombre: 'Bodega Magdalena', direccion: 'Jr. Castilla 620', referencia: 'Frente a la plaza principal' },
      { nombre: 'Minimarket Brasil', direccion: 'Av. Brasil 3800', referencia: 'Cerca al malecón' },
      { nombre: 'Tienda Echenique', direccion: 'Jr. Echenique 450', referencia: 'Al lado de la iglesia' },
    ],
    'San Miguel': [
      { nombre: 'Bodega San Miguel', direccion: 'Av. Universitaria 2200', referencia: 'Frente a Plaza San Miguel' },
      { nombre: 'Minimarket Costanera', direccion: 'Av. Costanera 1500', referencia: 'Cerca al malecón' },
      { nombre: 'Tienda Riva Agüero', direccion: 'Av. Riva Agüero 1400', referencia: 'Frente al parque' },
    ],
    'Barranco': [
      { nombre: 'Bodega Barranco', direccion: 'Av. Grau 620', referencia: 'Frente al puente de los suspiros' },
      { nombre: 'Minimarket Pedro de Osma', direccion: 'Av. Pedro de Osma 220', referencia: 'Cerca a la plaza' },
      { nombre: 'Tienda El Faro', direccion: 'Jr. Centenario 130', referencia: 'Frente al faro' },
    ],
    'Chorrillos': [
      { nombre: 'Bodega Chorrillos', direccion: 'Av. Huaylas 580', referencia: 'Frente a la plaza Matriz' },
      { nombre: 'Minimarket Los Cedros', direccion: 'Av. Los Cedros 340', referencia: 'Cerca a la villa militar' },
      { nombre: 'Tienda La Curva', direccion: 'Av. Defensores del Morro 1600', referencia: 'Frente al morro solar' },
    ],
    'Surquillo': [
      { nombre: 'Bodega Surquillo', direccion: 'Av. Angamos 1800', referencia: 'Frente al mercado N°2' },
      { nombre: 'Minimarket Villarán', direccion: 'Av. Villarán 900', referencia: 'Cerca al estadio municipal' },
      { nombre: 'Tienda Recavarren', direccion: 'Av. Recavarren 500', referencia: 'Al lado de la municipalidad' },
    ],
    'Los Olivos': [
      { nombre: 'Bodega Los Olivos', direccion: 'Av. Naranjal 1100', referencia: 'Cerca al mercado Covida' },
      { nombre: 'Minimarket Covida', direccion: 'Av. Antúnez de Mayolo 900', referencia: 'Dentro de la urb. Covida' },
      { nombre: 'Tienda Villa Sol', direccion: 'Av. Universitaria 5400', referencia: 'Frente al paradero' },
    ],
    'San Martín de Porres': [
      { nombre: 'Bodega SMP', direccion: 'Av. Perú 3200', referencia: 'Frente al mercado Caquetá' },
      { nombre: 'Minimarket Dueñas', direccion: 'Av. Dueñas 420', referencia: 'Cerca a la municipalidad' },
      { nombre: 'Tienda Tomás Valle', direccion: 'Av. Tomás Valle 1500', referencia: 'Frente al hospital' },
    ],
    'Comas': [
      { nombre: 'Bodega Comas', direccion: 'Av. Túpac Amaru 5200', referencia: 'Cerca a MegaPlaza' },
      { nombre: 'Minimarket Retablo', direccion: 'Av. El Retablo 700', referencia: 'Dentro de la urb. El Retablo' },
      { nombre: 'Tienda La Pascana', direccion: 'Av. La Pascana 300', referencia: 'Frente al mercado' },
    ],
    'Independencia': [
      { nombre: 'Bodega Independencia', direccion: 'Av. Los Jazmines 410', referencia: 'Cerca al colegio 3048 Santiago Antúnez de Mayolo' },
      { nombre: 'Minimarket Payet', direccion: 'Av. Panamericana Norte 5100', referencia: 'Cerca al óvalo Payet' },
      { nombre: 'Tienda Tahuantinsuyo', direccion: 'Av. Tahuantinsuyo 2100', referencia: 'Frente al mercado' },
    ],
    'San Juan de Lurigancho': [
      { nombre: 'Bodega SJL', direccion: 'Av. Próceres 4800', referencia: 'Frente a la estación del tren' },
      { nombre: 'Minimarket Canto Grande', direccion: 'Av. Canto Grande 900', referencia: 'Cerca al mercado' },
      { nombre: 'Tienda Bayóvar', direccion: 'Av. Bayóvar 600', referencia: 'Frente al paradero' },
    ],
    'Santa Anita': [
      { nombre: 'Bodega Santa Anita', direccion: 'Av. Los Eucaliptos 800', referencia: 'Frente al óvalo' },
      { nombre: 'Minimarket Mall Aventura', direccion: 'Av. La Cultura 2100', referencia: 'Dentro del mall' },
      { nombre: 'Tienda Colectora', direccion: 'Av. Colectora 350', referencia: 'Cerca a la municipalidad' },
    ],
    'Ate': [
      { nombre: 'Bodega Ate', direccion: 'Av. Nicolás Ayllón 4200', referencia: 'Frente al mercado Ceres' },
      { nombre: 'Minimarket Santa Clara', direccion: 'Av. Santa Clara 600', referencia: 'Dentro del centro comercial' },
      { nombre: 'Tienda Salamanca', direccion: 'Av. Los Quechuas 1200', referencia: 'Frente al parque' },
    ],
  };

  formPago: any = {
    distrito: '',
    direccionDetalle: '',
    referencia: '',
    nombreTarjeta: '',
    numeroCelular: '',
    correoYape: '',
    tokenYape: '',
    puntoSeleccionado: '',
    esUrgente: false
  };

  // --- VARIABLES DE STRIPE ---
  stripe: Stripe | null = null;
  cardNumberElement: StripeCardNumberElement | null = null;
  cardExpiryElement: StripeCardExpiryElement | null = null;
  cardCvcElement: StripeCardCvcElement | null = null;
  stripeInicializado: boolean = false; 
  procesandoPago: boolean = false; 

  constructor(
    private readonly cartService: CartService,
    private readonly router: Router,
    private readonly pagoService: PagoService,
    private readonly authService: AuthService,
    private readonly recetasService: RecetasService
  ) {}

  async ngOnInit() {
    // 1. Verificar si el usuario está autenticado, si no, redirigir al login
    const token = this.authService.getToken();
    if (!token) {
      this.router.navigate(['/login']); // Redirigir si no hay sesión
      return;
    }

    this.subtotal = this.cartService.getTotal();
    this.productosCarrito = this.cartService.getItems();
    this.subtotalOriginal = this.productosCarrito.reduce((acc, item) => {
      return acc + (item.producto.precioVenta * item.cantidad);
    }, 0);
    this.calcularTotales();

    // 2. Si el carrito está vacío, no hay nada que pagar
    if (this.subtotal === 0) {
      this.router.navigate(['/catalogo']);
      return;
    }

    // Verificar si requiere receta
    this.requiereReceta = this.productosCarrito.some(item => this.requiereRecetaMedica(item.producto.nombre));

    // 3. Inicializamos Stripe con tu clave pública
    // ¡REEMPLAZA ESTO CON TU CLAVE REAL!
    this.stripe = await loadStripe('pk_test_51UM4YM0qfL81bWnlpwCwq4l0PESEA0OOFACO2yenZG4wqabONiPvH9vB6nQIyObRU2TC9SXsGQ6r7KxiVjRWJ1G500nkwWyDRF');
  }

  ngAfterViewChecked() {
    if (this.metodoSeleccionado === 'TARJETA' && this.stripe && !this.stripeInicializado) {
      const elements = this.stripe.elements();
      
      const style = {
        base: {
          color: '#334155',
          fontFamily: '"Segoe UI", Roboto, Helvetica, Arial, sans-serif',
          fontSmoothing: 'antialiased',
          fontSize: '14px',
          '::placeholder': { color: '#94a3b8' }
        },
        invalid: { color: '#e11d48', iconColor: '#e11d48' }
      };

      // Creamos los 3 elementos por separado
      this.cardNumberElement = elements.create('cardNumber', { style, showIcon: true });
      this.cardExpiryElement = elements.create('cardExpiry', { style });
      this.cardCvcElement = elements.create('cardCvc', { style });

      const numDiv = document.getElementById('stripe-card-number');
      if (numDiv) {
        // Inyectamos cada elemento en su respectivo DIV del HTML
        this.cardNumberElement.mount('#stripe-card-number');
        this.cardExpiryElement.mount('#stripe-card-expiry');
        this.cardCvcElement.mount('#stripe-card-cvc');
        this.stripeInicializado = true;

        // Escuchar errores en cualquiera de los 3 campos
        const handleChange = (event: any) => {
          const displayError = document.getElementById('card-errors');
          if (event.error) {
            displayError!.textContent = event.error.message;
          } else {
            displayError!.textContent = '';
          }
        };

        this.cardNumberElement.on('change', handleChange);
        this.cardExpiryElement.on('change', handleChange);
        this.cardCvcElement.on('change', handleChange);
      }
    }
  }

  seleccionarMetodo(metodo: 'TARJETA' | 'YAPE') {
    this.metodoSeleccionado = metodo;
    this.mensajeError = '';
    this.mensajeExito = '';
    this.yapePaso = 1;
    
    // Desmontar los 3 elementos si se cambia a Yape
    if (metodo !== 'TARJETA') {
        this.stripeInicializado = false;
        if (this.cardNumberElement) { this.cardNumberElement.destroy(); this.cardNumberElement = null; }
        if (this.cardExpiryElement) { this.cardExpiryElement.destroy(); this.cardExpiryElement = null; }
        if (this.cardCvcElement) { this.cardCvcElement.destroy(); this.cardCvcElement = null; }
    }
  }

  onDistritoChange() {
    this.formPago.direccionDetalle = '';
    this.formPago.referencia = '';
    this.formPago.puntoSeleccionado = '';
  }

  onTipoEnvioChange() {
    this.formPago.direccionDetalle = '';
    this.formPago.referencia = '';
    this.formPago.puntoSeleccionado = '';
    this.calcularTotales();
  }

  onPuntoRecojoChange() {
    const selectedNombre = this.formPago.puntoSeleccionado;
    const puntos = this.puntosRecojoPorDistrito[this.formPago.distrito];
    if (puntos) {
      const punto = puntos.find(p => p.nombre === selectedNombre);
      if (punto) {
        this.formPago.direccionDetalle = `${punto.nombre} - ${punto.direccion}`;
        this.formPago.referencia = punto.referencia;
      }
    }
  }

  // --- MÉTODOS YAPE Y CUPÓN MANTENIDOS INTACTOS ---
  async enviarCodigoYape() {
    this.mensajeError = '';
    this.mensajeExito = '';
    const cel = this.formPago.numeroCelular;
    const correo = this.formPago.correoYape.trim();

    if (cel.length !== 9 || !cel.startsWith('9')) {
      this.mensajeError = 'Ingresa un número de 9 dígitos que empiece con 9.';
      return;
    }
    if (!correo || !correo.includes('@')) {
      this.mensajeError = 'Ingresa un correo válido donde recibirás el código.';
      return;
    }

    this.enviandoCodigo = true;
    this.codigoYapeGenerado = Math.floor(100000 + Math.random() * 900000).toString();
    this.correoDestino = correo;

    try {
      const res = await fetch('https://api.emailjs.com/api/v1.0/email/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          service_id:  EMAILJS_SERVICE_ID,
          template_id: EMAILJS_TEMPLATE_ID,
          user_id:     EMAILJS_PUBLIC_KEY,
          template_params: { to_email: correo, celular: cel, codigo: this.codigoYapeGenerado }
        })
      });

      if (res.ok) {
        this.mensajeExito = `Código enviado a ${correo}. Revisa tu bandeja.`;
        this.yapePaso = 2;
      } else {
        const err = await res.text();
        this.mensajeError = `No se pudo enviar el código (${err}).`;
      }
    } catch {
      this.mensajeError = 'Error de conexión. Intenta nuevamente.';
    } finally {
      this.enviandoCodigo = false;
    }
  }

  aplicarCupon() {
    const cod = this.codigoCupon.trim().toUpperCase();
    if (!cod) {
      this.mensajeError = 'Ingresa un código de cupón.';
      return;
    }
    if (this.cuponAplicado) {
      this.mensajeError = 'Ya has aplicado un cupón a este pedido.';
      return;
    }

    this.pagoService.validarCupon(cod).subscribe({
      next: (res) => {
        if (res.valido) {
          const valor = Number(res.valorDescuento) || 30;
          this.descuento = this.subtotal * (valor / 100);
          this.cuponAplicado = true;
          this.codigoAplicado = cod;
          this.mensajeError = '';
          this.calcularTotales();
        } else {
          this.mensajeError = 'Cupón no válido.';
        }
      },
      error: (err) => {
        this.mensajeError = err.error?.error || 'El cupón no es válido o ya fue usado.';
        this.descuento = 0;
        this.cuponAplicado = false;
        this.codigoAplicado = '';
        this.calcularTotales();
      }
    });
  }

  calcularTotales() {
    if (this.formPago.esUrgente) {
      this.costoEnvio = 10;
    } else {
      this.costoEnvio = this.subtotal > 50 ? 0 : 5;
    }
    const descuentoAutomatico = Math.max(0, this.subtotalOriginal - this.subtotal);
    this.descuentoTotal = descuentoAutomatico + this.descuento;

    const subtotalNeto = this.subtotalOriginal - this.descuentoTotal;
    this.subtotalBase = subtotalNeto / 1.18;
    this.igv = subtotalNeto - this.subtotalBase;

    this.total = subtotalNeto + this.costoEnvio;
  }

  // --- PAGO PRINCIPAL ---
  async ejecutarPago() {
    if (this.procesandoPago) return;
    this.mensajeError = '';

    if (!this.formPago.distrito) { this.mensajeError = 'Selecciona el distrito.'; return; }
    if (!this.formPago.direccionDetalle.trim()) { this.mensajeError = 'Ingresa la dirección.'; return; }

    if (this.requiereReceta && !this.recetaSubidaExito) {
        this.mensajeError = 'Debes subir y vincular tu receta médica para continuar.';
        return;
    }

    if (this.metodoSeleccionado === 'TARJETA') {
        if (!this.formPago.nombreTarjeta.trim()) {
            this.mensajeError = 'Ingresa el nombre del titular de la tarjeta.';
            return;
        }
        if (!this.stripe || !this.cardNumberElement) {
            this.mensajeError = 'Stripe no está inicializado. Recarga la página.';
            return;
        }

        this.procesandoPago = true;

        try {
            // 1. Crear el intento de pago en el servidor
             const datosParaIntent = {
                 monto: this.total,
                 moneda: 'pen',
                 codigoCupon: this.cuponAplicado ? this.codigoAplicado : null,
                 direccionEnvio: `${this.formPago.direccionDetalle}, ${this.formPago.distrito}`,
                 distrito: this.formPago.distrito,
                 metodoPago: this.metodoSeleccionado,
                 esUrgente: this.formPago.esUrgente,
                 idReceta: this.recetaSubidaId
             };

            const intentResponse: any = await firstValueFrom(this.pagoService.crearPaymentIntent(datosParaIntent));
            
            // 2. Stripe procesa la tarjeta de forma segura en el frontend
            const confirmResult = await this.stripe.confirmCardPayment(intentResponse.clientSecret, {
                payment_method: {
                    card: this.cardNumberElement,
                    billing_details: { name: this.formPago.nombreTarjeta }
                }
            });

            if (confirmResult.error) {
                this.mensajeError = confirmResult.error.message || 'Error al procesar la tarjeta.';
                this.procesandoPago = false;
                return;
            }

            // 3. Confirmación exitosa, guardamos en la Base de Datos
            if (confirmResult.paymentIntent?.status === 'succeeded') {
                this.finalizarPedidoEnBackend();
            }

        } catch (error: any) {
            this.mensajeError = error.error?.error || 'Error de comunicación con el servidor.';
            this.procesandoPago = false;
        }

    } else {
        // Lógica de Yape intacta
        if (this.yapePaso === 1) { this.mensajeError = 'Verifica tu código Yape.'; return; }
        if (this.formPago.tokenYape !== this.codigoYapeGenerado) {
            this.mensajeError = 'Código Yape incorrecto.'; return;
        }
        this.procesandoPago = true;
        this.finalizarPedidoEnBackend();
    }
  }

  // Finaliza el proceso y muestra la pantalla de éxito
  private finalizarPedidoEnBackend() {
        const datosPago = {
            metodoPago: this.metodoSeleccionado,
            codigoCupon: this.cuponAplicado ? this.codigoAplicado : null,
            direccionEnvio: `${this.formPago.direccionDetalle}, ${this.formPago.distrito}`,
            distrito: this.formPago.distrito,
            esUrgente: this.formPago.esUrgente,
            idReceta: this.recetaSubidaId
        };

      this.pagoService.procesarPago(datosPago).subscribe({
          next: () => {
              this.folioGenerado = `FC-${Math.floor(Math.random() * 100000).toString().padStart(5, '0')}`;
              this.direccionFinal = `${this.formPago.direccionDetalle}, ${this.formPago.distrito}, Lima`;
              this.pagoExitoso = true;
              this.procesandoPago = false;

              this.cartService.clear();
              setTimeout(() => { this.router.navigate(['/catalogo']); }, 4000);
          },
          error: (err) => {
              this.mensajeError = err.error?.error || 'Error al crear el pedido final.';
              this.procesandoPago = false;
          }
      });
  }

  // --- LÓGICA DE RECETA CHECKOUT ---

  requiereRecetaMedica(nombre: string): boolean {
    if (!nombre) return false;
    const n = nombre.toLowerCase();
    return n.includes('clonazepam') || n.includes('losartan') || n.includes('losartán');
  }

  onRecetaFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.recetaFile = input.files[0];
      this.recetaFileName = input.files[0].name;
    }
  }

  subirRecetaCheckout(): void {
    this.recetaMensajeError = '';
    this.recetaMensajeExito = '';

    if (!this.recetaForm.medicoId.trim()) {
      this.recetaMensajeError = 'Por favor, ingresa el nombre/colegiatura del médico.';
      return;
    }
    if (!this.recetaFile) {
      this.recetaMensajeError = 'Por favor, selecciona el archivo de la receta médica.';
      return;
    }

    this.subiendoReceta = true;

    // 1. Subir documento a Supabase
    this.recetasService.subirDocumento(this.recetaFile).subscribe({
      next: (uploadRes: { url: string }) => {
        // 2. Crear receta en base de datos
        const usuario = this.authService.getCurrentUser();
        const nuevaReceta: RecetaMedica = {
          pacienteId: usuario?.id ? usuario.id.toString() : '',
          medicoId: this.recetaForm.medicoId,
          medicamentos: this.productosCarrito
            .filter(item => this.requiereRecetaMedica(item.producto.nombre))
            .map(item => ({ nombre: item.producto.nombre, dosis: `${item.cantidad} u.` })),
          fechaEmision: this.recetaForm.fechaEmision,
          estado: 'en_espera',
          documentoUrl: uploadRes.url
        };

        this.recetasService.cargarReceta(nuevaReceta).subscribe({
          next: (savedReceta: RecetaMedica) => {
            this.subiendoReceta = false;
            this.recetaSubidaId = savedReceta.idReceta || parseInt(savedReceta.id || '0', 10);
            this.recetaSubidaExito = true;
            this.recetaMensajeExito = '¡Receta médica subida y vinculada con éxito!';
          },
          error: (err: any) => {
            this.subiendoReceta = false;
            this.recetaMensajeError = 'Error al registrar la receta en la base de datos.';
            console.error('Error al registrar receta:', err);
          }
        });
      },
      error: (err: any) => {
        this.subiendoReceta = false;
        this.recetaMensajeError = 'Error al subir la imagen del documento de receta.';
        console.error('Error al subir receta:', err);
      }
    });
  }
}
