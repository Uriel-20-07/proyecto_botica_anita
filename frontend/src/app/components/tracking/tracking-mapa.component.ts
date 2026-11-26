import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  Input,
  ElementRef,
  ViewChild,
  NgZone,
  signal,
  computed,
} from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { TrackingService, TrackingResponseDTO } from '../../services/tracking.service';
import { PedidoService } from '../../services/pedido.service';
import {
  MapaRutaService,
  BOTICA_ANITA,
  MIN_CONFIRMADO,
  MIN_DESPACHO,
  MIN_MOTORIZADO,
} from '../../services/mapa-ruta.service';
import { msDesdeFecha } from '../../services/fecha-util';
import { GoogleMapsLoaderService } from '../../services/google-maps-loader.service';

/** Pin vectorial de Botica Anita: azul marino con cruz de farmacia naranja. */
const SVG_PIN_BOTICA = `
<svg xmlns="http://www.w3.org/2000/svg" width="42" height="53" viewBox="0 0 42 53" fill="none">
  <path d="M21 1.5C10.2 1.5 1.5 10.1 1.5 20.7c0 12.4 14.5 25.6 18.1 30.4.8 1 2 1 2.8 0 3.6-4.8 18.1-18 18.1-30.4C40.5 10.1 31.8 1.5 21 1.5Z"
    fill="#0F294A" stroke="#ffffff" stroke-width="2.5"/>
  <path d="M18.4 11.5h5.2v5.3h5.3v5.2h-5.3v5.3h-5.2v-5.3h-5.3v-5.2h5.3v-5.3Z" fill="#F97316"/>
</svg>`;

/** Pin vectorial del domicilio: naranja de la marca con casa blanca. */
const SVG_PIN_CASA = `
<svg xmlns="http://www.w3.org/2000/svg" width="42" height="53" viewBox="0 0 42 53" fill="none">
  <path d="M21 1.5C10.2 1.5 1.5 10.1 1.5 20.7c0 12.4 14.5 25.6 18.1 30.4.8 1 2 1 2.8 0 3.6-4.8 18.1-18 18.1-30.4C40.5 10.1 31.8 1.5 21 1.5Z"
    fill="#F97316" stroke="#ffffff" stroke-width="2.5"/>
  <path d="M21 12.5 11.8 20v11.2h18.4V20L21 12.5Z" fill="#ffffff"/>
  <rect x="19" y="24" width="4" height="7.2" rx="1.1" fill="#F97316"/>
</svg>`;

/** Vista superior estilizada del motorizado (rota según el rumbo del viaje). */
const SVG_MOTO = `
<svg xmlns="http://www.w3.org/2000/svg" width="42" height="42" viewBox="0 0 42 42" fill="none">
  <rect x="16.5" y="3" width="9" height="11" rx="4.5" fill="#0F294A" stroke="#ffffff" stroke-width="1.6"/>
  <rect x="16.5" y="28" width="9" height="11" rx="4.5" fill="#0F294A" stroke="#ffffff" stroke-width="1.6"/>
  <rect x="13.5" y="12" width="15" height="18" rx="7.5" fill="#F97316" stroke="#ffffff" stroke-width="1.6"/>
  <rect x="7" y="10.2" width="28" height="3" rx="1.5" fill="#0F294A" stroke="#ffffff" stroke-width="1"/>
  <circle cx="21" cy="21" r="4.6" fill="#0F294A" stroke="#ffffff" stroke-width="1.2"/>
  <rect x="18.6" y="1.6" width="4.8" height="2.6" rx="1.3" fill="#FDE68A"/>
</svg>`;

