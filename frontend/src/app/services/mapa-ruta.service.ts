import { Injectable } from '@angular/core';
import { GoogleMapsService, DireccionUbicacion } from './google-maps.service';

/** Coordenadas fijas de Botica Anita: Av. Los Jazmines 408, Independencia, Lima. */
export const BOTICA_ANITA = {
  nombre: 'Botica Anita',
  direccion: 'Av. Los Jazmines 408, Independencia, Lima',
  lng: -77.04735384172724, // longitud (pin verificado por el usuario)
  lat: -12.001353207277829, // latitud (pin verificado por el usuario)
  color: '#1a2e4a',
};

/** Radio de cobertura de delivery en km (mismo que el backend). */
export const RADIO_COBERTURA_KM = 2.0;

/** Velocidad promedio del motorizado (km/h). */
export const VELOCIDAD_KMH = 25;

/** Tiempos fijos de etapa antes del viaje (minutos). */
export const MIN_CONFIRMADO = 0;
export const MIN_DESPACHO = 0.5;   // 30 segundos
export const MIN_MOTORIZADO = 0.5; // 30 segundos
export const MIN_ENTREGA_EXTRA = 1 / 60; // 10 segundos

/**
 * Estructura de sugerencia conservada por compatibilidad con el checkout y el
 * resto de componentes. Ahora se alimenta con Google Geocoding / Places.
 */
export interface GeoapifySuggestion {
  lat: number;
  lon: number;
  label: string;
  city?: string;
  country?: string;
  address_line1?: string;
  postcode?: string;
  distance?: number;
}

export interface RutaInfo {
  coords: Array<[number, number]>; // [lat, lng]
  distanciaM: number;
  duracionSeg: number;
  tripMin: number;
  porCalles: boolean;
}

export interface EstadoEntrega {
  tipo: 'delivery' | 'recojo';
  esUrgente: boolean;
}

/** Resultado de geocodificación inversa. */
export interface GeoapifyReverse {
  lat: number;
  lon: number;
  label: string;
  address_line1?: string;
  city?: string;
}

/**
 * Servicio de mapas y rutas de la aplicación.
 *
 * La API pública se mantiene intacta (la consumen checkout, seguimiento y el
 * mapa de tracking), pero la geocodificación y el cálculo de rutas ahora usan
 * Google Maps Platform (Geocoding + Routes API) a través de `GoogleMapsService`.
 */
@Injectable({ providedIn: 'root' })
export class MapaRutaService {
  /** Cache simple de rutas por pares origen-destino. */
  private rutaCache = new Map<string, RutaInfo>();

  constructor(private readonly googleMaps: GoogleMapsService) {}

