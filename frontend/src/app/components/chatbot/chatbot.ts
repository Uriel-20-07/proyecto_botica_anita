import { Component, OnInit, Inject, PLATFORM_ID, ViewChild, ElementRef } from '@angular/core';
import { isPlatformBrowser, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CartService } from '../../services/cart.service';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';
import {
  ChatbotService,
  ChatbotFiltros,
  ChatbotPasoResponse,
  ChatMensajeReq,
  ChatRespuesta,
  LlamadaFuncion,
  OpcionChat,
  PasoChatbot,
  ProductoChat,
  ProductoMostrado,
  filtrosVacios
} from '../../services/chatbot.service';

/** Botones secundarios que aparecen debajo de una pregunta o de un resultado. */
type AccionId =
  | 'OFERTAS'
  | 'TODAS_SUBCATEGORIAS'
  | 'TODAS_MARCAS'
  | 'VER_MAS'
  | 'REINICIAR'
  | 'ESCRIBIR'
  | 'VER_CARRITO'
  | 'IR_PAGO';

interface AccionRapida {
  id: AccionId;
  etiqueta: string;
}

type TipoChip = 'categoria' | 'subcategoria' | 'marca' | 'ofertas';

interface ChipFiltro {
  tipo: TipoChip;
  etiqueta: string;
}

interface MensajeChat {
  role: 'user' | 'model';
  /** Texto plano (sin HTML). Se formatea solo al mostrarlo. */
  content: string;
  /**
   * true = mensaje generado por el flujo de botones o por la propia interfaz.
   * Estos mensajes NO se envían a Gemini: solo cuenta lo que el cliente escribió y lo que SofIA respondió.
   */
  guiado?: boolean;
  /** Paso del flujo al que pertenecen las opciones de este mensaje. */
  paso?: PasoChatbot;
  opciones?: OpcionChat[];
  productos?: ProductoChat[];
  acciones?: AccionRapida[];
  /**
   * Presente solo en mensajes del modelo que ofrecen una confirmación con una cantidad alternativa
   * (cuando el backend rechaza por límite o stock pero sugiere un máximo).
   */
  confirmacion?: { idProducto: number; cantidad: number; nombre?: string };
}

/** El primer paso (categorías) casi no cambia: se reutiliza unos minutos para que "Empezar de nuevo" sea instantáneo. */
const VIGENCIA_PASO_INICIAL_MS = 5 * 60 * 1000;

/** Cuántos mensajes recientes se envían a SofIA cuando el cliente escribe. */
const MAX_HISTORIAL = 8;

/** Cuántos productos ya mostrados se informan al backend (los más recientes primero). */
const MAX_MOSTRADOS = 12;

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrls: ['./chatbot.css']
})
export class ChatbotComponent implements OnInit {
  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLElement>;
  @ViewChild('campoTexto') private campoTexto?: ElementRef<HTMLInputElement>;

  isOpen = false;
  isLoading = false;
  userInput = '';
  messages: MensajeChat[] = [];

  /** Lo que el cliente lleva elegido con los botones. Es el único estado del flujo guiado. */
  filtros: ChatbotFiltros = filtrosVacios();

  /** Chips con los filtros elegidos (se recalculan solo cuando cambian los filtros). */
  chips: ChipFiltro[] = [];

  /** Ids de productos que se están agregando ahora mismo (evita doble clic). */
  readonly agregando = new Set<number>();

  private guiaIniciada = false;
  private cachePasoInicial: { respuesta: ChatbotPasoResponse; ts: number } | null = null;

