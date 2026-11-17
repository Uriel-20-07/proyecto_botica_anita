import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Tipos del chatbot. Reflejan los DTO del backend (paquete com.example.demo.dto.chatbot).
 */

/** Paso del flujo guiado en el que se encuentra el cliente. */
export type PasoChatbot = 'CATEGORIA' | 'SUBCATEGORIA' | 'MARCA' | 'PRODUCTOS' | 'SIN_RESULTADOS';

/** Filtros que el cliente va acumulando con los botones (ChatbotFiltros). */
export interface ChatbotFiltros {
  idCategoria: number | null;
  nombreCategoria: string | null;
  idSubcategoria: number | null;
  nombreSubcategoria: string | null;
  idMarca: number | null;
  nombreMarca: string | null;
  soloOfertas: boolean;
  omitirSubcategoria: boolean;
  omitirMarca: boolean;
  pagina: number;
}

export function filtrosVacios(): ChatbotFiltros {
  return {
    idCategoria: null,
    nombreCategoria: null,
    idSubcategoria: null,
    nombreSubcategoria: null,
    idMarca: null,
    nombreMarca: null,
    soloOfertas: false,
    omitirSubcategoria: false,
    omitirMarca: false,
    pagina: 0
  };
}

/** Opción tocable: categoría, subcategoría o marca (OpcionDTO). */
export interface OpcionChat {
  id: number;
  nombre: string;
  total: number;
  totalOfertas: number;
}

/** Producto mínimo para mostrarse como tarjeta en el chat (ProductoChatDTO). */
export interface ProductoChat {
  idProducto: number;
  nombre: string;
  precioRegular: number;
  precioOferta: number | null;
  enOferta: boolean;
  precioFinal: number;
  descuentoPorcentaje: number;
  agotado: boolean;
  maxPorPedido: number;
  imgUrl: string | null;
}

/** Producto que ya se mostró como tarjeta; el backend lo excluye cuando el cliente pide "otras opciones" (ProductoMostrado). */
export interface ProductoMostrado {
  idProducto: number;
  nombre: string;
}

/** Respuesta del flujo guiado (ChatbotPasoResponse). */
export interface ChatbotPasoResponse {
  paso: PasoChatbot;
  pregunta: string;
  opciones: OpcionChat[];
  productos: ProductoChat[];
  hayMas: boolean;
  filtros: ChatbotFiltros;
}

/** Mensaje del historial que se envía cuando el cliente escribe (ChatMessage). */
export interface ChatMensajeReq {
  role: 'user' | 'model';
  content: string;
}

/** Acción que debe ejecutar el navegador por pedido del chatbot. */
export interface LlamadaFuncion {
  name: string;
  args?: Record<string, unknown>;
}

/** Respuesta del chatbot a un mensaje escrito (ChatResponse). */
export interface ChatRespuesta {
  role: 'model';
  content: string | null;
  functionCall: LlamadaFuncion | null;
  productos: ProductoChat[] | null;
  paso: ChatbotPasoResponse | null;
}

/**
 * Servicio HTTP del chatbot.
 *
 * - paso(): flujo guiado por botones (rápido, sin IA). Es público: solo lee el catálogo.
 * - consultar(): texto libre (usa Gemini en el backend). Envía el token del cliente, porque el backend
 *   exige sesión y limita cuántos mensajes puede enviar cada persona.
 */
@Injectable({
  providedIn: 'root'
})
export class ChatbotService {
  private readonly apiUrl = `${environment.apiUrl}/api/chatbot`;

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) { }

  /** Pide el siguiente paso del flujo guiado según los filtros elegidos. */
  paso(filtros: ChatbotFiltros): Observable<ChatbotPasoResponse> {
    return this.http.post<ChatbotPasoResponse>(`${this.apiUrl}/paso`, filtros);
  }

  /**
   * Envía lo que el cliente escribió para que SofIA responda, junto con los filtros que ya eligió y los productos
   * que ya tiene a la vista (para no repetirlos cuando pida otras opciones).
   */
  consultar(
    messages: ChatMensajeReq[],
    filtros: ChatbotFiltros,
    yaMostrados: ProductoMostrado[] = []
  ): Observable<ChatRespuesta> {
    const token = this.authService.getToken();
    const headers = token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : undefined;
    return this.http.post<ChatRespuesta>(`${this.apiUrl}/consultar`, { messages, filtros, yaMostrados }, { headers });
  }
}