  /** Distancia en metros (Haversine). */
  distanciaM(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const r = 6371000;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLng = ((lng2 - lng1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLng / 2) ** 2;
    return 2 * r * Math.asin(Math.sqrt(a));
  }

  /** Minutos estimados de viaje tienda -> destino. */
  tripMin(distM: number): number {
    return Math.max(1, Math.round((distM / 1000 / VELOCIDAD_KMH) * 60));
  }

  /** Costo de envío dinámico según distancia y urgencia. */
  costoEnvio(distKm: number, esUrgente: boolean): number {
    if (!esUrgente) return 0; // recojo en tienda: gratis
    if (distKm > RADIO_COBERTURA_KM) return NaN; // fuera de cobertura
    return 5.0; // delivery urgente: S/ 5.00
  }

  /** Devuelve true si la dirección está dentro del radio de cobertura. */
  dentroDeCobertura(distKm: number): boolean {
    return distKm <= RADIO_COBERTURA_KM;
  }

  /**
   * Buscador de direcciones con Google Geocoding (respaldo: Places Autocomplete).
   * Devuelve sugerencias en Lima con coordenadas listas para usar.
   */
  async buscarDirecciones(consulta: string): Promise<GeoapifySuggestion[]> {
    if (!consulta || consulta.trim().length < 3) return [];

    // 1) Geocodificación directa (más precisa para direcciones completas).
    try {
      const detalle = await this.googleMaps.geocodificar(consulta.trim());
      if (detalle) return [this.aSugerencia(detalle)];
    } catch (error) {
      console.warn('Google Geocoding no disponible, se intentará Places.', error);
    }

    // 2) Respaldo: primera coincidencia de Places Autocomplete.
    try {
      const sugerencias = await this.googleMaps.buscarSugerencias(consulta.trim());
      const primera = sugerencias[0];
      if (primera?.prediction) {
        const ubicacion = await this.googleMaps.resolverSugerencia(primera.prediction);
        if (ubicacion) return [this.aSugerencia(ubicacion)];
      }
    } catch (error) {
      console.error('Google Places error:', error);
    }

    return [];
  }

  /** Geocodificación inversa: coordenadas -> dirección legible. */
  async coordenadasADireccion(lat: number, lng: number): Promise<string> {
    try {
      const detalle = await this.googleMaps.geocodificarInverso(lat, lng);
      return detalle?.direccion || `${lat}, ${lng}`;
    } catch (error) {
      console.error('Google reverse geocoding error:', error);
      return `${lat}, ${lng}`;
    }
  }

  /**
   * Ruta real por calles con Google Routes API.
   * Devuelve la geometría en [lat, lng] y duración estimada.
   */
  async obtenerRutaPorCalles(
    lngOrigen: number,
    latOrigen: number,
    lngDestino: number,
    latDestino: number
  ): Promise<RutaInfo> {
    const key = `${latOrigen.toFixed(4)},${lngOrigen.toFixed(4)}|${latDestino.toFixed(4)},${lngDestino.toFixed(4)}`;
    const hit = this.rutaCache.get(key);
    if (hit) return hit;

    try {
      const ruta = await this.googleMaps.calcularRuta(
        { lat: latOrigen, lng: lngOrigen },
        { lat: latDestino, lng: lngDestino }
      );

      if (ruta && ruta.coords.length > 1) {
        const info: RutaInfo = {
          coords: ruta.coords,
          distanciaM: ruta.distanciaM,
          duracionSeg: ruta.duracionSeg,
          tripMin: Math.max(1, Math.round(ruta.duracionSeg / 60)),
          porCalles: ruta.porCalles,
        };
        this.rutaCache.set(key, info);
        return info;
      }
      throw new Error('Sin ruta válida');
    } catch (error) {
      console.warn('Google Routes error, usando línea recta:', error);
      const distanciaM = this.distanciaM(latOrigen, lngOrigen, latDestino, lngDestino);
      const info: RutaInfo = {
        coords: [
          [latOrigen, lngOrigen],
          [latDestino, lngDestino],
        ],
        distanciaM,
        duracionSeg: Math.round((distanciaM / 1000 / VELOCIDAD_KMH) * 3600),
        tripMin: this.tripMin(distanciaM),
        porCalles: false,
      };
      this.rutaCache.set(key, info);
      return info;
    }
  }

  /** Resuelve el destino: coords del pedido > geocodificación > punto provisional (la tienda). */
  async resolverDestino(pedido: any): Promise<[number, number]> {
    if (pedido?.latitudEntrega && pedido?.longitudEntrega) {
      return [Number(pedido.latitudEntrega), Number(pedido.longitudEntrega)];
    }
    const sugerencia = await this.buscarDirecciones(
      `${pedido?.direccionEnvio || ''}, ${pedido?.distrito || ''}, Lima`
    );
    if (sugerencia.length > 0) {
      const s = sugerencia[0];
      if (
        Number.isFinite(s.lat) &&
        Number.isFinite(s.lon) &&
        s.lat >= -20 &&
        s.lat <= 0 &&
        s.lon >= -82 &&
        s.lon <= -68
      ) {
        return [s.lat, s.lon];
      }
    }
    return [BOTICA_ANITA.lat, BOTICA_ANITA.lng];
  }

  /** Verifica si una dirección está dentro del radio de cobertura (para delivery). */
  async validarCoberturaDelivery(
    direccion: string,
    distrito: string
  ): Promise<{ dentro: boolean; distKm: number; coords?: [number, number] }> {
    const sugerencias = await this.buscarDirecciones(`${direccion}, ${distrito}, Lima`);
    if (sugerencias.length === 0) {
      // Si no se encontró con el distrito, intentar solo con la dirección.
      const sugerencias2 = await this.buscarDirecciones(`${direccion}, Lima`);
      if (sugerencias2.length === 0) {
        return { dentro: false, distKm: Infinity };
      }
      sugerencias.push(...sugerencias2);
    }

    // Elegir el resultado más cercano a la tienda (como hace el backend).
    const validas = sugerencias.filter(
      (s) =>
        Number.isFinite(s.lat) &&
        Number.isFinite(s.lon) &&
        s.lat >= -20 &&
        s.lat <= 0 &&
        s.lon >= -82 &&
        s.lon <= -68
    );
    if (validas.length === 0) {
      return { dentro: false, distKm: Infinity };
    }

    let mejor = validas[0];
    let mejorD = Infinity;
    for (const sug of validas) {
      const d = this.distanciaM(BOTICA_ANITA.lat, BOTICA_ANITA.lng, sug.lat, sug.lon);
      if (d < mejorD) {
        mejorD = d;
        mejor = sug;
      }
    }

    const distKm = mejorD / 1000;
    return {
      dentro: distKm <= RADIO_COBERTURA_KM,
      distKm,
      coords: [mejor.lat, mejor.lon],
    };
  }

  private aSugerencia(detalle: DireccionUbicacion): GeoapifySuggestion {
    return {
      lat: detalle.lat,
      lon: detalle.lng,
      label: detalle.direccion,
      city: detalle.distrito || detalle.ciudad,
      country: 'Perú',
      address_line1: [detalle.calle, detalle.numero].filter(Boolean).join(' ').trim(),
      postcode: detalle.codigoPostal || undefined,
    };
  }
}
