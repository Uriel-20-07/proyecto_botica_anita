package com.example.demo.services;

import com.example.demo.models.Pedido;
import com.example.demo.repositories.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logica compartida del seguimiento de pedidos: geocodificacion de la direccion
 * y avance del estado segun el tiempo transcurrido.
 *
 * La usan tanto el endpoint de tracking (para responder en vivo) como el
 * scheduler (para que los pedidos avancen aunque nadie los este viendo).
 */
@Service
public class PedidoTrackingService {

    // Tienda Botica Anita: Av. Los Jazmines 408, Independencia (pin verificado).
    public static final double TIENDA_LAT = -12.001181821042714;
    public static final double TIENDA_LNG = -77.04736614959307;

    // Minutos fijos por etapa antes del viaje (igual que el frontend).
    public static final long MIN_CONFIRMADO = 0;
    public static final long MIN_DESPACHO = 1;
    public static final long MIN_MOTORIZADO = 1;
    public static final long MIN_PREVIOS = MIN_CONFIRMADO + MIN_DESPACHO + MIN_MOTORIZADO; // 2 min

    /** Minutos sin reintentar la geocodificacion de un pedido (evita el 429 de Nominatim). */
    private static final long REINTENTO_PEDIDO_MS = 60_000;
    /** Pedidos bloqueados 5 minutos cuando Nominatim responde 429. */
    private static final long BLOQUEO_POR_429_MS = 300_000;
    /** Ultimo intento por pedido. */
    private static final Map<Integer, Long> ULTIMO_INTENTO = new ConcurrentHashMap<>();
    /** Pedidos bloqueados por respuesta 429 de Nominatim. */
    private static final Map<Integer, Long> BLOQUEADOS = new ConcurrentHashMap<>();

    private final PedidoRepository pedidoRepository;
    private final GeocodificacionService geocodificacion;

    public PedidoTrackingService(PedidoRepository pedidoRepository, GeocodificacionService geocodificacion) {
        this.pedidoRepository = pedidoRepository;
        this.geocodificacion = geocodificacion;
    }

    /**
     * Avanza el estado del pedido segun el tiempo transcurrido:
     * PAGADO/CONFIRMADO -> EN_CAMINO (2 min) -> COMPLETADO (2 min + viaje + 10 s).
     * En recojo: PAGADO -> LISTO_RECOJO (2 min) -> COMPLETADO (1 hora despues).
     */
    @Transactional
    public Pedido avanzarEstado(Pedido pedido) {
        return avanzarEstado(pedido, true);
    }

    /**
     * @param permitirGeocodificar si es false no se llama a Nominatim (se usa el
     *                            tiempo de viaje por defecto). Sirve para el
     *            scheduler, que no debe saturar el servicio de geocodificacion.
     */
    @Transactional
    public Pedido avanzarEstado(Pedido pedido, boolean permitirGeocodificar) {
        if (pedido.getFecha() == null) return pedido;
        String estado = pedido.getEstado() != null ? pedido.getEstado().toUpperCase() : "";
        if (estado.contains("COMPLETAD") || estado.contains("ENTREGAD")
                || estado.contains("CANCELAD") || estado.contains("RECHAZAD")) {
            return pedido;
        }
        long segundos = Duration.between(pedido.getFecha(), LocalDateTime.now(ZoneOffset.UTC)).getSeconds();

        // Una sola vez: geocodifica la direccion y la guarda para que frontend y
        // backend usen el mismo punto.
        boolean guardoCoordenadas = permitirGeocodificar && resolverYGuardarCoordenadas(pedido);

        double tripMin = 5.0;
        if (pedido.getLatitudEntrega() != null && pedido.getLongitudEntrega() != null) {
            double distM = distanciaM(TIENDA_LAT, TIENDA_LNG,
                    pedido.getLatitudEntrega().doubleValue(), pedido.getLongitudEntrega().doubleValue());
            tripMin = Math.max(1.0, (distM / 1000.0 / 25.0) * 60.0);
        }
        long tripMinSeg = Math.round(tripMin * 60.0);

        boolean cambio = false;
        boolean urgente = pedido.isEsUrgente();
        if ((estado.equals("PAGADO") || estado.equals("CONFIRMADO")) && segundos >= MIN_PREVIOS * 60) {
            pedido.setEstado(urgente ? "EN_CAMINO" : "LISTO_RECOJO");
            cambio = true;
        }
        if ("EN_CAMINO".equalsIgnoreCase(pedido.getEstado())
                && segundos >= MIN_PREVIOS * 60 + tripMinSeg + 10) {
            pedido.setEstado("COMPLETADO");
            cambio = true;
        }
        if ("LISTO_RECOJO".equalsIgnoreCase(pedido.getEstado()) && segundos >= MIN_PREVIOS * 60 + 3600) {
            // Recojo sin reclamo en 1 hora: se cierra solo.
            pedido.setEstado("COMPLETADO");
            cambio = true;
        }
        if (cambio || guardoCoordenadas) {
            return pedidoRepository.save(pedido);
        }
        return pedido;
    }