@Component({
  selector: 'app-tracking-mapa',
  standalone: true,
  template: `
    <div class="tracking-card">
      <!-- Cabecera -->
      <div class="tracking-header">
        <div class="tracking-title">
          <span class="tracking-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
              <circle cx="12" cy="10" r="3"></circle>
            </svg>
          </span>
          <div class="tracking-title-text">
            <span class="tracking-eyebrow">Seguimiento en vivo</span>
            <h2>Pedido #{{ idPedido }}</h2>
            @if (pedido()) {
              <p class="tracking-sub">{{ direccionCompleta() }}</p>
            }
          </div>
        </div>
        <span class="estado-badge"
          [class.entregado]="estadoFinal() && !estadoNoEntregado()"
          [class.suspendido]="esSuspendido()"
          [class.cancelado]="esCancelado()"
          [class.en-vivo]="!estadoFinal()">
          @if (!estadoFinal()) {
            <span class="pulse" aria-hidden="true"></span>
          }
          {{ estadoTexto() }}
        </span>
      </div>

      @if (pedido()) {
        <div class="status-strip">
          <div class="status-main">
            <span class="status-beacon" [class.done]="estadoFinal()" aria-hidden="true"></span>
            <div class="status-copy">
              <span class="status-kicker">Estado del pedido</span>
              <strong class="status-title">{{ estadoTexto() }}</strong>
            </div>
          </div>

          <div class="status-address">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
              <circle cx="12" cy="10" r="3"></circle>
            </svg>
            <span>{{ direccionCompleta() }}</span>
          </div>

          @if (mostrandoSimulacion()) {
            <span class="status-sim">
              <span class="sim-dot" aria-hidden="true"></span>
              Simulación en vivo
            </span>
          }
        </div>
      }

      <!-- Marco premium del mapa -->
      <div class="mapa-frame" [class.hidden]="mapaOculto()">
        <div class="mapa-wrapper">
          <div #mapContainer class="map-container"></div>

          @if (cargandoMapa() && !mapaOculto()) {
            <div class="mapa-overlay">
              <span class="spinner" aria-hidden="true"></span>
              <span>{{ pedido() ? 'Cargando mapa…' : 'Cargando seguimiento…' }}</span>
            </div>
          }

          @if (errorMapa() && !mapaOculto()) {
            <div class="mapa-overlay mapa-error">
              <span>⚠️ {{ errorMapa() }}</span>
            </div>
          }

          <!-- Botón re-centrar -->
          <button class="btn-recentrar" (click)="recentrar()" title="Re-centrar mapa" aria-label="Re-centrar mapa">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="7"></circle>
              <circle cx="12" cy="12" r="2.5"></circle>
              <path d="M12 2v3M12 19v3M2 12h3M19 12h3"></path>
            </svg>
          </button>

          <!-- Tarjeta flotante compacta -->
          <div class="floating-card" [class.hidden]="!mostrarFloatingCard()">
            <div class="fc-eta">
              @if (etaMin() > 0) {
                <span class="fc-eta-value">{{ etaMin() }}</span>
                <span class="fc-eta-unit">min</span>
              } @else if (estadoFinal()) {
                <span class="fc-eta-value">✓</span>
              } @else {
                <span class="fc-eta-value">—</span>
              }
            </div>

            <span class="fc-sep" aria-hidden="true"></span>

            <div class="fc-data">
              @if (etaMin() > 0) {
                <span class="fc-hora">≈ {{ etaHora() }}</span>
              }
              <span class="fc-meta">
                <span class="fc-dist">{{ distanciaKm().toFixed(1) }} km</span>
                <span class="fc-dot" aria-hidden="true"></span>
                <span class="fc-cost">{{ costoEnvioTexto() }}</span>
              </span>
            </div>
          </div>

          <!-- Leyenda unificada con los mismos SVG de los marcadores -->
          <div class="leyenda">
            <span class="leyenda-item" title="Botica Anita">
              <span class="leyenda-icon" aria-hidden="true">
                <svg viewBox="0 0 42 53" width="14" height="17.7" fill="none">
                  <path d="M21 1.5C10.2 1.5 1.5 10.1 1.5 20.7c0 12.4 14.5 25.6 18.1 30.4.8 1 2 1 2.8 0 3.6-4.8 18.1-18 18.1-30.4C40.5 10.1 31.8 1.5 21 1.5Z"
                    fill="#0F294A" stroke="#ffffff" stroke-width="2.5"/>
                  <path d="M18.4 11.5h5.2v5.3h5.3v5.2h-5.3v5.3h-5.2v-5.3h-5.3v-5.2h5.3v-5.3Z" fill="#F97316"/>
                </svg>
              </span>
              <span class="leyenda-label">Botica Anita</span>
            </span>

            <span class="leyenda-sep" aria-hidden="true"></span>

            <span class="leyenda-item" title="Repartidor">
              <span class="leyenda-icon" aria-hidden="true">
                <svg viewBox="0 0 42 42" width="18" height="18" fill="none">
                  <rect x="16.5" y="3" width="9" height="11" rx="4.5" fill="#0F294A" stroke="#ffffff" stroke-width="1.6"/>
                  <rect x="16.5" y="28" width="9" height="11" rx="4.5" fill="#0F294A" stroke="#ffffff" stroke-width="1.6"/>
                  <rect x="13.5" y="12" width="15" height="18" rx="7.5" fill="#F97316" stroke="#ffffff" stroke-width="1.6"/>
                  <rect x="7" y="10.2" width="28" height="3" rx="1.5" fill="#0F294A" stroke="#ffffff" stroke-width="1"/>
                  <circle cx="21" cy="21" r="4.6" fill="#0F294A" stroke="#ffffff" stroke-width="1.2"/>
                  <rect x="18.6" y="1.6" width="4.8" height="2.6" rx="1.3" fill="#FDE68A"/>
                </svg>
              </span>
              <span class="leyenda-label">Repartidor</span>
            </span>

            <span class="leyenda-sep" aria-hidden="true"></span>

            <span class="leyenda-item" title="Tu domicilio">
              <span class="leyenda-icon" aria-hidden="true">
                <svg viewBox="0 0 42 53" width="14" height="17.7" fill="none">
                  <path d="M21 1.5C10.2 1.5 1.5 10.1 1.5 20.7c0 12.4 14.5 25.6 18.1 30.4.8 1 2 1 2.8 0 3.6-4.8 18.1-18 18.1-30.4C40.5 10.1 31.8 1.5 21 1.5Z"
                    fill="#F97316" stroke="#ffffff" stroke-width="2.5"/>
                  <path d="M21 12.5 11.8 20v11.2h18.4V20L21 12.5Z" fill="#ffffff"/>
                  <rect x="19" y="24" width="4" height="7.2" rx="1.1" fill="#F97316"/>
                </svg>
              </span>
              <span class="leyenda-label">Tu domicilio</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        --ba-navy: #1a2e4a;
        --ba-navy-deep: #0f2347;
        --ba-orange: #e85d04;
        --ba-orange-soft: #f97316;
        --ba-slate: #64748b;
        --ba-border: #e2e8f0;
        font-family: 'Outfit', 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      }

      .tracking-card {
        position: relative;
        border: 1px solid var(--ba-border);
        border-radius: 22px;
        background: #ffffff;
        box-shadow: 0 24px 60px -30px rgba(26, 46, 74, 0.4), 0 2px 6px rgba(26, 46, 74, 0.04);
        overflow: hidden;
      }

      .tracking-card::before {
        content: '';
        position: absolute;
        inset: 0 0 auto;
        height: 3px;
        background: linear-gradient(90deg, var(--ba-navy), var(--ba-orange));
      }

      .tracking-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 14px;
        padding: 22px 22px 16px;
        background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
      }

      .tracking-title {
        display: flex;
        align-items: center;
        gap: 13px;
        min-width: 0;
      }

      .tracking-icon {
        flex-shrink: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 46px;
        height: 46px;
        border-radius: 14px;
        color: var(--ba-orange);
        background: linear-gradient(135deg, #fff4ec 0%, #ffe4d1 100%);
        border: 1px solid #ffe0cb;
        box-shadow: 0 6px 14px -6px rgba(232, 93, 4, 0.5);
      }

      .tracking-icon svg { width: 21px; height: 21px; }

      .tracking-title-text { min-width: 0; }

      .tracking-eyebrow {
        display: block;
        font-size: 9.5px;
        font-weight: 800;
        letter-spacing: 0.1em;
        text-transform: uppercase;
        color: #94a3b8;
        margin-bottom: 1px;
      }

      .tracking-header h2 {
        margin: 0;
        font-size: 1.18rem;
        color: var(--ba-navy);
        font-weight: 800;
        letter-spacing: -0.015em;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .tracking-sub {
        margin: 2px 0 0;
        font-size: 0.77rem;
        color: var(--ba-slate);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .estado-badge {
        flex-shrink: 0;
        display: inline-flex;
        align-items: center;
        gap: 7px;
        font-size: 0.73rem;
        font-weight: 800;
        letter-spacing: 0.01em;
        padding: 7px 13px;
        border-radius: 999px;
        background: #f1f5f9;
        color: #475569;
        border: 1px solid #e2e8f0;
        white-space: nowrap;
      }

      .estado-badge.en-vivo {
        background: linear-gradient(135deg, #fff4ec, #ffe9d9);
        color: #c2410c;
        border-color: #ffddc6;
        box-shadow: 0 4px 12px -6px rgba(232, 93, 4, 0.55);
      }

      .estado-badge.entregado {
        background: linear-gradient(135deg, #f0fdf4, #dcfce7);
        color: #166534;
        border-color: #bbf7d0;
      }

      .estado-badge.suspendido {
        background: linear-gradient(135deg, #fff7ed, #ffedd5);
        color: #9a3412;
        border-color: #fed7aa;
      }

      .estado-badge.cancelado {
        background: linear-gradient(135deg, #fef2f2, #fee2e2);
        color: #991b1b;
        border-color: #fecaca;
      }

      .pulse {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: var(--ba-orange);
        box-shadow: 0 0 0 0 rgba(232, 93, 4, 0.55);
        animation: latido 1.8s ease-out infinite;
      }

      @keyframes latido {
        0% { box-shadow: 0 0 0 0 rgba(232, 93, 4, 0.5); }
        70% { box-shadow: 0 0 0 7px rgba(232, 93, 4, 0); }
        100% { box-shadow: 0 0 0 0 rgba(232, 93, 4, 0); }
      }

      /* ── Franja de estado ─────────────────────────────────────────── */
      .status-strip {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 14px;
        margin: 0 22px 6px;
        padding: 14px 16px;
        border-radius: 16px;
        border: 1px solid #eef2f7;
        background: linear-gradient(135deg, #f8fafc 0%, #ffffff 70%);
      }

      .status-main {
        display: flex;
        align-items: center;
        gap: 11px;
        min-width: 0;
      }

      .status-beacon {
        flex-shrink: 0;
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: var(--ba-orange);
        box-shadow: 0 0 0 0 rgba(232, 93, 4, 0.45);
        animation: latido 1.9s ease-out infinite;
      }

      .status-beacon.done {
        background: #16a34a;
        animation: none;
        box-shadow: 0 0 0 4px rgba(22, 163, 74, 0.14);
      }

      .status-copy {
        display: flex;
        flex-direction: column;
        gap: 1px;
        min-width: 0;
      }

      .status-kicker {
        font-size: 9.5px;
        font-weight: 800;
        letter-spacing: 0.09em;
        text-transform: uppercase;
        color: #94a3b8;
      }

      .status-title {
        font-size: 0.92rem;
        font-weight: 800;
        color: var(--ba-navy);
        letter-spacing: -0.01em;
      }

      .status-address {
        display: flex;
        align-items: center;
        gap: 7px;
        flex: 1;
        min-width: 0;
        padding-left: 14px;
        border-left: 1px solid var(--ba-border);
        font-size: 0.79rem;
        color: #475569;
      }

      .status-address svg {
        width: 15px;
        height: 15px;
        flex-shrink: 0;
        color: #94a3b8;
      }

      .status-address span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .status-sim {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 5px 11px;
        border-radius: 999px;
        background: #fff4ec;
        border: 1px solid #ffe0cb;
        color: #c2410c;
        font-size: 0.66rem;
        font-weight: 800;
        letter-spacing: 0.02em;
        white-space: nowrap;
      }

      .sim-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--ba-orange);
        animation: latido 1.8s ease-out infinite;
      }

      /* ── Marco del mapa ───────────────────────────────────────────── */
      .mapa-frame {
        margin: 16px 18px 18px;
        padding: 7px;
        border-radius: 24px;
        background: linear-gradient(135deg, rgba(26, 46, 74, 0.22), rgba(232, 93, 4, 0.3));
        box-shadow: 0 22px 46px -24px rgba(26, 46, 74, 0.55);
        transition: opacity 0.3s ease;
      }

      .mapa-frame.hidden {
        display: none;
      }

      .mapa-wrapper {
        position: relative;
        height: 460px;
        width: 100%;
        border-radius: 18px;
        overflow: hidden;
        background: #eef2f7;
      }

      .map-container {
        height: 100%;
        width: 100%;
      }

      .mapa-overlay {
        position: absolute;
        inset: 0;
        z-index: 40;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 10px;
        border-radius: 18px;
        background: rgba(248, 250, 252, 0.9);
        backdrop-filter: blur(2px);
        color: var(--ba-slate);
        font-size: 0.82rem;
        font-weight: 700;
      }

      .mapa-overlay.mapa-error {
        color: #b91c1c;
        background: rgba(254, 242, 242, 0.95);
        padding: 20px;
        text-align: center;
      }

      .spinner {
        width: 26px;
        height: 26px;
        border-radius: 50%;
        border: 3px solid rgba(232, 93, 4, 0.2);
        border-top-color: var(--ba-orange);
        animation: girar 0.8s linear infinite;
      }

      @keyframes girar {
        to { transform: rotate(360deg); }
      }

      /* ── Tarjeta flotante compacta ───────────────────────────────── */
      .floating-card {
        position: absolute;
        top: 14px;
        right: 14px;
        z-index: 50;
        display: flex;
        align-items: center;
        gap: 11px;
        padding: 10px 14px;
        border-radius: 14px;
        background: rgba(255, 255, 255, 0.97);
        backdrop-filter: blur(8px);
        border: 1px solid rgba(255, 255, 255, 0.7);
        box-shadow: 0 14px 32px -12px rgba(15, 23, 42, 0.42);
        transition: transform 0.3s ease, opacity 0.3s ease;
        white-space: nowrap;
      }

      .floating-card.hidden {
        transform: translateY(-12px);
        opacity: 0;
        pointer-events: none;
      }

      .fc-eta {
        display: flex;
        align-items: baseline;
        gap: 3px;
        line-height: 1;
      }

      .fc-eta-value {
        font-size: 1.55rem;
        font-weight: 800;
        color: var(--ba-orange);
        letter-spacing: -0.02em;
      }

      .fc-eta-unit {
        font-size: 0.68rem;
        font-weight: 800;
        color: var(--ba-orange);
      }

      .fc-sep {
        width: 1px;
        height: 28px;
        background: #e8edf3;
      }

      .fc-data {
        display: flex;
        flex-direction: column;
        gap: 2px;
        line-height: 1.15;
      }

      .fc-hora {
        font-size: 0.66rem;
        font-weight: 700;
        color: var(--ba-slate);
      }

      .fc-meta {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 0.72rem;
        font-weight: 800;
        color: var(--ba-navy);
      }

      .fc-dot {
        width: 3px;
        height: 3px;
        border-radius: 50%;
        background: #cbd5e1;
      }

      .fc-dist { color: var(--ba-navy); }
      .fc-cost { color: var(--ba-orange); }

      /* ── Leyenda unificada ───────────────────────────────────────── */
      .leyenda {
        position: absolute;
        left: 14px;
        top: 14px;
        z-index: 45;
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 7px 13px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.82);
        backdrop-filter: blur(6px);
        border: 1px solid rgba(255, 255, 255, 0.7);
        box-shadow: 0 8px 20px rgba(15, 23, 42, 0.16);
      }

      .leyenda-item {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 0.65rem;
        font-weight: 700;
        color: #334155;
        white-space: nowrap;
      }

      .leyenda-sep {
        width: 1px;
        height: 14px;
        background: rgba(148, 163, 184, 0.35);
      }

      .leyenda-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        filter: drop-shadow(0 1px 1px rgba(15, 23, 42, 0.25));
      }

      .leyenda-icon svg { display: block; }

      .btn-recentrar {
        position: absolute;
        bottom: 42px;
        right: 14px;
        z-index: 60;
        width: 42px;
        height: 42px;
        border-radius: 50%;
        border: 1px solid rgba(26, 46, 74, 0.12);
        background: #ffffff;
        box-shadow: 0 8px 20px rgba(26, 46, 74, 0.22);
        cursor: pointer;
        color: var(--ba-navy);
        display: flex;
        align-items: center;
        justify-content: center;
        transition: transform 0.15s ease, box-shadow 0.15s ease;
      }

      .btn-recentrar svg { width: 18px; height: 18px; }

      .btn-recentrar:hover {
        transform: scale(1.08);
        box-shadow: 0 10px 24px rgba(26, 46, 74, 0.28);
      }

      @media (max-width: 720px) {
        .tracking-header { padding: 18px 16px 14px; }
        .status-strip { margin: 0 16px 6px; padding: 12px 13px; gap: 10px; }
        .status-address {
          padding-left: 0;
          border-left: 0;
          width: 100%;
        }
        .mapa-frame {
          margin: 14px 12px 14px;
          padding: 6px;
          border-radius: 20px;
        }
        .mapa-wrapper { height: 380px; border-radius: 15px; }
        .floating-card {
          left: 12px;
          right: auto;
          top: 12px;
          max-width: calc(100% - 24px);
          padding: 9px 12px;
          gap: 9px;
        }
        .leyenda {
          left: 10px;
          top: auto;
          bottom: 34px;
          gap: 7px;
          padding: 5px 9px;
        }
        .leyenda-item { font-size: 0.56rem; gap: 4px; }
        .leyenda-sep { height: 12px; }
        .btn-recentrar { bottom: 42px; right: 12px; }
      }

      @media (max-width: 480px) {
        .tracking-header {
          flex-wrap: wrap;
          gap: 10px;
          padding: 16px 14px 12px;
        }
        .tracking-title { flex: 1 1 100%; }
        .tracking-icon { width: 40px; height: 40px; border-radius: 12px; }
        .tracking-icon svg { width: 18px; height: 18px; }
        .tracking-header h2 { font-size: 1.05rem; }
        .tracking-sub { font-size: 0.72rem; }
        .estado-badge {
          font-size: 0.68rem;
          padding: 6px 11px;
        }

        /* La franja de estado apila sus bloques y separa el pin del badge */
        .status-strip {
          margin: 0 12px 6px;
          padding: 12px;
          gap: 8px;
          row-gap: 8px;
        }
        .status-main { flex: 1 1 100%; }
        .status-address {
          flex: 1 1 100%;
          width: 100%;
          padding-left: 0;
          border-left: 0;
        }
        .status-sim {
          margin-left: 0;
          align-self: flex-start;
        }

        .mapa-frame { margin: 12px 10px 12px; padding: 5px; border-radius: 18px; }
        .mapa-wrapper { height: 300px; border-radius: 14px; }
        .floating-card {
          left: 10px;
          top: 10px;
          max-width: calc(100% - 20px);
          padding: 8px 10px;
          gap: 8px;
        }
        .fc-eta-value { font-size: 1.3rem; }
        .fc-meta { font-size: 0.66rem; }
        .leyenda {
          left: 8px;
          bottom: 30px;
          gap: 6px;
          padding: 5px 8px;
        }
        .leyenda-item { font-size: 0.53rem; }
        .btn-recentrar {
          width: 38px;
          height: 38px;
          bottom: 38px;
          right: 10px;
        }
      }

      @media (max-width: 400px) {
        .leyenda-label { display: none; }
        .leyenda-item { gap: 0; }
        .leyenda { gap: 8px; }
        .mapa-wrapper { height: 270px; }
        .tracking-header h2 { font-size: 1rem; }
      }
    `,
  ],
})
export class TrackingMapaComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() idPedido!: number;
  @Input() fechaPedido?: string;

  /** Tipo de entrega: 'delivery' o 'recojo'. Se inyecta desde el padre. */
  @Input() tipoEntrega: 'delivery' | 'recojo' = 'delivery';

  /** Estado actual calculado por tiempo (0-4), pasado desde el padre para no adelantarse. */
  @Input() estadoActualMapa?: number;

  /** Señales reactivas para el estado del pedido. */
  pedido = signal<TrackingResponseDTO | null>(null);
  etaMin = signal(0);
  distanciaKm = signal(0);
  costoEnvio = signal(0);
  mostrandoSimulacion = signal(false);
  cargandoMapa = signal(true);
  errorMapa = signal('');

  private map?: google.maps.Map;
  private creandoMapa = false;
  private destruido = false;
  private destLat = BOTICA_ANITA.lat;
  private destLng = BOTICA_ANITA.lng;
  usandoFallback = false;
  private bearingActual = 0;
  /** Coordenadas corregidas manualmente por el usuario (persisten entre polls). */
  private coordsOverride: { lat: number; lng: number } | null = null;
  private ultimasCoordsDestino: { lat: number; lng: number } | null = null;

  /** Constructores obtenidos con `importLibrary` (nunca se usa `google` global). */
  private CtorAdvancedMarker?: typeof google.maps.marker.AdvancedMarkerElement;
  private CtorPolyline?: typeof google.maps.Polyline;
  private CtorLatLngBounds?: typeof google.maps.LatLngBounds;
  private CtorInfoWindow?: typeof google.maps.InfoWindow;

  private markerTienda?: google.maps.marker.AdvancedMarkerElement;
  private markerDestino?: google.maps.marker.AdvancedMarkerElement;
  private markerRepartidor?: google.maps.marker.AdvancedMarkerElement;
  private repartidorEl?: SVGElement;
  private infoWindow?: google.maps.InfoWindow;

  private lineaRuta?: google.maps.Polyline;
  private lineaCasing?: google.maps.Polyline;
  private lineaRecorrida?: google.maps.Polyline;

  private rutaSimulada: Array<[number, number]> = [];
  private pasoSim = 0;
  private animacionMoto?: number;
  private simulacionTimer?: ReturnType<typeof setInterval>;

  // Guardamos la suscripción RxJS para cancelarla al destruir el componente
  private pollingSub?: Subscription;

  @ViewChild('mapContainer') mapContainer?: ElementRef<HTMLDivElement>;

  constructor(
    private trackingService: TrackingService,
    private mapaService: MapaRutaService,
    private pedidoService: PedidoService,
    private mapsLoader: GoogleMapsLoaderService,
    private zone: NgZone
  ) {}

  /** Señales derivadas para el template. */
  estadoFinal = computed(() => {
    const est = (this.pedido()?.estado || '').toUpperCase();
    return (
      est.includes('COMPLETAD') ||
      est.includes('ENTREGAD') ||
      est.includes('CANCELAD') ||
      est.includes('SUSPENDID') ||
      est.includes('RECHAZAD')
    );
  });

  esSuspendido = computed(() => (this.pedido()?.estado || '').toUpperCase().includes('SUSPENDID'));

  esCancelado = computed(() => (this.pedido()?.estado || '').toUpperCase().includes('CANCELAD'));

  estadoNoEntregado = computed(() => this.esSuspendido() || this.esCancelado());

  mapaOculto = computed(() => {
    // El mapa solo se oculta cuando el pedido ya terminó (entregado, suspendido o cancelado)
    const ped = this.pedido();
    if (!ped) return false;
    const est = (ped.estado || '').toUpperCase();
    if (this.tipoEntrega === 'recojo') return true;
    const oculto = est.includes('COMPLETAD') || est.includes('ENTREGAD')
      || est.includes('SUSPENDID') || est.includes('CANCELAD');
    return oculto;
  });

  mostrarFloatingCard = computed(() => {
    if (this.mapaOculto()) return false;
    // Mostrar la tarjeta desde el principio para que el usuario vea el progreso.
    return this.tipoEntrega === 'delivery';
  });

  mostrarMoto = computed(() => {
    if (this.tipoEntrega !== 'delivery') return false;
    const ped = this.pedido();
    if (!ped) return false;
    const est = (ped.estado || '').toUpperCase();
    if (est.includes('CANCELAD') || est.includes('SUSPENDID') || est.includes('RECHAZAD')) return false;
    // La moto aparece a partir de "Motorizado asignado" (paso >= 2).
    return this.pasoEfectivo() >= 2;
  });

  /** La ruta (gris + naranja) solo se muestra a partir de "En camino" (paso >= 3). */
  mostrarRuta = computed(() => {
    if (this.tipoEntrega !== 'delivery') return false;
    return this.pasoEfectivo() >= 3;
  });

  estadoTexto = computed(() => {
    const ped = this.pedido();
    if (!ped) return 'Cargando…';

    // Usar el paso efectivo (padre o derivado del estado del pedido) para que
    // el badge coincida con el estado mostrado en la página de seguimiento.
    const paso = this.pasoEfectivo();
    const est = (ped.estado || '').toUpperCase();

    // Estados finales no entregados: el badge debe reflejarlos tal cual.
    if (est.includes('SUSPENDID')) return 'Suspendido';
    if (est.includes('CANCELAD')) return 'Cancelado';

    // Si el pedido está en espera/verificación, no mostrar estados de entrega
    if (est.includes('EN_ESPERA') || est.includes('ESPERANDO') || est.includes('PENDIENTE_VERIFICACION')) {
      return est === 'PENDIENTE_VERIFICACION' ? 'Verificando pago' : 'Esperando confirmación';
    }

    // Mapear el paso (0-4) al texto correspondiente, respetando el tiempo real
    switch (paso) {
      case 0: return 'Pedido confirmado';
      case 1: return 'En despacho';
      case 2: return this.tipoEntrega === 'delivery' ? 'Motorizado asignado' : 'Listo para recojo';
      case 3: return 'En camino';
      case 4: return 'Entregado';
      default:
        // Fallback por estado crudo si no hay paso claro
        if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 'Entregado';
        if (est.includes('EN_CAMINO')) return 'En camino';
        if (est.includes('LISTO')) return 'Listo para recojo';
        if (est.includes('PAGAD') || est.includes('CONFIRMAD')) return this.tipoEntrega === 'delivery' ? 'Motorizado asignado' : 'Listo para recojo';
        return 'Pedido confirmado';
    }
  });

  direccionCompleta = computed(() => {
    const d = this.pedido()?.direccionEnvio || '';
    const dist = this.pedido()?.distrito || '';
    if (!dist || d.toLowerCase().includes(dist.toLowerCase())) return d || dist;
    return `${d} (${dist})`;
  });

  costoEnvioTexto = computed(() => {
    const c = this.costoEnvio();
    if (Number.isNaN(c)) return 'Fuera de cobertura';
    if (c === 0) return 'Gratis';
    return `S/ ${c.toFixed(2)}`;
  });

  etaHora = computed(() => {
    const m = this.etaMin();
    const d = new Date();
    d.setMinutes(d.getMinutes() + m);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
  });

  /** Paso efectivo (0-4) usado por el badge de estado y la visibilidad de la ruta.
   *  Nunca queda por debajo del estado real del backend (piso). */
  pasoEfectivo = computed(() => {
    const est = (this.pedido()?.estado || '').toUpperCase();
    let backendPaso = 0;
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) backendPaso = 4;
    else if (est.includes('SUSPENDID') || est.includes('CANCELAD')) backendPaso = 3;
    else if (est.includes('EN_CAMINO')) backendPaso = 3;
    else if (est.includes('LISTO')) backendPaso = 2;
    else if (est.includes('PAGAD') || est.includes('CONFIRMAD')) backendPaso = 1;

    const padre = (this.estadoActualMapa != null && this.estadoActualMapa > 0) ? this.estadoActualMapa : 0;
    let paso = Math.max(padre, backendPaso);
    // No mostrar "Entregado" (paso 4) a menos que el backend lo confirme.
    if (backendPaso < 4 && paso >= 4) paso = 3;
    return paso;
  });

  ngOnInit(): void {
    this.iniciarPolling();
    // Avanza la simulación entre polls para que el repartidor se mueva "en vivo".
    this.simulacionTimer = setInterval(() => {
      if (!this.destruido && this.pedido() && !this.tieneCoordsReales()) {
        this.avanzarSimulacion();
      }
    }, 4000);
  }

  ngAfterViewInit(): void {
    // Fuera del ciclo de detección de cambios para evitar NG0100.
    setTimeout(() => {
      if (this.pedido() && !this.map) void this.crearMapa();
    }, 50);
  }

  ngOnDestroy(): void {
    this.destruido = true;
    if (this.pollingSub) this.pollingSub.unsubscribe();
    if (this.simulacionTimer) clearInterval(this.simulacionTimer);
    if (this.animacionMoto) cancelAnimationFrame(this.animacionMoto);
    this.infoWindow?.close();
    this.infoWindow = undefined;
    this.eliminarMarcadores();
    this.lineaRuta?.setMap(null);
    this.lineaCasing?.setMap(null);
    this.lineaRecorrida?.setMap(null);
    this.lineaRuta = undefined;
    this.lineaCasing = undefined;
    this.lineaRecorrida = undefined;
    this.map = undefined;
  }

  /** Inicializa el mapa de Google Maps una sola vez. */
  private async crearMapa(): Promise<void> {
    const el = this.mapContainer?.nativeElement;
    if (!el || this.map || this.creandoMapa || this.destruido) return;
    const ped = this.pedido();
    if (!ped) return;

    // Si el mapa está oculto (recojo o pedido ya entregado) no lo inicializamos.
    if (this.mapaOculto()) {
      this.cargandoMapa.set(false);
      return;
    }

    this.creandoMapa = true;

    // Resolver coordenadas reales: override manual > backend > geocodificación.
    let latDestino: number;
    let lngDestino: number;
    if (this.coordsOverride) {
      latDestino = this.coordsOverride.lat;
      lngDestino = this.coordsOverride.lng;
      this.usandoFallback = false;
    } else {
      latDestino = Number(ped.latitudEntrega);
      lngDestino = Number(ped.longitudEntrega);
      this.usandoFallback = false;
      if (!latDestino || !lngDestino) {
        this.usandoFallback = true;
        try {
          const coords = await this.mapaService.resolverDestino(ped);
          latDestino = coords[0];
          lngDestino = coords[1];
        } catch {
          latDestino = BOTICA_ANITA.lat;
          lngDestino = BOTICA_ANITA.lng;
        }
      }
    }

    // Validar que las coords sean números válidos dentro de Perú; si no, usar la tienda.
    if (!this.coordsValidas(latDestino, lngDestino)) {
      latDestino = BOTICA_ANITA.lat;
      lngDestino = BOTICA_ANITA.lng;
      this.usandoFallback = true;
    }
    this.destLat = latDestino;
    this.destLng = lngDestino;
    // El destino inicial ya está resuelto: evita que el primer poll vuelva a
    // calcular la ruta (antes se hacía dos veces al abrir el seguimiento).
    this.ultimasCoordsDestino = { lat: latDestino, lng: lngDestino };

    // Guardar en el pedido para no re-resolver
    if (ped && !ped.latitudEntrega) {
      (ped as any).latitudEntrega = latDestino;
      (ped as any).longitudEntrega = lngDestino;
    }

    if (!this.mapsLoader.disponible) {
      this.errorMapa.set('Google Maps no está configurado en este entorno.');
      this.cargandoMapa.set(false);
      this.creandoMapa = false;
      return;
    }

    try {
      // 1) Esperar a que `window.google.maps.importLibrary` exista de verdad.
      await this.mapsLoader.cargar();
      if (this.destruido) return;

      // 2) Constructores desde las librerías (sin `google.maps.*` global).
      const [mapsLib, markerLib, coreLib] = await Promise.all([
        this.mapsLoader.importLibrary('maps'),
        this.mapsLoader.importLibrary('marker'),
        this.mapsLoader.importLibrary('core'),
      ]);
      if (this.destruido || this.map) return;

      this.CtorAdvancedMarker = markerLib.AdvancedMarkerElement;
      this.CtorPolyline = mapsLib.Polyline;
      this.CtorLatLngBounds = coreLib.LatLngBounds;
      this.CtorInfoWindow = mapsLib.InfoWindow;

      this.map = new mapsLib.Map(el, {
        center: { lat: latDestino, lng: lngDestino },
        zoom: 14,
        // Requerido para que AdvancedMarkerElement renderice los tiles vectoriales.
        mapId: 'DEMO_MAP_ID',
        disableDefaultUI: true,
        zoomControl: false,
        clickableIcons: false,
        gestureHandling: 'cooperative',
        backgroundColor: '#eef2f7',
      });

      this.cargandoMapa.set(false);
      this.dibujarElementos(latDestino, lngDestino);
      this.ajustarCamara(latDestino, lngDestino);
    } catch (error) {
      console.error('No se pudo inicializar el mapa de seguimiento.', error);
      this.errorMapa.set('No se pudo cargar el mapa. Revisa tu conexión.');
      this.cargandoMapa.set(false);
    } finally {
      this.creandoMapa = false;
    }
  }

  /** Re-centra el mapa para encuadrar tienda y destino. */
  recentrar(): void {
    if (!this.map) return;
    this.ajustarCamara(this.destLat, this.destLng);
  }

  /** Ajusta la cámara para encuadrar tienda y destino. */
  private ajustarCamara(latDestino: number, lngDestino: number): void {
    const LatLngBounds = this.CtorLatLngBounds;
    if (!this.map || !LatLngBounds) return;

    if (!this.coordsValidas(latDestino, lngDestino)) {
      this.map.setCenter({ lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng });
      this.map.setZoom(15);
      return;
    }

    const dist = this.mapaService.distanciaM(
      BOTICA_ANITA.lat,
      BOTICA_ANITA.lng,
      latDestino,
      lngDestino
    );

    // Si el destino coincide con la tienda, no hacemos fitBounds (zoom excesivo).
    if (dist < 60) {
      this.map.setCenter({ lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng });
      this.map.setZoom(16);
      return;
    }

    const bounds = new LatLngBounds();
    bounds.extend({ lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng });
    bounds.extend({ lat: latDestino, lng: lngDestino });
    this.map.fitBounds(bounds, { top: 72, right: 60, bottom: 84, left: 60 });
  }

  /** Dibuja marcadores y lanza la carga de la ruta. */
  private dibujarElementos(latDestino: number, lngDestino: number): void {
    if (!this.map || !this.CtorAdvancedMarker) return;
    this.crearMarcadores(latDestino, lngDestino);
    void this.cargarRuta(latDestino, lngDestino);
  }

  /** Crea los marcadores fijos (tienda y domicilio). */
  private crearMarcadores(latDestino: number, lngDestino: number): void {
    const AdvancedMarkerElement = this.CtorAdvancedMarker;
    if (!AdvancedMarkerElement || !this.map) return;

    // Marcador de la tienda
    if (!this.markerTienda) {
      this.markerTienda = new AdvancedMarkerElement({
        map: this.map,
        position: { lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng },
        title: BOTICA_ANITA.nombre,
        gmpClickable: true,
        zIndex: 20,
        content: this.crearMarcador('tienda'),
      });
      this.markerTienda.addEventListener('gmp-click', () => {
        this.abrirInfo(
          'Botica Anita',
          [BOTICA_ANITA.direccion, 'Preparando tu pedido'],
          { lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng },
          this.markerTienda
        );
      });
    } else {
      this.markerTienda.position = { lat: BOTICA_ANITA.lat, lng: BOTICA_ANITA.lng };
    }

    // Marcador del domicilio (arrastrable para corregir la ubicación)
    if (!this.markerDestino) {
      this.markerDestino = new AdvancedMarkerElement({
        map: this.map,
        position: { lat: latDestino, lng: lngDestino },
        title: 'Tu domicilio',
        gmpClickable: true,
        gmpDraggable: true,
        zIndex: 30,
        content: this.crearMarcador('casa'),
      });

      this.markerDestino.addEventListener('gmp-click', () => {
        const pos = this.aLatLng(this.markerDestino?.position);
        if (!pos) return;
        this.abrirInfo(
          'Tu domicilio',
          [this.direccionCompleta(), 'Arrastra el pin para ajustar'],
          pos,
          this.markerDestino
        );
      });

      this.markerDestino.addEventListener('gmp-dragend', () => {
        const pos = this.aLatLng(this.markerDestino?.position);
        if (!pos) return;
        this.destLat = pos.lat;
        this.destLng = pos.lng;
        this.coordsOverride = pos;
        this.ultimasCoordsDestino = pos;
        this.usandoFallback = false;

        const ped = this.pedido();
        if (ped) {
          (ped as any).latitudEntrega = pos.lat;
          (ped as any).longitudEntrega = pos.lng;
        }

        // Persistir en el backend para que el email "en camino" use la ubicación corregida.
        if (this.idPedido) {
          this.pedidoService.actualizarUbicacion(this.idPedido, pos.lat, pos.lng).subscribe({
            next: () => { /* ok */ },
            error: (e) => { console.error('No se pudo guardar la ubicación corregida:', e); },
          });
        }

        void this.recalcularRuta(pos.lat, pos.lng, true);
      });
    } else {
      this.markerDestino.position = { lat: latDestino, lng: lngDestino };
    }
  }

  /** Calcula y dibuja la ruta completa tienda -> domicilio. */
  private async cargarRuta(latDestino: number, lngDestino: number): Promise<void> {
    const ruta = await this.mapaService.obtenerRutaPorCalles(
      BOTICA_ANITA.lng,
      BOTICA_ANITA.lat,
      lngDestino,
      latDestino
    );
    if (!this.map || this.destruido) return;

    this.aplicarRuta(ruta.coords);
    this.distanciaKm.set(ruta.distanciaM / 1000);
    this.calcularCostoEnvio();
    this.actualizarEta(ruta.distanciaM);
    this.avanzarSimulacion();
  }

  /** Recalcula la ruta tras mover el pin del domicilio. */
  private async recalcularRuta(lat: number, lng: number, ajustarCamara: boolean): Promise<void> {
    const ruta = await this.mapaService.obtenerRutaPorCalles(
      BOTICA_ANITA.lng,
      BOTICA_ANITA.lat,
      lng,
      lat
    );
    if (!this.map || this.destruido) return;

    this.aplicarRuta(ruta.coords);
    this.distanciaKm.set(ruta.distanciaM / 1000);
    this.calcularCostoEnvio();
    this.actualizarEta(ruta.distanciaM);
    if (ajustarCamara) this.ajustarCamara(lat, lng);
    this.avanzarSimulacion();
    this.actualizarPosicionRepartidor();
  }

  /** Pinta las polilíneas con una nueva geometría. */
  private aplicarRuta(coords: Array<[number, number]>): void {
    if (!this.map) return;
    this.rutaSimulada = coords;
    this.pasoSim = 0;

    const path = coords.map(([lat, lng]) => ({ lat, lng }));
    this.crearPolilineas();
    this.lineaCasing?.setPath(path);
    this.lineaRuta?.setPath(path);
    this.lineaRecorrida?.setPath([]);
    this.sincronizarVisibilidadRuta();
  }

  /** Crea las tres polilíneas (borde blanco, ruta gris y recorrido naranja). */
  private crearPolilineas(): void {
    const Polyline = this.CtorPolyline;
    if (!Polyline || !this.map) return;

    if (!this.lineaCasing) {
      this.lineaCasing = new Polyline({
        map: this.map,
        strokeColor: '#ffffff',
        strokeOpacity: 0.95,
        strokeWeight: 10,
        zIndex: 5,
        clickable: false,
        geodesic: true,
      });
    }
    if (!this.lineaRuta) {
      this.lineaRuta = new Polyline({
        map: this.map,
        strokeColor: '#94a3b8',
        strokeOpacity: 0.85,
        strokeWeight: 6,
        zIndex: 6,
        clickable: false,
        geodesic: true,
      });
    }
    if (!this.lineaRecorrida) {
      this.lineaRecorrida = new Polyline({
        map: this.map,
        strokeColor: '#e85d04',
        strokeOpacity: 1,
        strokeWeight: 7,
        zIndex: 7,
        clickable: false,
        geodesic: true,
      });
    }
  }

  /** Actualiza el destino cuando el backend ya geolocalizó la dirección. */
  private actualizarDestinoSiCambio(): void {
    if (!this.map || !this.pedido()) return;
    const ped = this.pedido();
    if (!ped) return;
    const lat = Number(ped.latitudEntrega) || 0;
    const lng = Number(ped.longitudEntrega) || 0;
    if (!lat || !lng) return;
    if (this.coordsOverride) return; // la corrección manual manda

    // Solo re-fetch si las coords cambiaron (evita parpadeo en cada poll).
    if (
      this.ultimasCoordsDestino &&
      Math.abs(this.ultimasCoordsDestino.lat - lat) < 0.00001 &&
      Math.abs(this.ultimasCoordsDestino.lng - lng) < 0.00001
    ) {
      return;
    }
    this.ultimasCoordsDestino = { lat, lng };
    this.destLat = lat;
    this.destLng = lng;

    if (this.markerDestino) {
      this.markerDestino.position = { lat, lng };
    }

    void this.recalcularRuta(lat, lng, true);
  }

  /** Polling del backend cada 8s. */
  private iniciarPolling(): void {
    this.pollingSub = timer(0, 8000)
      .pipe(switchMap(() => this.trackingService.getTrackingInfo(this.idPedido)))
      .subscribe({
        next: (data) => {
          // Preservar corrección manual del usuario (drag) sobre el backend.
          if (this.coordsOverride && data) {
            (data as any).latitudEntrega = this.coordsOverride.lat;
            (data as any).longitudEntrega = this.coordsOverride.lng;
          }
          this.pedido.set(data);
          const est = (data.estado || '').toUpperCase();
          this.sincronizarVisibilidadRuta();

          if (!this.map) {
            void this.crearMapa();
          } else {
            this.actualizarDestinoSiCambio();
          }

          if (this.tieneCoordsReales()) {
            this.mostrandoSimulacion.set(false);
            this.actualizarPosicionRepartidor();
          } else {
            this.mostrandoSimulacion.set(true);
            this.avanzarSimulacion();
          }

          const estFinal =
            est.includes('ENTREGAD') ||
            est.includes('COMPLETAD') ||
            est.includes('CANCELAD') ||
            est.includes('RECHAZAD');
          if (estFinal) {
            this.etaMin.set(0);
            if (this.pollingSub) this.pollingSub.unsubscribe();
          }
        },
        error: (err) => {
          console.error('Error al obtener la ubicación del pedido:', err);
        },
      });
  }

  private tieneCoordsReales(): boolean {
    const p = this.pedido();
    return !!p && !!p.latitudRepartidor && !!p.longitudRepartidor;
  }

  /** Tope de progreso (%) según el estado real.
   *  En camino la moto llega hasta el domicilio (100%), pero el estado final
   *  solo cambia cuando el asistente lo confirma. */
  private topePorEstado(): number {
    const est = (this.pedido()?.estado || '').toUpperCase();
    if (est.includes('COMPLETAD') || est.includes('ENTREGAD')) return 100;
    if (est.includes('EN_CAMINO')) return 100;
    if (est.includes('RECHAZAD') || est.includes('CANCELAD') || est.includes('SUSPENDID')) return 0;
    return 0;
  }

  /** Avanza el marcador del repartidor según el tiempo real desde el pedido. */
  private avanzarSimulacion(): void {
    if (!this.map || this.rutaSimulada.length === 0) return;
    const tope = this.topePorEstado();
    const topePaso = Math.floor((tope / 100) * (this.rutaSimulada.length - 1));
    let objetivo = topePaso;
    if (this.fechaPedido && this.rutaSimulada.length > 0) {
      const elapsedMin = (Date.now() - msDesdeFecha(this.fechaPedido)) / 60000;
      const inicioViaje = MIN_CONFIRMADO + MIN_DESPACHO + MIN_MOTORIZADO;
      const tripMin = this.tripMinDesdeDistancia();
      const frac = Math.min(Math.max((elapsedMin - inicioViaje) / tripMin, 0), 1);
      objetivo = Math.min(Math.floor(frac * (this.rutaSimulada.length - 1)), topePaso);
    }
    this.pasoSim = Math.max(this.pasoSim, Math.min(objetivo, topePaso));
    const pos = this.rutaSimulada[this.pasoSim];
    this.pintarRepartidor(pos[0], pos[1]);
    const restante = this.mapaService.distanciaM(
      pos[0],
      pos[1],
      this.rutaSimulada[this.rutaSimulada.length - 1][0],
      this.rutaSimulada[this.rutaSimulada.length - 1][1]
    );
    this.actualizarEta(restante);
  }

  private tripMinDesdeDistancia(): number {
    const d = this.distanciaKm() * 1000;
    return Math.max(1, Math.round((d / 1000 / 25) * 60));
  }

  private actualizarPosicionRepartidor(): void {
    const ped = this.pedido();
    if (!ped || !this.map) return;
    if (this.tieneCoordsReales()) {
      const lat = Number(ped.latitudRepartidor);
      const lng = Number(ped.longitudRepartidor);
      this.pintarRepartidor(lat, lng);
      this.etaMin.set(0);
      return;
    }
    if (this.rutaSimulada.length > 0) {
      const pos = this.rutaSimulada[this.pasoSim];
      this.pintarRepartidor(pos[0], pos[1]);
    }
  }

  private pintarRepartidor(lat: number, lng: number): void {
    if (!this.mostrarMoto()) {
      if (this.markerRepartidor) {
        this.markerRepartidor.map = null;
        this.markerRepartidor = undefined;
        this.repartidorEl = undefined;
      }
      return;
    }
    const AdvancedMarkerElement = this.CtorAdvancedMarker;
    if (!this.map || !AdvancedMarkerElement) return;

    if (!this.markerRepartidor) {
      const contenedor = this.crearMarcadorMoto();
      const svg = contenedor.querySelector('svg') as SVGElement;
      svg.style.transition = 'transform .4s ease';
      this.repartidorEl = svg;

      this.markerRepartidor = new AdvancedMarkerElement({
        map: this.map,
        position: { lat, lng },
        title: 'Repartidor en camino',
        gmpClickable: true,
        zIndex: 40,
        anchorTop: '-50%',
        content: contenedor,
      });

      this.markerRepartidor.addEventListener('gmp-click', () => {
        const pos = this.aLatLng(this.markerRepartidor?.position);
        if (!pos) return;
        this.abrirInfo('Repartidor en camino', ['Tu pedido va en ruta.'], pos, this.markerRepartidor);
      });

      this.aplicarRotacionMoto();
    } else {
      this.moverRepartidorSuave(lat, lng);
      this.aplicarRotacionMoto();
    }

    // Línea del recorrido crece conforme avanza el repartidor.
    const recorridos = this.rutaSimulada.slice(0, this.pasoSim + 1);
    if (recorridos.length > 1) {
      this.lineaRecorrida?.setPath(recorridos.map(([la, ln]) => ({ lat: la, lng: ln })));
    }
  }

  /**
   * Anima el marcador del repartidor entre la posición anterior y la nueva.
   * Se ejecuta fuera de Angular para no disparar detección de cambios por frame.
   */
  private moverRepartidorSuave(lat: number, lng: number): void {
    const marker = this.markerRepartidor;
    if (!marker) return;
    const desde = this.aLatLng(marker.position);
    if (!desde) {
      marker.position = { lat, lng };
      return;
    }
    // Sin cambio real: no animamos para ahorrar frames.
    if (Math.abs(desde.lat - lat) < 1e-9 && Math.abs(desde.lng - lng) < 1e-9) return;

    if (this.animacionMoto) cancelAnimationFrame(this.animacionMoto);
    const inicio = performance.now();
    const duracion = 900;

    this.zone.runOutsideAngular(() => {
      const paso = (ahora: number): void => {
        if (!this.markerRepartidor) return;
        const t = Math.min(1, (ahora - inicio) / duracion);
        // easeInOutQuad
        const e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        this.markerRepartidor.position = {
          lat: desde.lat + (lat - desde.lat) * e,
          lng: desde.lng + (lng - desde.lng) * e,
        };
        if (t < 1) this.animacionMoto = requestAnimationFrame(paso);
      };
      this.animacionMoto = requestAnimationFrame(paso);
    });
  }

  /** Rota el icono de la moto para que mire en la dirección del viaje. */
  private aplicarRotacionMoto(): void {
    if (!this.repartidorEl || this.rutaSimulada.length < 2) return;
    const idx = Math.min(this.pasoSim, this.rutaSimulada.length - 2);
    const a = this.rutaSimulada[idx];
    const b = this.rutaSimulada[idx + 1];
    if (!a || !b) return;
    const lat1 = a[0], lng1 = a[1];
    const lat2 = b[0], lng2 = b[1];
    const toRad = (d: number) => (d * Math.PI) / 180;
    const y = Math.sin(toRad(lng2 - lng1)) * Math.cos(toRad(lat2));
    const x =
      Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) -
      Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(toRad(lng2 - lng1));
    const brng = (Math.atan2(y, x) * 180) / Math.PI;
    this.bearingActual = (brng + 360) % 360;
    this.repartidorEl.style.transform = `rotate(${this.bearingActual}deg)`;
    this.repartidorEl.style.transformOrigin = 'center center';
  }

  /** Actualiza la visibilidad de las polilíneas según `mostrarRuta`. */
  private sincronizarVisibilidadRuta(): void {
    const visible = this.mostrarRuta();
    this.lineaRuta?.setVisible(visible);
    this.lineaCasing?.setVisible(visible);
    this.lineaRecorrida?.setVisible(visible);
  }

  private actualizarEta(distM: number): void {
    this.etaMin.set(Math.max(1, Math.round((distM / 1000 / 25) * 60)));
  }

  private calcularCostoEnvio(): void {
    const distKm = this.distanciaKm();
    const esUrgente = this.tipoEntrega === 'delivery';
    this.costoEnvio.set(this.mapaService.costoEnvio(distKm, esUrgente));
  }

  // ── Utilidades ────────────────────────────────────────────────────────────

  /** Crea el pin vectorial (Botica Anita o domicilio) para el mapa. */
  private crearMarcador(variante: 'tienda' | 'casa'): HTMLElement {
    const el = document.createElement('div');
    el.className = 'ba-pin';
    el.innerHTML = variante === 'tienda' ? SVG_PIN_BOTICA : SVG_PIN_CASA;
    return el;
  }

  /** Crea el icono vectorial del repartidor (vista superior). */
  private crearMarcadorMoto(): HTMLElement {
    const el = document.createElement('div');
    el.className = 'ba-moto';
    el.innerHTML = SVG_MOTO;
    return el;
  }

  /** Abre un InfoWindow con contenido seguro (sin inyectar HTML). */
  private abrirInfo(
    titulo: string,
    lineas: string[],
    pos: { lat: number; lng: number },
    anchor?: google.maps.marker.AdvancedMarkerElement
  ): void {
    const Ctor = this.CtorInfoWindow;
    if (!Ctor || !this.map) return;

    if (!this.infoWindow) this.infoWindow = new Ctor();
    this.infoWindow.close();

    const cont = document.createElement('div');
    cont.style.cssText =
      "font:13px/1.45 'Outfit',system-ui,sans-serif;color:#334155;max-width:220px;";

    const h = document.createElement('strong');
    h.textContent = titulo;
    h.style.cssText = 'display:block;color:#1a2e4a;font-size:13.5px;margin-bottom:2px;';
    cont.appendChild(h);

    for (const linea of lineas) {
      if (!linea) continue;
      const p = document.createElement('div');
      p.textContent = linea;
      cont.appendChild(p);
    }

    this.infoWindow.setContent(cont);
    this.infoWindow.setPosition(pos);
    this.infoWindow.open({ map: this.map, anchor });
  }

  /** Normaliza la posición de un marcador a `{ lat, lng }`. */
  private aLatLng(
    pos: google.maps.LatLng | google.maps.LatLngLiteral | google.maps.LatLngAltitude | google.maps.LatLngAltitudeLiteral | null | undefined
  ): { lat: number; lng: number } | null {
    if (!pos) return null;
    if (typeof (pos as google.maps.LatLng).lat === 'function') {
      const ll = pos as google.maps.LatLng;
      return { lat: ll.lat(), lng: ll.lng() };
    }
    const literal = pos as google.maps.LatLngLiteral;
    if (typeof literal.lat !== 'number' || typeof literal.lng !== 'number') return null;
    return { lat: literal.lat, lng: literal.lng };
  }

  /** Coordenadas válidas dentro de Perú. */
  private coordsValidas(lat: number, lng: number): boolean {
    return (
      Number.isFinite(lat) && Number.isFinite(lng) &&
      lat >= -20 && lat <= 0 &&
      lng >= -82 && lng <= -68
    );
  }

  private eliminarMarcadores(): void {
    for (const marker of [this.markerTienda, this.markerDestino, this.markerRepartidor]) {
      if (marker) marker.map = null;
    }
    this.markerTienda = undefined;
    this.markerDestino = undefined;
    this.markerRepartidor = undefined;
    this.repartidorEl = undefined;
  }
}
