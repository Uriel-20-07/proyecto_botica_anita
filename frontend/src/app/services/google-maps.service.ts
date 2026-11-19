import { Injectable } from '@angular/core';
import { GoogleMapsLoaderService } from './google-maps-loader.service';

/** Sugerencia devuelta por Places Autocomplete (API nueva). */
export interface DireccionSugerida {
  placeId: string;
  principal: string;
  secundaria: string;
  texto: string;
  distanciaM: number | null;
  /** Referencia interna a la predicción de Places (necesaria para pedir detalles). */
  prediction?: google.maps.places.PlacePrediction;
}

/** Dirección ya resuelta con coordenadas. */
export interface DireccionUbicacion {
  lat: number;
  lng: number;
  direccion: string;
  calle: string;
  numero: string;
  distrito: string;
  ciudad: string;
  codigoPostal: string;
}

/** Ruta calculada por Google Routes (o Directions como respaldo). */
export interface RutaCalculada {
  coords: Array<[number, number]>;
  distanciaM: number;
  duracionSeg: number;
  porCalles: boolean;
}

/** Límites aproximados de Lima Metropolitana para sesgar las búsquedas. */
const LIMA_BOUNDS: google.maps.LatLngBoundsLiteral = {
  north: -11.75,
  south: -12.35,
  east: -76.75,
  west: -77.25,
};

const LIMA_CENTER = { lat: -12.0464, lng: -77.0428 };

@Injectable({ providedIn: 'root' })
export class GoogleMapsService {
  constructor(private readonly loader: GoogleMapsLoaderService) {}

  /** Crea un token de sesión para agrupar las peticiones de autocompletado. */
  crearSessionToken(): google.maps.places.AutocompleteSessionToken | null {
    if (typeof google === 'undefined' || !google.maps?.places) return null;
    return new google.maps.places.AutocompleteSessionToken();
  }

  /**
   * Autocompletado de direcciones restringido a Perú (con sesgo a Lima).
   * Usa la Places API nueva: `AutocompleteSuggestion.fetchAutocompleteSuggestions`.
   */
  async buscarSugerencias(
    input: string,
    sessionToken?: google.maps.places.AutocompleteSessionToken | null
  ): Promise<DireccionSugerida[]> {
    const consulta = input?.trim() ?? '';
    if (consulta.length < 3) return [];

    await this.loader.cargar();
    const { AutocompleteSuggestion } = await this.loader.importLibrary('places');

    const request: google.maps.places.AutocompleteRequest = {
      input: consulta,
      includedRegionCodes: ['pe'],
      language: 'es',
      region: 'pe',
      locationBias: LIMA_BOUNDS,
    };
    if (sessionToken) request.sessionToken = sessionToken;

    const { suggestions } = await AutocompleteSuggestion.fetchAutocompleteSuggestions(request);

    return (suggestions ?? [])
      .map((s) => s.placePrediction)
      .filter((p): p is google.maps.places.PlacePrediction => !!p)
      .slice(0, 6)
      .map((p) => ({
        placeId: p.placeId,
        principal: p.mainText?.text ?? p.text.text,
        secundaria: p.secondaryText?.text ?? '',
        texto: p.text.text,
        distanciaM: p.distanceMeters ?? null,
        prediction: p,
      }));
  }

  /** Convierte una sugerencia de Places en una dirección con coordenadas. */
  async resolverSugerencia(
    prediction: google.maps.places.PlacePrediction
  ): Promise<DireccionUbicacion | null> {
    await this.loader.cargar();
    const place = prediction.toPlace();
    await place.fetchFields({
      fields: ['location', 'formattedAddress', 'addressComponents'],
    });

    const location = place.location;
    if (!location) return null;

    const componentes = this.extraerComponentesPlace(place.addressComponents ?? []);
    return {
      lat: location.lat(),
      lng: location.lng(),
      direccion: place.formattedAddress ?? componentes.direccionCorta,
      ...componentes,
    };
  }

  /** Geocodificación directa (texto -> coordenadas), restringida a Perú. */
  async geocodificar(direccion: string): Promise<DireccionUbicacion | null> {
    const consulta = direccion?.trim();
    if (!consulta) return null;

    await this.loader.cargar();
    const { Geocoder } = await this.loader.importLibrary('geocoding');
    const geocoder = new Geocoder();
    const { results } = await geocoder.geocode({
      address: consulta,
      componentRestrictions: { country: 'PE' },
      region: 'pe',
      language: 'es',
      bounds: LIMA_BOUNDS,
    });

    const mejor = results?.[0];
    return mejor ? this.desdeGeocoder(mejor) : null;
  }

  /** Geocodificación inversa (coordenadas -> dirección legible). */
  async geocodificarInverso(lat: number, lng: number): Promise<DireccionUbicacion | null> {
    await this.loader.cargar();
    const { Geocoder } = await this.loader.importLibrary('geocoding');
    const geocoder = new Geocoder();
    const { results } = await geocoder.geocode({
      location: { lat, lng },
      language: 'es',
      region: 'pe',
    });

    const mejor = results?.[0];
    return mejor ? this.desdeGeocoder(mejor) : null;
  }

  /**
   * Calcula la ruta en vehículo con Routes API. Si falla, usa DirectionsService
   * y, como último recurso, devuelve `null` para que el llamador decida.
   */
  async calcularRuta(
    origen: { lat: number; lng: number },
    destino: { lat: number; lng: number }
  ): Promise<RutaCalculada | null> {
    await this.loader.cargar();

    const porRoutes = await this.rutaConRoutes(origen, destino);
    if (porRoutes) return porRoutes;

    return this.rutaConDirections(origen, destino);
  }

