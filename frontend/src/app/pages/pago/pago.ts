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

  readonly distritosArequipa: string[] = [
    'Alto Selva Alegre', 'Arequipa (Cercado)', 'Cayma', 'Cerro Colorado',
    'Characato', 'Chiguata', 'Jacobo Hunter', 'José Luis Bustamante y Rivero',
    'La Joya', 'Mariano Melgar', 'Miraflores', 'Mollebaya', 'Paucarpata',
    'Pocsi', 'Polobaya', 'Quequeña', 'Sabandía', 'Sachaca', 'San Juan de Siguas',
    'San Juan de Tarucani', 'Santa Isabel de Siguas', 'Santa Rita de Siguas',
    'Socabaya', 'Tiabaya', 'Uchumayo', 'Vitor', 'Yanahuara', 'Yarabamba',
    'Yura'
  ];

  readonly puntosRecojoPorDistrito: { [key: string]: { nombre: string; direccion: string; referencia: string }[] } = {
    'Alto Selva Alegre': [
      { nombre: 'Bodega San Lucho', direccion: 'Av. Arequipa 302', referencia: 'Frente a la Plaza de Alto Selva Alegre' },
      { nombre: 'Minimarket Lucerito', direccion: 'Calle España 512', referencia: 'A una cuadra de la comisaría' },
      { nombre: 'Tienda La Esperanza', direccion: 'Av. Las Torres 104', referencia: 'Al lado de la I.E. Santa Rosa de Lima' },
      { nombre: 'Bodega Rosita', direccion: 'Pasaje Florida 205', referencia: 'Frente al Parque de la Juventud' },
      { nombre: 'Minimarket El Sol', direccion: 'Calle Arica 418', referencia: 'Cerca al mercado principal de ASA' }
    ],
    'Arequipa (Cercado)': [
      { nombre: 'Bodega La Merced', direccion: 'Calle La Merced 204', referencia: 'A media cuadra de la Plaza de Armas' },
      { nombre: 'Minimarket El Misti', direccion: 'Calle San Juan de Dios 315', referencia: 'Frente a la galería comercial' },
      { nombre: 'Tienda Jerusalén', direccion: 'Calle Jerusalén 408', referencia: 'Al costado del banco de la Nación' },
      { nombre: 'Bodega Santo Domingo', direccion: 'Calle Santo Domingo 112', referencia: 'Frente a la iglesia Santo Domingo' },
      { nombre: 'Minimarket Central', direccion: 'Calle Mercaderes 220', referencia: 'Cerca al pasaje peatonal' }
    ],
    'Cayma': [
      { nombre: 'Bodega Cayma Centro', direccion: 'Av. Cayma 605', referencia: 'Frente a la plaza principal de Cayma' },
      { nombre: 'Minimarket El Solitario', direccion: 'Calle Melgar 102', referencia: 'Cerca al mirador de Carmen Alto' },
      { nombre: 'Tienda Bolognesi', direccion: 'Av. Bolognesi 410', referencia: 'Al lado del policlínico de Cayma' },
      { nombre: 'Bodega La Tradición', direccion: 'Calle Tronchadero 304', referencia: 'Frente a la posta médica' },
      { nombre: 'Minimarket Los Arcos', direccion: 'Av. Ramón Castilla 715', referencia: 'Cerca al puente Chilina' }
    ],
    'Cerro Colorado': [
      { nombre: 'Bodega El Misti Colorado', direccion: 'Av. Aviación 1102', referencia: 'Frente al centro comercial Arequipa Center' },
      { nombre: 'Minimarket Zamácola', direccion: 'Calle Italia 205', referencia: 'Cerca a la Plaza de Zamácola' },
      { nombre: 'Tienda Libertad', direccion: 'Av. Villa Hermosa 412', referencia: 'Al lado del mercado de Cerro Colorado' },
      { nombre: 'Bodega Las Flores', direccion: 'Calle Alfonso Ugarte 308', referencia: 'Frente a la comisaría de Cerro Colorado' },
      { nombre: 'Minimarket Challapampa', direccion: 'Av. Metropolitana 501', referencia: 'Cerca a la zona residencial Challapampa' }
    ],
    'Characato': [
      { nombre: 'Bodega Characato Real', direccion: 'Calle Grau 105', referencia: 'Frente a la Plaza de Armas de Characato' },
      { nombre: 'Minimarket Sabandía', direccion: 'Av. Characato 304', referencia: 'Al lado del paradero final de buses' },
      { nombre: 'Tienda El Manantial', direccion: 'Calle Bolognesi 201', referencia: 'Frente al ojo de agua de Characato' },
      { nombre: 'Bodega Tradición Loncca', direccion: 'Av. Arequipa 512', referencia: 'Cerca de la municipalidad' },
      { nombre: 'Minimarket El Campo', direccion: 'Calle Melgar 108', referencia: 'Frente a la cancha de toros' }
    ],
    'Chiguata': [
      { nombre: 'Bodega Chiguata Centro', direccion: 'Calle Real 204', referencia: 'Frente a la Plaza Principal de Chiguata' },
      { nombre: 'Tienda El Pino', direccion: 'Av. Pichu Pichu 102', referencia: 'A una cuadra de la iglesia Espíritu Santo' },
      { nombre: 'Bodega La Campiña', direccion: 'Calle La Libertad 305', referencia: 'Cerca al complejo deportivo' },
      { nombre: 'Minimarket San Bernardo', direccion: 'Calle Bolognesi 115', referencia: 'Frente a la posta de salud de Chiguata' },
      { nombre: 'Tienda El Mirador de Chiguata', direccion: 'Calle Melgar 402', referencia: 'Cerca a la entrada del distrito' }
    ],
    'Jacobo Hunter': [
      { nombre: 'Bodega Hunter', direccion: 'Av. Viña del Mar 402', referencia: 'Frente al municipio de Jacobo Hunter' },
      { nombre: 'Minimarket El Bosque', direccion: 'Calle Arica 215', referencia: 'Cerca al mirador de Hunter' },
      { nombre: 'Tienda San Francisco', direccion: 'Av. San Miguel 308', referencia: 'Frente al colegio República de Francia' },
      { nombre: 'Bodega La Colina', direccion: 'Calle Los Libertadores 120', referencia: 'Al costado de la posta de salud' },
      { nombre: 'Minimarket Milagritos', direccion: 'Av. Mariscal Cáceres 604', referencia: 'Frente al mercado principal de Hunter' }
    ],
    'José Luis Bustamante y Rivero': [
      { nombre: 'Minimarket Bustamante', direccion: 'Av. Dolores 124', referencia: 'Frente a la zona de restaurantes' },
      { nombre: 'Bodega Las Brisas', direccion: 'Av. Estados Unidos 315', referencia: 'Cerca al óvalo de los Bomberos' },
      { nombre: 'Tienda Los Portales', direccion: 'Calle Colón 208', referencia: 'Frente a la urbanización Satélite' },
      { nombre: 'Bodega La Alborada', direccion: 'Av. Pizarro 410', referencia: 'Al costado de la comisaría de Bustamante' },
      { nombre: 'Minimarket Tres de Octubre', direccion: 'Calle Lambayeque 502', referencia: 'Cerca a la Av. Andres Avelino Cáceres' }
    ],
    'La Joya': [
      { nombre: 'Bodega La Joya Centro', direccion: 'Av. 2 de Mayo 104', referencia: 'Frente a la plaza principal de La Joya' },
      { nombre: 'Minimarket El Cruce', direccion: 'Panamericana Sur Km. 965', referencia: 'En el cruce principal de La Joya' },
      { nombre: 'Tienda San Isidro', direccion: 'Calle Bolognesi 302', referencia: 'Cerca al hospital de La Joya' },
      { nombre: 'Bodega El Valle', direccion: 'Av. Ferrocarril 205', referencia: 'Frente a la estación de tren' },
      { nombre: 'Minimarket Santa Rosa', direccion: 'Calle Real 410', referencia: 'Cerca a la municipalidad' }
    ],
    'Mariano Melgar': [
      { nombre: 'Bodega Melgar Centro', direccion: 'Av. Lima 602', referencia: 'Frente a la plaza Umachiri de Mariano Melgar' },
      { nombre: 'Minimarket La Victoria', direccion: 'Calle Comandante Canga 315', referencia: 'Cerca de la posta médica' },
      { nombre: 'Tienda El Misti Melgariano', direccion: 'Av. Simón Bolívar 804', referencia: 'Al lado del mercadillo El Filtro' },
      { nombre: 'Bodega Santa Rosa', direccion: 'Calle San Martín 208', referencia: 'Cerca al complejo deportivo' },
      { nombre: 'Minimarket Las Flores', direccion: 'Av. Argentina 412', referencia: 'Frente a la comisaría de Mariano Melgar' }
    ],
    'Miraflores': [
      { nombre: 'Bodega Miraflores Centro', direccion: 'Av. Unión 502', referencia: 'Frente a la Plaza Mayta Cápac' },
      { nombre: 'Minimarket San Antonio', direccion: 'Calle Teniente Rodríguez 314', referencia: 'A una cuadra del hospital Goyeneche' },
      { nombre: 'Tienda Alameda', direccion: 'Av. Progreso 802', referencia: 'Frente al cuartel Salaverry' },
      { nombre: 'Bodega Chapi', direccion: 'Calle Pasaje San Pedro 105', referencia: 'Cerca a la Iglesia Chapi Chico' },
      { nombre: 'Minimarket El Porvenir', direccion: 'Calle Puno 408', referencia: 'Frente al complejo deportivo Miramar' }
    ],
    'Mollebaya': [
      { nombre: 'Bodega Mollebaya Centro', direccion: 'Calle Principal 102', referencia: 'Frente a la Plaza de Armas de Mollebaya' },
      { nombre: 'Tienda El Mirador', direccion: 'Calle Bolognesi 205', referencia: 'Cerca al municipio' },
      { nombre: 'Bodega Machahuaya', direccion: 'Av. Arequipa S/N', referencia: 'Frente al paradero final de colectivos' },
      { nombre: 'Minimarket El Trigal', direccion: 'Calle Libertad 304', referencia: 'A una cuadra de la posta de salud' },
      { nombre: 'Tienda San Pedro de Mollebaya', direccion: 'Calle Melgar 112', referencia: 'Cerca a la zona arqueológica de Mollebaya' }
    ],
    'Paucarpata': [
      { nombre: 'Bodega Paucarpata Centro', direccion: 'Av. Kennedy 702', referencia: 'Frente a la Plaza de Paucarpata' },
      { nombre: 'Minimarket Miguel Grau', direccion: 'Calle El Sol 204', referencia: 'Cerca al mercado 3 de Octubre' },
      { nombre: 'Tienda Los Balcones', direccion: 'Calle Colón 315', referencia: 'Al lado del centro de salud Pedro P. Díaz' },
      { nombre: 'Bodega Bellavista', direccion: 'Av. Jesús 1104', referencia: 'Frente al cementerio de Paucarpata' },
      { nombre: 'Minimarket La Campiña Paucarpata', direccion: 'Calle Progreso 508', referencia: 'Cerca al arco de Paucarpata' }
    ],
    'Pocsi': [
      { nombre: 'Bodega Pocsi Centro', direccion: 'Calle Real S/N', referencia: 'Frente a la Plaza Principal de Pocsi' },
      { nombre: 'Tienda La Tradición de Pocsi', direccion: 'Calle Bolognesi S/N', referencia: 'Cerca de la Iglesia de Pocsi' },
      { nombre: 'Bodega El Misti Pocsi', direccion: 'Av. Chiguata S/N', referencia: 'Al lado de la posta de Pocsi' },
      { nombre: 'Minimarket El Campo Pocsi', direccion: 'Calle Principal 104', referencia: 'Frente a la municipalidad' },
      { nombre: 'Tienda San Salvador', direccion: 'Calle Melgar 202', referencia: 'Cerca al paradero comunal' }
    ],
    'Polobaya': [
      { nombre: 'Bodega Polobaya Centro', direccion: 'Calle Principal 105', referencia: 'Frente a la Plaza de Polobaya' },
      { nombre: 'Tienda Santuario', direccion: 'Camino a Chapi S/N', referencia: 'Cerca al Santuario de la Virgen de Chapi' },
      { nombre: 'Bodega Polobaya Chico', direccion: 'Calle Real S/N', referencia: 'Frente a la capilla principal' },
      { nombre: 'Minimarket San José', direccion: 'Calle Bolognesi S/N', referencia: 'Al lado del puesto de salud' },
      { nombre: 'Tienda El Agricultor', direccion: 'Calle Melgar S/N', referencia: 'Cerca al local comunal' }
    ],
    'Quequeña': [
      { nombre: 'Bodega Quequeña Centro', direccion: 'Calle Real S/N', referencia: 'Frente a la Plaza de Quequeña' },
      { nombre: 'Tienda Los Mártires', direccion: 'Calle Libertad S/N', referencia: 'Cerca a la quebrada de Quequeña' },
      { nombre: 'Bodega Quequeña Antigua', direccion: 'Calle Sucre S/N', referencia: 'Frente a la capilla tradicional' },
      { nombre: 'Minimarket El Valle Quequeña', direccion: 'Calle Bolognesi S/N', referencia: 'Al lado de la posta médica' },
      { nombre: 'Tienda El Mirador Quequeña', direccion: 'Av. Arequipa S/N', referencia: 'Cerca a la zona turística' }
    ],
    'Sabandía': [
      { nombre: 'Bodega El Molino Sabandía', direccion: 'Av. Sabandía 204', referencia: 'Frente al Molino de Sabandía' },
      { nombre: 'Minimarket Sabandía Real', direccion: 'Calle Grau 102', referencia: 'A una cuadra de la plaza principal' },
      { nombre: 'Tienda Los Andenes', direccion: 'Av. Characato 305', referencia: 'Frente al paradero principal de Sabandía' },
      { nombre: 'Bodega La Campiña Sabandía', direccion: 'Calle Bolognesi 410', referencia: 'Cerca del restaurant El Turco' },
      { nombre: 'Minimarket San Martín', direccion: 'Calle Melgar 212', referencia: 'Frente al colegio de Sabandía' }
    ],
    'Sachaca': [
      { nombre: 'Bodega Sachaca Centro', direccion: 'Calle Mariscal Castilla 302', referencia: 'Frente a la Plaza de Sachaca' },
      { nombre: 'Minimarket Pampa de Camarones', direccion: 'Av. Fernandini 415', referencia: 'Cerca a la Iglesia de Pampa de Camarones' },
      { nombre: 'Tienda Tahuaycani', direccion: 'Av. Tahuaycani 104', referencia: 'Al lado de la urbanización Tahuaycani' },
      { nombre: 'Bodega El Mirador Sachaca', direccion: 'Calle Bolognesi 504', referencia: 'Cerca al mirador tradicional' },
      { nombre: 'Minimarket Arrayanes', direccion: 'Calle Los Arrayanes 210', referencia: 'Frente al club de golf' }
    ],
    'San Juan de Siguas': [
      { nombre: 'Bodega Siguas Centro', direccion: 'Panamericana Sur Km. 940', referencia: 'En la plaza de San Juan de Siguas' },
      { nombre: 'Tienda El Valle de Siguas', direccion: 'Calle Real S/N', referencia: 'Frente al puesto de salud de Siguas' },
      { nombre: 'Bodega La Unión Siguas', direccion: 'Av. Arequipa S/N', referencia: 'Cerca al puente Siguas' },
      { nombre: 'Minimarket San Juan', direccion: 'Calle Bolognesi S/N', referencia: 'Frente a la municipalidad' },
      { nombre: 'Tienda Agraria Siguas', direccion: 'Calle Melgar S/N', referencia: 'Al lado de la junta de regantes' }
    ],
    'San Juan de Tarucani': [
      { nombre: 'Bodega Tarucani Centro', direccion: 'Calle Principal S/N', referencia: 'Frente a la Plaza de San Juan de Tarucani' },
      { nombre: 'Tienda El Altiplano', direccion: 'Av. Salinas S/N', referencia: 'Cerca a la laguna de Salinas' },
      { nombre: 'Bodega Vicuña Tarucani', direccion: 'Calle Real S/N', referencia: 'Frente a la posta de salud' },
      { nombre: 'Minimarket Tarucani', direccion: 'Calle Bolognesi S/N', referencia: 'Al lado del puesto policial' },
      { nombre: 'Tienda San Juanito', direccion: 'Calle Melgar S/N', referencia: 'Cerca al colegio agropecuario' }
    ],
    'Santa Isabel de Siguas': [
      { nombre: 'Bodega Santa Isabel', direccion: 'Calle Real S/N', referencia: 'Frente a la Plaza de Santa Isabel de Siguas' },
      { nombre: 'Tienda La Finca Siguas', direccion: 'Calle Bolognesi S/N', referencia: 'Cerca de la iglesia tradicional' },
      { nombre: 'Bodega El Paraíso Siguas', direccion: 'Av. Siguas S/N', referencia: 'Frente a la posta médica' },
      { nombre: 'Minimarket San Isidro Siguas', direccion: 'Calle Melgar S/N', referencia: 'Al lado de la municipalidad' },
      { nombre: 'Tienda Isabelina', direccion: 'Calle Libertad S/N', referencia: 'Cerca al puente colgante de Siguas' }
    ],
    'Santa Rita de Siguas': [
      { nombre: 'Bodega Santa Rita Centro', direccion: 'Calle Real S/N', referencia: 'Frente a la Plaza de Santa Rita de Siguas' },
      { nombre: 'Minimarket Santa Rita', direccion: 'Calle Libertad 102', referencia: 'Al lado de la posta de salud' },
      { nombre: 'Tienda Agrícola Siguas', direccion: 'Av. Panamericana S/N', referencia: 'Frente al banco de la Nación' },
      { nombre: 'Bodega La Joyita Siguas', direccion: 'Calle Bolognesi S/N', referencia: 'Cerca al mercado de abastos' },
      { nombre: 'Minimarket El Solitario Siguas', direccion: 'Calle Melgar S/N', referencia: 'Frente al complejo deportivo' }
    ],
    'Socabaya': [
      { nombre: 'Bodega Socabaya Centro', direccion: 'Av. Salaverry 402', referencia: 'Frente a la Plaza de Socabaya' },
      { nombre: 'Minimarket San Martín de Socabaya', direccion: 'Calle San Martín 305', referencia: 'Cerca a la Villa Olímpica' },
      { nombre: 'Tienda Horacio Zeballos', direccion: 'Calle Los Claveles 104', referencia: 'Frente al paradero final de Horacio Zeballos' },
      { nombre: 'Bodega Lara', direccion: 'Calle Lara 208', referencia: 'Cerca de la posta médica de Lara' },
      { nombre: 'Minimarket San Agustín', direccion: 'Av. Las Peñas 610', referencia: 'Frente a la urbanización Lara' }
    ],
    'Tiabaya': [
      { nombre: 'Bodega Tiabaya Centro', direccion: 'Calle Real 304', referencia: 'Frente a la Plaza de Tiabaya' },
      { nombre: 'Minimarket San José de Tiabaya', direccion: 'Av. Arequipa 512', referencia: 'Cerca del estadio municipal de Tiabaya' },
      { nombre: 'Tienda Los Perales', direccion: 'Calle Melgar 104', referencia: 'Cerca a la zona de campiña' },
      { nombre: 'Bodega Alata', direccion: 'Calle Alata 208', referencia: 'Frente a la posta de salud de Alata' },
      { nombre: 'Minimarket El Túnel Tiabaya', direccion: 'Calle Bolognesi 410', referencia: 'Cerca a la entrada del túnel de Tiabaya' }
    ],
    'Uchumayo': [
      { nombre: 'Bodega Congata', direccion: 'Calle Principal Congata 402', referencia: 'Frente a la Plaza de Congata' },
      { nombre: 'Minimarket Uchumayo Centro', direccion: 'Calle Real 204', referencia: 'Frente a la Plaza de Armas de Uchumayo' },
      { nombre: 'Tienda Cerro Verde', direccion: 'Calle Bolognesi 105', referencia: 'Cerca de la garita de Cerro Verde' },
      { nombre: 'Bodega El Misti de Uchumayo', direccion: 'Av. Arequipa S/N', referencia: 'Al lado de la posta de Uchumayo' },
      { nombre: 'Minimarket La Estación Uchumayo', direccion: 'Calle Melgar 312', referencia: 'Cerca de la antigua estación de tren' }
    ],
    'Vitor': [
      { nombre: 'Bodega Vitor Centro', direccion: 'Calle Real S/N', referencia: 'Frente a la Plaza de Armas de Vitor' },
      { nombre: 'Minimarket La Hacienda Vitor', direccion: 'Panamericana Sur Km. 950', referencia: 'Cerca de la entrada al valle de Vitor' },
      { nombre: 'Tienda El Porvenir Vitor', direccion: 'Av. Arequipa S/N', referencia: 'Frente a la posta médica de Vitor' },
      { nombre: 'Bodega San José Vitor', direccion: 'Calle Bolognesi S/N', referencia: 'Al lado de la municipalidad' },
      { nombre: 'Minimarket Vitoriano', direccion: 'Calle Melgar S/N', referencia: 'Cerca a la cooperativa agraria' }
    ],
    'Yanahuara': [
      { nombre: 'Bodega El Mirador Yanahuara', direccion: 'Calle Roma 102', referencia: 'A media cuadra del Mirador de Yanahuara' },
      { nombre: 'Minimarket Yanahuara Centro', direccion: 'Av. Ejército 315', referencia: 'Frente al centro comercial Plaza Vea' },
      { nombre: 'Tienda San Vicente', direccion: 'Calle Misti 408', referencia: 'Al lado del templo de Yanahuara' },
      { nombre: 'Bodega Umacollo', direccion: 'Calle Melgar 212', referencia: 'Frente al parque de Umacollo' },
      { nombre: 'Minimarket Chullo', direccion: 'Calle Chullo 504', referencia: 'Cerca a la clínica San Juan de Dios' },
      { nombre: 'Bodega Los Arces', direccion: 'Calle Los Arces 118', referencia: 'Frente al parque Los Arces, Umacollo' },
      { nombre: 'Minimarket La Estación', direccion: 'Av. Víctor Andrés Belaunde 240', referencia: 'A dos cuadras del óvalo Quiñones' },
      { nombre: 'Bodega Santa Rosa Yanahuara', direccion: 'Calle Santa Rosa 325', referencia: 'Al costado de la parroquia Santa Rosa' },
      { nombre: 'Tienda El Volcán', direccion: 'Calle El Volcán 510', referencia: 'Frente a la plazuela El Volcán' },
      { nombre: 'Minimarket Yanahuara Express', direccion: 'Av. Ejército 620', referencia: 'Cerca al grifo Primax de Av. Ejército' }
    ],
    'Yarabamba': [
      { nombre: 'Bodega Yarabamba Centro', direccion: 'Calle Principal S/N', referencia: 'Frente a la Plaza de Armas de Yarabamba' },
      { nombre: 'Tienda Tradición Yarabambina', direccion: 'Calle Libertad S/N', referencia: 'Cerca de la posta médica' },
      { nombre: 'Bodega El Valle Sagrado Yarabamba', direccion: 'Av. Arequipa S/N', referencia: 'Frente al paradero final de buses' },
      { nombre: 'Minimarket Yarabamba', direccion: 'Calle Bolognesi S/N', referencia: 'Al lado de la municipalidad' },
      { nombre: 'Tienda El Mirador de Yarabamba', direccion: 'Calle Melgar S/N', referencia: 'Cerca de la zona campestre' }
    ],
    'Yura': [
      { nombre: 'Bodega Yura Viejo', direccion: 'Calle Real S/N', referencia: 'Frente a la Plaza de Yura Viejo' },
      { nombre: 'Minimarket La Calera Yura', direccion: 'Calle Los Baños S/N', referencia: 'Cerca a los baños termales de Yura' },
      { nombre: 'Tienda Estación Yura', direccion: 'Av. Principal S/N', referencia: 'Frente a la estación de tren' },
      { nombre: 'Bodega Ciudad de Dios Yura', direccion: 'Av. Asociación Ciudad de Dios S/N', referencia: 'Cerca al mercado Ciudad de Dios' },
      { nombre: 'Minimarket Yura Express', direccion: 'Calle Bolognesi S/N', referencia: 'Frente a la planta de cemento Yura' }
    ]
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
    this.stripe = await loadStripe('pk_test_51Tiip2Lnmv4gKqcemmOezxhr7kH03Q8SYcwKjfOeM2PeH2HRd8xUqQQyAHrtCDGGFEPlummaXaoXkv6u0HuCEImF00JaBwW6Dd');
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
              this.direccionFinal = `${this.formPago.direccionDetalle}, ${this.formPago.distrito}, Arequipa`;
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