  constructor(
    @Inject(PLATFORM_ID) private platformId: Object,
    private readonly chatbotService: ChatbotService,
    private readonly router: Router,
    private readonly cartService: CartService,
    private readonly authService: AuthService,
    private readonly authModalService: AuthModalService
  ) { }

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.messages.push({
        role: 'model',
        guiado: true,
        content: '¡Hola! 👋 Soy SofIA, la asistente virtual de BoticaAnita. Estoy aquí para ayudarte a encontrar lo que necesitas, con mucho gusto 💙'
      });
    }
  }

  toggleChat() {
    if (!this.authService.isAuthenticated()) {
      this.authModalService.open('registro'); // Abre el modal de registro
      return;
    }
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      // Las categorías se piden la primera vez que se abre el chat, no al cargar la página
      if (!this.guiaIniciada) {
        this.guiaIniciada = true;
        this.cargarPaso();
      }
      this.scrollToBottom();
    }
  }

  // ------------------------------------------------------------------
  // Flujo guiado (botones)
  // ------------------------------------------------------------------

  /** El cliente tocó una categoría, subcategoría o marca. */
  elegirOpcion(msg: MensajeChat, opcion: OpcionChat): void {
    if (this.isLoading || !msg.paso) return;

    const f: ChatbotFiltros = { ...this.filtros, pagina: 0 };
    switch (msg.paso) {
      case 'CATEGORIA':
        f.idCategoria = opcion.id;
        f.nombreCategoria = opcion.nombre;
        f.idSubcategoria = null;
        f.nombreSubcategoria = null;
        f.idMarca = null;
        f.nombreMarca = null;
        f.omitirSubcategoria = false;
        f.omitirMarca = false;
        break;
      case 'SUBCATEGORIA':
        f.idSubcategoria = opcion.id;
        f.nombreSubcategoria = opcion.nombre;
        f.omitirSubcategoria = false;
        f.idMarca = null;
        f.nombreMarca = null;
        f.omitirMarca = false;
        break;
      case 'MARCA':
        f.idMarca = opcion.id;
        f.nombreMarca = opcion.nombre;
        f.omitirMarca = false;
        break;
      default:
        return;
    }

    this.mensajeUsuario(opcion.nombre);
    this.aplicarFiltros(f);
    this.cargarPaso();
  }

  /** El cliente tocó un botón secundario ("Solo ofertas", "Ver más", "Empezar de nuevo"...). */
  ejecutarAccion(accion: AccionRapida): void {
    if (this.isLoading) return;
    this.mensajeUsuario(this.sinEmojiInicial(accion.etiqueta));

    switch (accion.id) {
      case 'OFERTAS':
        this.aplicarFiltros({ ...this.filtros, soloOfertas: true, pagina: 0 });
        this.cargarPaso();
        break;
      case 'TODAS_SUBCATEGORIAS':
        this.aplicarFiltros({
          ...this.filtros,
          idSubcategoria: null,
          nombreSubcategoria: null,
          omitirSubcategoria: true,
          idMarca: null,
          nombreMarca: null,
          omitirMarca: false,
          pagina: 0
        });
        this.cargarPaso();
        break;
      case 'TODAS_MARCAS':
        this.aplicarFiltros({ ...this.filtros, idMarca: null, nombreMarca: null, omitirMarca: true, pagina: 0 });
        this.cargarPaso();
        break;
      case 'VER_MAS':
        this.aplicarFiltros({ ...this.filtros, pagina: this.filtros.pagina + 1 });
        this.cargarPaso();
        break;
      case 'REINICIAR':
        this.aplicarFiltros(filtrosVacios());
        this.cargarPaso();
        break;
      case 'ESCRIBIR':
        this.mensajeModelo('¡Claro! Cuéntame con tus palabras qué necesitas y con gusto te ayudo 😊');
        setTimeout(() => this.campoTexto?.nativeElement.focus(), 50);
        break;
      case 'VER_CARRITO':
        this.irA('/carrito', 'Claro 😊 Te llevo a tu carrito.');
        break;
      case 'IR_PAGO':
        this.irA('/pago', '¡Perfecto! Te llevo a pagar 😊');
        break;
    }
  }

  /** El cliente tocó la ✕ de un chip: se quita ese filtro y todo lo que dependía de él. */
  quitarFiltro(tipo: TipoChip): void {
    if (this.isLoading) return;
    const f: ChatbotFiltros = { ...this.filtros, pagina: 0 };

    switch (tipo) {
      case 'categoria':
        f.idCategoria = null;
        f.nombreCategoria = null;
        f.idSubcategoria = null;
        f.nombreSubcategoria = null;
        f.omitirSubcategoria = false;
        f.idMarca = null;
        f.nombreMarca = null;
        f.omitirMarca = false;
        break;
      case 'subcategoria':
        f.idSubcategoria = null;
        f.nombreSubcategoria = null;
        f.omitirSubcategoria = false;
        f.idMarca = null;
        f.nombreMarca = null;
        f.omitirMarca = false;
        break;
      case 'marca':
        f.idMarca = null;
        f.nombreMarca = null;
        f.omitirMarca = false;
        break;
      case 'ofertas':
        f.soloOfertas = false;
        break;
    }

    this.limpiarBotonesAnteriores();
    this.mensajeModelo('Sin problema 😊 Volvamos un paso atrás.');
    this.aplicarFiltros(f);
    this.cargarPaso();
  }

  /** Pide el siguiente paso al backend (o reutiliza el primer paso si es reciente). */
  private cargarPaso(): void {
    const inicial = this.esInicio(this.filtros);

    if (inicial && this.cachePasoInicial && Date.now() - this.cachePasoInicial.ts < VIGENCIA_PASO_INICIAL_MS) {
      this.mostrarPaso(this.cachePasoInicial.respuesta);
      return;
    }

    this.isLoading = true;
    this.scrollToBottom();
    this.chatbotService.paso(this.filtros).subscribe({
      next: (respuesta) => {
        this.isLoading = false;
        if (inicial && respuesta.paso === 'CATEGORIA') {
          this.cachePasoInicial = { respuesta, ts: Date.now() };
        }
        this.mostrarPaso(respuesta);
      },
      error: (err) => {
        this.isLoading = false;
        console.error('Error al cargar el flujo guiado del chatbot:', err);
        this.limpiarBotonesAnteriores();
        this.mensajeModelo('Ay, no pude cargar las opciones en este momento 😔 ¿Lo intentamos de nuevo?', {
          acciones: [{ id: 'REINICIAR', etiqueta: 'Intentar de nuevo' }]
        });
      }
    });
  }

  /** Muestra la pregunta de SofIA con sus botones o con las tarjetas de productos. */
  private mostrarPaso(respuesta: ChatbotPasoResponse): void {
    // El backend puede haber elegido solo una opción única: se sincronizan los chips con lo que quedó aplicado
    this.aplicarFiltros({ ...filtrosVacios(), ...respuesta.filtros });
    this.limpiarBotonesAnteriores();
    this.messages.push({
      role: 'model',
      guiado: true,
      content: respuesta.pregunta,
      paso: respuesta.paso,
      opciones: [...(respuesta.opciones ?? [])],
      productos: [...(respuesta.productos ?? [])],
      acciones: this.accionesDe(respuesta)
    });
    this.scrollToBottom();
  }

  private accionesDe(respuesta: ChatbotPasoResponse): AccionRapida[] {
    const acciones: AccionRapida[] = [];
    const paso = respuesta.paso;
    const hayOfertas =
      (respuesta.opciones ?? []).some((o) => o.totalOfertas > 0) ||
      (respuesta.productos ?? []).some((p) => p.enOferta);

    if (paso === 'SUBCATEGORIA') {
      acciones.push({ id: 'TODAS_SUBCATEGORIAS', etiqueta: 'Ver todo en ' + (this.filtros.nombreCategoria ?? 'esta categoría') });
    } else if (paso === 'MARCA') {
      acciones.push({ id: 'TODAS_MARCAS', etiqueta: 'Cualquier marca' });
    } else if (paso === 'PRODUCTOS' && respuesta.hayMas) {
      acciones.push({ id: 'VER_MAS', etiqueta: 'Ver más opciones' });
    }

    if (!this.filtros.soloOfertas && hayOfertas && paso !== 'SIN_RESULTADOS') {
      acciones.push({ id: 'OFERTAS', etiqueta: paso === 'CATEGORIA' ? '🔥 Ver ofertas' : '🔥 Solo ofertas' });
    }
    if (paso === 'CATEGORIA' || paso === 'SIN_RESULTADOS') {
      acciones.push({ id: 'ESCRIBIR', etiqueta: '✍️ Prefiero escribir' });
    }
    if (paso !== 'CATEGORIA') {
      acciones.push({ id: 'REINICIAR', etiqueta: '↩️ Empezar de nuevo' });
    }
    return acciones;
  }

  // ------------------------------------------------------------------
  // Texto libre (SofIA con IA)
  // ------------------------------------------------------------------

  sendMessage(event: Event) {
    event.preventDefault();
    const texto = this.userInput.trim();
    if (!texto || this.isLoading) return;

    this.userInput = '';
    this.messages.push({ role: 'user', content: texto });
    this.isLoading = true;
    this.scrollToBottom();

    this.chatbotService.consultar(this.construirHistorial(), this.filtros, this.productosMostrados()).subscribe({
      next: (respuesta) => {
        this.isLoading = false;
        this.procesarRespuesta(respuesta);
      },
      error: (err) => {
        this.isLoading = false;
        console.error('Error al consultar chatbot:', err);
        this.limpiarBotonesAnteriores();
        this.mensajeModelo(
          'Ay, no logré comunicarme en este momento 😔 Revisa tu conexión e inténtalo de nuevo. Mientras tanto, puedes usar los botones.',
          { acciones: [{ id: 'REINICIAR', etiqueta: 'Ver opciones' }] }
        );
      }
    });
  }

  private procesarRespuesta(respuesta: ChatRespuesta | null): void {
    if (!respuesta) {
      this.mensajeModelo('Mmm, no recibí una respuesta clara 😅 ¿Lo intentamos otra vez?');
      return;
    }

    // 1. Acción que debe ejecutar el navegador (agregar al carrito, redirigir)
    if (respuesta.functionCall) {
      this.ejecutarFuncion(respuesta.functionCall);
      return;
    }

    // 2. Texto de SofIA, con las tarjetas de productos si hubo búsqueda (no se envían los guiados a Gemini)
    this.messages.push({
      role: 'model',
      content: respuesta.content ?? '¿Me lo repites, por favor? 😊',
      productos: respuesta.productos ?? undefined
    });

    // 3. Si no se encontró nada (o hubo un problema), SofIA vuelve a ofrecer los botones
    if (respuesta.paso) {
      this.mostrarPaso(respuesta.paso);
    }
    this.scrollToBottom();
  }

  private ejecutarFuncion(llamada: LlamadaFuncion): void {
    const args = llamada.args ?? {};

    if (llamada.name === 'agregarAlCarrito') {
      const nombre = typeof args['nombre'] === 'string' ? args['nombre'] : undefined;
      this.ejecutarAgregarAlCarrito(Number(args['idProducto']), Number(args['cantidad']) || 1, nombre);
    } else if (llamada.name === 'redirigir') {
      const ruta = String(args['ruta'] ?? '');
      if (ruta.startsWith('/')) {
        this.irA(ruta, 'Entendido 😊 Te llevo ahí ahora mismo.');
      } else {
        this.mensajeModelo('Mmm, no pude abrir esa página 😅 ¿Me cuentas qué necesitas?');
      }
    }
  }

  /** Solo se envía a SofIA lo que el cliente escribió y lo que ella respondió (nunca los mensajes de los botones). */
  private construirHistorial(): ChatMensajeReq[] {
    return this.messages
      .filter((m) => !m.guiado && m.content.trim().length > 0)
      .slice(-MAX_HISTORIAL)
      .map((m) => ({ role: m.role, content: m.content }));
  }

  /**
   * Productos que el cliente ya tiene a la vista como tarjetas, de los más recientes a los más antiguos y sin
   * repetir. Así, cuando pide "otras opciones", SofIA no le vuelve a mostrar los mismos.
   */
  private productosMostrados(): ProductoMostrado[] {
    const vistos = new Map<number, ProductoMostrado>();
    for (let i = this.messages.length - 1; i >= 0 && vistos.size < MAX_MOSTRADOS; i--) {
      for (const p of this.messages[i].productos ?? []) {
        if (!vistos.has(p.idProducto) && vistos.size < MAX_MOSTRADOS) {
          vistos.set(p.idProducto, { idProducto: p.idProducto, nombre: p.nombre });
        }
      }
    }
    return [...vistos.values()];
  }

  // ------------------------------------------------------------------
  // Carrito
  // ------------------------------------------------------------------

  /** El cliente tocó "Agregar" en una tarjeta de producto. */
  agregarProducto(producto: ProductoChat): void {
    if (producto.agotado || this.agregando.has(producto.idProducto)) return;
    this.ejecutarAgregarAlCarrito(producto.idProducto, 1, producto.nombre);
  }

  /**
   * Ejecuta la agregación al carrito y reacciona al resultado real del backend.
   *
   * Se espera la respuesta: si el backend acepta, se celebra y se ofrece seguir. Si rechaza
   * (HTTP 400 con { error, maxAdicional }), se muestra el motivo y, cuando maxAdicional > 0,
   * se ofrecen botones para agregar exactamente esa cantidad alternativa.
   */
  private ejecutarAgregarAlCarrito(idProducto: number, cantidad: number, nombre?: string): void {
    this.agregando.add(idProducto);

    this.cartService.addWithQty(idProducto, cantidad).subscribe({
      next: () => {
        this.agregando.delete(idProducto);
        const que = nombre ? `**${nombre}**` : 'el producto';
        const cuanto = cantidad > 1 ? `${cantidad} unidades de ${que}` : que;
        this.limpiarBotonesAnteriores();
        this.mensajeModelo(`¡Listo! Agregué ${cuanto} a tu carrito 🛒 ¿Qué te gustaría hacer ahora?`, {
          acciones: [
            { id: 'REINICIAR', etiqueta: 'Seguir comprando' },
            { id: 'VER_CARRITO', etiqueta: 'Ver mi carrito' },
            { id: 'IR_PAGO', etiqueta: 'Ir a pagar' }
          ]
        });
      },
      error: (err) => {
        this.agregando.delete(idProducto);

        if (err?.status === 401 || err?.status === 403) {
          this.mensajeModelo('Para agregar productos necesitas iniciar sesión 😊 Ya te abrí la ventana para que ingreses.');
          return;
        }

        // El backend responde 400 con { error, maxAdicional } para rechazos de negocio.
        const cuerpo = err?.error;
        const mensaje: string = cuerpo?.error || 'No pude agregar el producto al carrito 😔 ¿Lo intentamos de nuevo?';
        const maxAdicional: number = cuerpo?.maxAdicional ?? 0;

        if (maxAdicional > 0) {
          this.mensajeModelo(`${mensaje} ¿Te agrego ${maxAdicional} unidad(es)?`, {
            confirmacion: { idProducto, cantidad: maxAdicional, nombre }
          });
        } else {
          this.mensajeModelo(mensaje);
        }
      }
    });
  }

  /** Confirma y agrega la cantidad alternativa ofrecida por SofIA (la máxima permitida según el backend). */
  confirmarCantidad(msg: MensajeChat): void {
    if (!msg.confirmacion) return;
    const { idProducto, cantidad, nombre } = msg.confirmacion;

    this.mensajeUsuario('Sí, agregar ' + cantidad + ' unidad(es)');
    msg.confirmacion = undefined;
    this.ejecutarAgregarAlCarrito(idProducto, cantidad, nombre);
  }

  /** Cancela la confirmación ofrecida: no se agrega nada al carrito. */
  cancelarConfirmacion(msg: MensajeChat): void {
    if (!msg.confirmacion) return;
    msg.confirmacion = undefined;
    this.mensajeUsuario('No, gracias');
    this.mensajeModelo('Sin problema 😊 No agregué nada. ¿Te ayudo con algo más?');
  }

  // ------------------------------------------------------------------
  // Auxiliares
  // ------------------------------------------------------------------

  /** Convierte el texto de SofIA en HTML seguro: escapa todo y solo permite saltos de línea y **negrita**. */
  renderContent(text: string): string {
    if (!text) return '';
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  }

  private aplicarFiltros(filtros: ChatbotFiltros): void {
    this.filtros = filtros;
    this.chips = this.calcularChips(filtros);
  }

  private calcularChips(f: ChatbotFiltros): ChipFiltro[] {
    const chips: ChipFiltro[] = [];
    if (f.idCategoria !== null) {
      chips.push({ tipo: 'categoria', etiqueta: f.nombreCategoria ?? 'Categoría' });
    }
    if (f.idSubcategoria !== null) {
      chips.push({ tipo: 'subcategoria', etiqueta: f.nombreSubcategoria ?? 'Subcategoría' });
    } else if (f.omitirSubcategoria) {
      chips.push({ tipo: 'subcategoria', etiqueta: 'Todas las subcategorías' });
    }
    if (f.idMarca !== null) {
      chips.push({ tipo: 'marca', etiqueta: f.nombreMarca ?? 'Marca' });
    } else if (f.omitirMarca) {
      chips.push({ tipo: 'marca', etiqueta: 'Todas las marcas' });
    }
    if (f.soloOfertas) {
      chips.push({ tipo: 'ofertas', etiqueta: '🔥 Solo ofertas' });
    }
    return chips;
  }

  private esInicio(f: ChatbotFiltros): boolean {
    return (
      f.idCategoria === null &&
      f.idSubcategoria === null &&
      f.idMarca === null &&
      !f.soloOfertas &&
      !f.omitirSubcategoria &&
      !f.omitirMarca &&
      f.pagina === 0
    );
  }

  /** Quita los botones de los mensajes anteriores para que no se pueda tocar una pregunta ya contestada. */
  private limpiarBotonesAnteriores(): void {
    for (const m of this.messages) {
      m.opciones = undefined;
      m.acciones = undefined;
    }
  }

  /** Lo que el cliente tocó se muestra como un mensaje suyo (y no viaja a Gemini). */
  private mensajeUsuario(texto: string): void {
    this.limpiarBotonesAnteriores();
    this.messages.push({ role: 'user', content: texto, guiado: true });
    this.scrollToBottom();
  }

  private mensajeModelo(texto: string, extra: Partial<MensajeChat> = {}): void {
    this.messages.push({ role: 'model', content: texto, guiado: true, ...extra });
    this.scrollToBottom();
  }

  private irA(ruta: string, mensaje: string): void {
    this.mensajeModelo(mensaje);
    this.router.navigate([ruta]);
    this.isOpen = false; // Cerrar el chat al redirigir
  }

  /** "🔥 Solo ofertas" → "Solo ofertas" (para mostrarlo como mensaje del cliente). */
  private sinEmojiInicial(texto: string): string {
    return texto.replace(/^[^\p{L}\p{N}]+/u, '');
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
