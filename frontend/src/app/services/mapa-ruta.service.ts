import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

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
export const MIN_DESPACHO = 1;
export const MIN_MOTORIZADO = 1;
export const MIN_ENTREGA_EXTRA = 1 / 60; // 10 segundos

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

@Injectable({ providedIn: 'root' })
export class MapaRutaService {
  private readonly geoapifyKey = environment.geoapifyApiKey;

  /** Cache simple de rutas por pares origen-destino. */
  private rutaCache = new Map<string, RutaInfo>();

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
   * Buscador predictivo con Geoapify Autocomplete.
   * Devuelve sugerencias de direcciones en Lima.
   */
  async buscarDirecciones(consulta: string): Promise<GeoapifySuggestion[]> {
    if (!consulta || consulta.trim().length < 3) {
      return [];
    }
    try {
      const url = new URL('https://api.geoapify.com/v1/geocode/autocomplete');
      url.searchParams.set('text', consulta.trim());
      url.searchParams.set('format', 'json');
      url.searchParams.set('apiKey', this.geoapifyKey);
      url.searchParams.set('limit', '6');
      url.searchParams.set('filter', 'countrycode:pe'); // solo Perú

      const resp = await fetch(url.toString());
      if (!resp.ok) return [];
      const data = await resp.json();

      const results = data.results || [];
      return results.map((item: any) => ({
        lat: item.lat,
        lon: item.lon,
        label: item.formatted || item.label || '',
        city: item.city,
        country: item.country,
        address_line1: item.address_line1,
        postcode: item.postcode,
        distance: item.distance,
      }));
    } catch (err) {
      console.error('Geoapify autocomplete error:', err);
      return [];
    }
  }

  /**
   * Geocodificación inversa: coordenadas -> dirección legible.
   */
  async coordenadasADireccion(lat: number, lng: number): Promise<string> {
    try {
      const url = new URL('https://api.geoapify.com/v1/geocode/reverse');
      url.searchParams.set('lat', String(lat));
      url.searchParams.set('lon', String(lng));
      url.searchParams.set('format', 'json');
      url.searchParams.set('apiKey', this.geoapifyKey);
      url.searchParams.set('lang', 'es');

      const resp = await fetch(url.toString());
      if (!resp.ok) return `${lat}, ${lng}`;
      const data = await resp.json();
      return data.formatted || data.address_line1 || `${lat}, ${lng}`;
    } catch (err) {
      console.error('Geoapify reverse error:', err);
      return `${lat}, ${lng}`;
    }
  }

  /**
   * Ruta real por calles con Mapbox Directions API.
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
      const url = `https://router.project-osrm.org/route/v1/driving/${lngOrigen},${latOrigen};${lngDestino},${latDestino}?overview=full&geometries=geojson`;
      const resp = await fetch(url);
      if (!resp.ok) throw new Error(`OSRM ${resp.status}`);
      const data = await resp.json();
      const route = data.routes?.[0];
      const pts = route?.geometry?.coordinates;
      if (Array.isArray(pts) && pts.length > 1 && route.duration > 0) {
        const coords: Array<[number, number]> = pts.map(
          (c: [number, number]) => [c[1], c[0]] as [number, number]
        );
        const distanciaM = route.distance || this.distanciaM(latOrigen, lngOrigen, latDestino, lngDestino);
        const duracionSeg = route.duration || Math.round((distanciaM / 1000) / VELOCIDAD_KMH * 3600);

        const info: RutaInfo = {
          coords,
          distanciaM,
          duracionSeg,
          tripMin: Math.max(1, Math.round(duracionSeg / 60)),
          porCalles: true,
        };
        this.rutaCache.set(key, info);
        return info;
      }
      throw new Error('Sin ruta válida');
    } catch (err) {
      console.error('OSRM directions error:', err);
      const distanciaM = this.distanciaM(latOrigen, lngOrigen, latDestino, lngDestino);
      const info: RutaInfo = {
        coords: [[latOrigen, lngOrigen], [latDestino, lngDestino]],
        distanciaM,
        duracionSeg: Math.round((distanciaM / 1000) / VELOCIDAD_KMH * 3600),
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
      if (Number.isFinite(s.lat) && Number.isFinite(s.lon) &&
          s.lat >= -20 && s.lat <= 0 && s.lon >= -82 && s.lon <= -68) {
        return [s.lat, s.lon];
      }
    }
    return [BOTICA_ANITA.lat, BOTICA_ANITA.lng];
  }

  /** Verifica si una dirección está dentro del radio de cobertura (para delivery). */
  async validarCoberturaDelivery(direccion: string, distrito: string): Promise<{ dentro: boolean; distKm: number; coords?: [number, number] }> {
    const sugerencias = await this.buscarDirecciones(`${direccion}, ${distrito}, Lima`);
    if (sugerencias.length === 0) {
      // Si no se encontró en Geoapify, intentar solo con la dirección
      const sugerencias2 = await this.buscarDirecciones(`${direccion}, Lima`);
      if (sugerencias2.length === 0) {
        return { dentro: false, distKm: Infinity };
      }
      sugerencias.push(...sugerencias2);
    }

    // Elegir el resultado más cercano a la tienda (como hace el backend con Nominatim)
    const validas = sugerencias.filter((s) =>
      Number.isFinite(s.lat) && Number.isFinite(s.lon) &&
      s.lat >= -20 && s.lat <= 0 && s.lon >= -82 && s.lon <= -68
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

    const distM = mejorD;
    const distKm = distM / 1000;
    return {
      dentro: distKm <= RADIO_COBERTURA_KM,
      distKm,
      coords: [mejor.lat, mejor.lon],
    };
  }
}
