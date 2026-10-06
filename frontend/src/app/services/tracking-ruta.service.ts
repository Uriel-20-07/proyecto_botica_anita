import { Injectable } from '@angular/core';

// Tienda Botica Anita: esquina Las Violetas con Av. Los Jazmines, Independencia.
export const TIENDA_LAT = -12.0016;
export const TIENDA_LNG = -77.0501;
// Velocidad promedio del motorizado (km/h) para estimar tiempos.
export const VELOCIDAD_KMH = 25;
// Minutos por etapa antes del viaje.
export const MIN_CONFIRMADO = 1;
export const MIN_DESPACHO = 5;
export const MIN_MOTORIZADO = 5;
// Minutos extra tras llegar para marcar entregado.
export const MIN_ENTREGA_EXTRA = 1;

@Injectable({
  providedIn: 'root'
})
export class TrackingRutaService {

  /** Distancia en metros (Haversine). */
  distanciaM(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const r = 6371000;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLng = (lng2 - lng1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) ** 2 +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
    return 2 * r * Math.asin(Math.sqrt(a));
  }

  /** Minutos estimados de viaje tienda -> destino. */
  tripMin(distM: number): number {
    return Math.max(1, Math.round((distM / 1000 / VELOCIDAD_KMH) * 60));
  }

  /** Destino: coords del pedido > geocodificación > centro de Lima. */
  async resolverDestino(pedido: any): Promise<[number, number]> {
    let lat = Number(pedido?.latitudEntrega) || 0;
    let lng = Number(pedido?.longitudEntrega) || 0;
    if (!lat || !lng) {
      const geo = await this.geocodificar(
        `${pedido?.direccionEnvio || ''}, ${pedido?.distrito || ''}, Lima, Perú`,
        pedido?.distrito || '');
      if (geo) { lat = geo[0]; lng = geo[1]; }
    }
    if (!lat || !lng) { lat = -12.046374; lng = -77.042793; }
    return [lat, lng];
  }

  /** Convierte una dirección en coordenadas (Nominatim, gratis). */
  private async geocodificar(direccion: string, distrito: string): Promise<[number, number] | null> {
    try {
      const url = 'https://nominatim.openstreetmap.org/search?format=json&limit=5&q=' + encodeURIComponent(direccion);
      const r = await fetch(url, { headers: { 'Accept': 'application/json' } });
      if (!r.ok) return null;
      const arr = await r.json();
      if (!Array.isArray(arr) || arr.length === 0) return null;
      const dist = (distrito || '').toLowerCase();
      let cands = arr;
      if (dist) {
        const enDistrito = arr.filter((x: any) => String(x.display_name || '').toLowerCase().includes(dist));
        if (enDistrito.length > 0) cands = enDistrito;
      }
      let mejor = cands[0];
      let mejorD = Number.MAX_VALUE;
      for (const c of cands) {
        const d = this.distanciaM(TIENDA_LAT, TIENDA_LNG, Number(c.lat), Number(c.lon));
        if (d < mejorD) { mejorD = d; mejor = c; }
      }
      return [Number(mejor.lat), Number(mejor.lon)];
    } catch {
      return null;
    }
  }
}