  /** Formatea metros a un texto corto en km. */
  formatearDistancia(metros: number): string {
    if (!Number.isFinite(metros)) return '—';
    if (metros < 1000) return `${Math.round(metros)} m`;
    return `${(metros / 1000).toFixed(1)} km`;
  }

  /** Formatea segundos a minutos u horas. */
  formatearDuracion(segundos: number): string {
    if (!Number.isFinite(segundos)) return '—';
    const min = Math.max(1, Math.round(segundos / 60));
    if (min < 60) return `${min} min`;
    const h = Math.floor(min / 60);
    const resto = min % 60;
    return resto ? `${h} h ${resto} min` : `${h} h`;
  }

  // ── Internos ────────────────────────────────────────────────────────────

  private async rutaConRoutes(
    origen: { lat: number; lng: number },
    destino: { lat: number; lng: number }
  ): Promise<RutaCalculada | null> {
    try {
      const { Route } = await this.loader.importLibrary('routes');
      const { UnitSystem } = await this.loader.importLibrary('core');
      const { routes } = await Route.computeRoutes({
        origin: origen,
        destination: destino,
        travelMode: 'DRIVING',
        routingPreference: 'TRAFFIC_AWARE',
        polylineQuality: 'HIGH_QUALITY',
        language: 'es',
        region: 'pe',
        units: UnitSystem.METRIC,
        fields: ['path', 'distanceMeters', 'durationMillis'],
      });

      const ruta = routes?.[0];
      const path = ruta?.path;
      if (!ruta || !path || path.length < 2) return null;

      const coords: Array<[number, number]> = path.map((p) => [p.lat, p.lng]);
      const distanciaM = ruta.distanceMeters ?? 0;
      const duracionSeg = (ruta.durationMillis ?? 0) / 1000;

      if (!distanciaM || !duracionSeg) return null;
      return { coords, distanciaM, duracionSeg, porCalles: true };
    } catch (error) {
      console.warn('Google Routes no disponible, se intentará DirectionsService.', error);
      return null;
    }
  }

  private async rutaConDirections(
    origen: { lat: number; lng: number },
    destino: { lat: number; lng: number }
  ): Promise<RutaCalculada | null> {
    try {
      const { DirectionsService, TravelMode } = await this.loader.importLibrary('routes');
      const service = new DirectionsService();
      const result = await service.route({
        origin: origen,
        destination: destino,
        travelMode: TravelMode.DRIVING,
        region: 'pe',
      });

      const ruta = result.routes?.[0];
      const path = ruta?.overview_path;
      if (!ruta || !path || path.length < 2) return null;

      const legs = ruta.legs ?? [];
      const distanciaM = legs.reduce((acc, leg) => acc + (leg.distance?.value ?? 0), 0);
      const duracionSeg = legs.reduce((acc, leg) => acc + (leg.duration?.value ?? 0), 0);

      return {
        coords: path.map((p) => [p.lat(), p.lng()]),
        distanciaM,
        duracionSeg,
        porCalles: true,
      };
    } catch (error) {
      console.warn('DirectionsService no disponible.', error);
      return null;
    }
  }

  private desdeGeocoder(result: google.maps.GeocoderResult): DireccionUbicacion {
    const loc = result.geometry.location;
    const componentes = this.extraerComponentesGeocoder(result.address_components ?? []);
    return {
      lat: loc.lat(),
      lng: loc.lng(),
      direccion: result.formatted_address || componentes.direccionCorta,
      ...componentes,
    };
  }

  private extraerComponentesGeocoder(componentes: google.maps.GeocoderAddressComponent[]) {
    const buscar = (...tipos: string[]): string => {
      for (const tipo of tipos) {
        const match = componentes.find((c) => c.types.includes(tipo));
        if (match?.long_name) return match.long_name;
      }
      return '';
    };

    const calle = buscar('route', 'pedestrian', 'point_of_interest');
    const numero = buscar('street_number');
    const distrito = buscar('locality', 'sublocality_level_1', 'administrative_area_level_3');
    const ciudad = buscar('administrative_area_level_2', 'administrative_area_level_1');
    const codigoPostal = buscar('postal_code');

    return {
      calle,
      numero,
      distrito,
      ciudad,
      codigoPostal,
      direccionCorta: [calle, numero].filter(Boolean).join(' ').trim(),
    };
  }

  private extraerComponentesPlace(componentes: google.maps.places.AddressComponent[]) {
    const buscar = (...tipos: string[]): string => {
      for (const tipo of tipos) {
        const match = componentes.find((c) => c.types.includes(tipo));
        if (match?.longText) return match.longText;
      }
      return '';
    };

    const calle = buscar('route', 'pedestrian', 'point_of_interest');
    const numero = buscar('street_number');
    const distrito = buscar('locality', 'sublocality_level_1', 'administrative_area_level_3');
    const ciudad = buscar('administrative_area_level_2', 'administrative_area_level_1');
    const codigoPostal = buscar('postal_code');

    return {
      calle,
      numero,
      distrito,
      ciudad,
      codigoPostal,
      direccionCorta: [calle, numero].filter(Boolean).join(' ').trim(),
    };
  }
}

/** Centro de referencia de Lima (útil para inicializar mapas). */
export const LIMA_CENTRO = LIMA_CENTER;