    /**
     * Geocodifica la direccion una sola vez (Nominatim, gratis) y la deja en el pedido.
     * Elige el tramo de calle mas cercano a la tienda.
     *
     * @return true si se resolvieron coordenadas nuevas
     */
    private boolean resolverYGuardarCoordenadas(Pedido pedido) {
        if (pedido.getLatitudEntrega() != null && pedido.getLongitudEntrega() != null) {
            return false;
        }
        Integer id = pedido.getIdPedido();
        if (!puedeIntentarGeocodificar(id)) {
            return false;
        }
        try {
            geocodificacion.esperarTurno();
            List<Map<String, Object>> cands = geocodificacion.candidatos(
                    pedido.getDireccionEnvio(), pedido.getDistrito());
            if (cands.isEmpty()) return false;

            // Se prefieren los candidatos que mencionen el distrito y, entre ellos,
            // el tramo de calle mas cercano a la tienda.
            String dist = pedido.getDistrito() != null ? pedido.getDistrito().toLowerCase() : "";
            List<Map<String, Object>> preferidos = new ArrayList<>();
            if (!dist.isBlank()) {
                for (Map<String, Object> c : cands) {
                    if (String.valueOf(c.get("display_name")).toLowerCase().contains(dist)) {
                        preferidos.add(c);
                    }
                }
            }
            List<Map<String, Object>> aUsar = preferidos.isEmpty() ? cands : preferidos;

            Map<String, Object> mejor = aUsar.get(0);
            double mejorD = Double.MAX_VALUE;
            for (Map<String, Object> c : aUsar) {
                double d = distanciaM(TIENDA_LAT, TIENDA_LNG,
                        Double.parseDouble(String.valueOf(c.get("lat"))),
                        Double.parseDouble(String.valueOf(c.get("lon"))));
                if (d < mejorD) {
                    mejorD = d;
                    mejor = c;
                }
            }
            pedido.setLatitudEntrega(new BigDecimal(String.valueOf(mejor.get("lat"))));
            pedido.setLongitudEntrega(new BigDecimal(String.valueOf(mejor.get("lon"))));
            return true;
        } catch (Exception e) {
            // Sin coordenadas se usa el viaje por defecto; no se bloquea el tracking.
            System.out.println("No se pudo geocodificar la direccion del pedido "
                    + id + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Control de ritmo: no se repite el mismo pedido antes de 1 minuto (asi no se
     * satura Nominatim con 429).
     */
    private boolean puedeIntentarGeocodificar(Integer idPedido) {
        long ahora = System.currentTimeMillis();
        Long bloqueo = BLOQUEADOS.get(idPedido);
        if (bloqueo != null) {
            if (ahora < bloqueo) {
                return false;
            }
            BLOQUEADOS.remove(idPedido);
        }
        Long previo = ULTIMO_INTENTO.get(idPedido);
        if (previo != null && ahora - previo < REINTENTO_PEDIDO_MS) {
            return false;
        }
        ULTIMO_INTENTO.put(idPedido, System.currentTimeMillis());
        return true;
    }

    /** Distancia en metros (Haversine). */
    public double distanciaM(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
