import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { environment } from '../../environments/environment';

declare global {
  interface Window {
    __googleMapsReady?: () => void;
  }
}

/**
 * Carga el SDK de Google Maps JavaScript API de forma asíncrona y una sola vez.
 *
 * La promesa no se resuelve hasta comprobar que `google.maps.importLibrary`
 * existe realmente. Así se evita el clásico `ReferenceError: google is not
 * defined` cuando el componente intenta usar el SDK antes de que el script
 * termine de inicializarse.
 *
 * Es seguro importarlo en SSR: la carga real solo ocurre en el navegador.
 */
@Injectable({ providedIn: 'root' })
export class GoogleMapsLoaderService {
  private static readonly SCRIPT_ID = 'google-maps-sdk';
  private static readonly CALLBACK = '__googleMapsReady';
  private static readonly TIMEOUT_MS = 15000;

  private readonly platformId = inject(PLATFORM_ID);
  private readonly apiKey = environment.googleMapsApiKey;

  private cargaPromise: Promise<void> | null = null;

  /** true si estamos en el navegador y hay una API key configurada. */
  get disponible(): boolean {
    return isPlatformBrowser(this.platformId) && !!this.apiKey;
  }

  /**
   * Carga (o reutiliza) el SDK. Resuelve únicamente cuando
   * `window.google.maps.importLibrary` está disponible.
   */
  async cargar(): Promise<void> {
    if (!isPlatformBrowser(this.platformId)) {
      throw new Error('Google Maps solo está disponible en el navegador.');
    }
    if (!this.apiKey) {
      throw new Error('Falta configurar googleMapsApiKey en environment.ts');
    }

    if (this.sdkListo()) return;

    if (!this.cargaPromise) {
      this.cargaPromise = this.injectScript().catch((error: unknown) => {
        // Permite reintentar en una próxima llamada si falló la red.
        this.cargaPromise = null;
        throw error;
      });
    }

    return this.cargaPromise;
  }

  /**
   * Atajo tipado para obtener una librería después de asegurar la carga.
   * Evita acceder a `google` desde los componentes antes de tiempo.
   */
  async importLibrary<T extends keyof google.maps.ImportLibraryMap>(
    name: T
  ): Promise<google.maps.ImportLibraryMap[T]> {
    await this.cargar();
    return google.maps.importLibrary(name) as Promise<google.maps.ImportLibraryMap[T]>;
  }

  private sdkListo(): boolean {
    const g = (window as Window & { google?: typeof google }).google;
    return !!g && typeof g.maps?.importLibrary === 'function';
  }

  private injectScript(): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      let finalizado = false;

      const finalizar = (): void => {
        if (finalizado) return;
        finalizado = true;
        this.esperarSdk().then(resolve).catch(reject);
      };

      const existente = document.getElementById(
        GoogleMapsLoaderService.SCRIPT_ID
      ) as HTMLScriptElement | null;

      if (existente) {
        if (this.sdkListo()) {
          resolve();
          return;
        }
        existente.addEventListener('load', finalizar, { once: true });
        existente.addEventListener(
          'error',
          () => {
            finalizado = true;
            reject(new Error('No se pudo cargar Google Maps.'));
          },
          { once: true }
        );
        // Red de seguridad: el script pudo terminar antes de registrar el listener.
        finalizar();
        return;
      }

      const params = new URLSearchParams({
        key: this.apiKey,
        v: 'weekly',
        language: 'es',
        region: 'PE',
        // Carga dinámica: cada librería se pide con `importLibrary(...)`.
        loading: 'async',
        callback: GoogleMapsLoaderService.CALLBACK,
      });

      window[GoogleMapsLoaderService.CALLBACK] = finalizar;

      const script = document.createElement('script');
      script.id = GoogleMapsLoaderService.SCRIPT_ID;
      script.async = true;
      script.defer = true;
      script.src = `https://maps.googleapis.com/maps/api/js?${params.toString()}`;
      script.onload = finalizar;
      script.onerror = () => {
        finalizado = true;
        script.remove();
        reject(new Error('No se pudo cargar Google Maps.'));
      };
      document.head.appendChild(script);
    });
  }

  /**
   * Espera activamente a que el namespace global esté listo. El callback del
   * script puede ejecutarse un instante antes de que `importLibrary` exista.
   */
  private esperarSdk(timeoutMs = GoogleMapsLoaderService.TIMEOUT_MS): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const inicio = Date.now();

      const comprobar = (): void => {
        if (this.sdkListo()) {
          resolve();
          return;
        }
        if (Date.now() - inicio >= timeoutMs) {
          reject(new Error('Google Maps no terminó de cargar a tiempo.'));
          return;
        }
        setTimeout(comprobar, 50);
      };

      comprobar();
    });
  }
}
