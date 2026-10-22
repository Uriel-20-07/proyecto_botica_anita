package com.example.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geocodificacion de direcciones con Nominatim (servicio gratuito de OpenStreetMap).
 *
 * Reglas importantes:
 * - Nominatim acepta como maximo 1 peticion por segundo: si se supera responde 429.
 *   Por eso aqui se controla el ritmo de forma global.
 * - La direccion se busca primero sola (con la ciudad) y solo despues se prueba
 *   anadiendo el distrito. Si se combinaran de entrada, una direccion de otro
 *   distrito no devolveria ningun resultado y la validacion se quedaria sin hacer.
 */
@Service
public class GeocodificacionService {

    private static final long MS_ENTRE_PETICIONES = 1200;
    private static final Object CANDADO = new Object();
    private static long ultimaPeticion = 0;

    /** Cache de candidatos por direccion (clave -> lista de candidatos). */
    private static final Map<String, List<Map<String, Object>>> CACHE = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Busca las coordenadas de una direccion.
     *
     * @return {lat, lng} o null si no se pudo resolver.
     */
    public double[] buscar(String direccion, String distrito) {
        List<Map<String, Object>> cands = candidatos(direccion, distrito);
        if (cands.isEmpty()) return null;

        // Se prefiere el candidato que mencione el distrito; si ninguno, el primero.
        Map<String, Object> mejor = cands.get(0);
        if (distrito != null && !distrito.isBlank()) {
            String dist = distrito.toLowerCase();
            for (Map<String, Object> c : cands) {
                String nombre = String.valueOf(c.get("display_name")).toLowerCase();
                if (nombre.contains(dist)) {
                    mejor = c;
                    break;
                }
            }
        }
        double lat = Double.parseDouble(String.valueOf(mejor.get("lat")));
        double lng = Double.parseDouble(String.valueOf(mejor.get("lon")));
        CACHE.put(clave(direccion, distrito), new ArrayList<>(cands));
        return new double[] { lat, lng };
    }

    /** Todos los candidatos de una direccion (para elegir el mas cercano a la tienda). */
    public List<Map<String, Object>> candidatos(String direccion, String distrito) {
        if (direccion == null || direccion.isBlank()) return new ArrayList<>();
        List<Map<String, Object>> hit = CACHE.get(clave(direccion, distrito));
        if (hit != null) return hit;
        List<Map<String, Object>> cands = consultar(direccion, distrito);
        if (!cands.isEmpty()) {
            CACHE.put(clave(direccion, distrito), new ArrayList<>(cands));
        }
        return cands;
    }

    private String clave(String direccion, String distrito) {
        return (direccion == null ? "" : direccion.trim().toLowerCase())
                + "|" + (distrito == null ? "" : distrito.trim().toLowerCase());
    }

    /** Devuelve todos los candidatos, con la direccion primero y el distrito despues. */
    private List<Map<String, Object>> consultar(String direccion, String distrito) {
        // 1) Direccion + ciudad (sin forzar el distrito).
        List<Map<String, Object>> cands = pedir(direccion + ", Lima, Peru");
        if (cands.isEmpty() && distrito != null && !distrito.isBlank()) {
            // 2) Direccion + distrito + ciudad.
            cands = pedir(direccion + ", " + distrito + ", Lima, Peru");
        }
        return cands;
    }

    private List<Map<String, Object>> pedir(String consulta) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&q="
                    + URLEncoder.encode(consulta, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "BoticaAnita-tracking/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                // 429 = demasiadas peticiones. No se bloquea el flujo: se reintenta luego.
                return new ArrayList<>();
            }
            JsonNode arr = objectMapper.readTree(res.body());
            List<Map<String, Object>> cands = new ArrayList<>();
            if (arr == null || !arr.isArray()) return cands;
            for (JsonNode n : arr) {
                cands.add(objectMapper.convertValue(n, Map.class));
            }
            return cands;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Espera su turno para no provocar un 429 de Nominatim. */
    public void esperarTurno() {
        synchronized (CANDADO) {
            long desde = System.currentTimeMillis() - ultimaPeticion;
            if (desde < MS_ENTRE_PETICIONES) {
                try {
                    Thread.sleep(MS_ENTRE_PETICIONES - desde);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ultimaPeticion = System.currentTimeMillis();
        }
    }
}
