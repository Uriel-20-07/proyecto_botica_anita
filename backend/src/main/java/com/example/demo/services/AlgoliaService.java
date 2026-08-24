package com.example.demo.services;

import com.example.demo.models.Producto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para sincronizar productos con el índice de Algolia.
 *
 * Usa la REST API de Algolia directamente (sin SDK) para:
 * - Indexar un producto al crearlo/editarlo (saveObject)
 * - Eliminar un producto del índice al borrarlo (deleteObject)
 * - Indexar todos los productos en lote al arrancar (batch)
 *
 * Configurar en application.properties:
 *   algolia.app-id=TU_APP_ID
 *   algolia.admin-key=TU_ADMIN_API_KEY
 *   algolia.index=productos
 */
@Service
public class AlgoliaService {

    @Value("${algolia.app-id}")
    private String appId;

    @Value("${algolia.admin-key}")
    private String adminKey;

    @Value("${algolia.index}")
    private String indexName;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Construye la URL base de la REST API de Algolia para el índice configurado.
     */
    private String baseUrl() {
        return "https://" + appId + "-dsn.algolia.net/1/indexes/" + indexName;
    }

    /**
     * Convierte un Producto a un Map con los campos que Algolia debe indexar.
     * El campo "objectID" es obligatorio en Algolia (equivale a la PK).
     */
    private Map<String, Object> toAlgoliaRecord(Producto producto) {
        Map<String, Object> record = new HashMap<>();
        record.put("objectID", String.valueOf(producto.getIdProducto()));
        record.put("idProducto", producto.getIdProducto());
        record.put("nombre", producto.getNombre());
        record.put("descripcion", producto.getDescripcion() != null ? producto.getDescripcion() : "");
        record.put("precioVenta", producto.getPrecioVenta() != null ? producto.getPrecioVenta().doubleValue() : 0.0);
        record.put("precioConDescuento", producto.getPrecioConDescuento() != null ? producto.getPrecioConDescuento().doubleValue() : (producto.getPrecioVenta() != null ? producto.getPrecioVenta().doubleValue() : 0.0));
        record.put("descuentoPorcentaje", producto.getDescuentoPorcentaje() != null ? producto.getDescuentoPorcentaje().doubleValue() : 0.0);
        record.put("stock", producto.getStock() != null ? producto.getStock() : 0);
        record.put("imgUrl", producto.getImgUrl() != null ? producto.getImgUrl() : "");
        record.put("fechaCaducidad", producto.getFechaCaducidad() != null ? producto.getFechaCaducidad().toString() : "");

        if (producto.getCategoria() != null) {
            record.put("categoriaId", producto.getCategoria().getIdCategoria());
            record.put("categoriaNombre", producto.getCategoria().getNombre());
        } else {
            record.put("categoriaId", null);
            record.put("categoriaNombre", "General");
        }
        return record;
    }

    /**
     * Indexa o actualiza un producto en Algolia.
     * Llamar desde ProductoService al crear o editar un producto.
     *
     * @param producto el producto a indexar.
     */
    public void indexarProducto(Producto producto) {
        try {
            String objectID = String.valueOf(producto.getIdProducto());
            String body = objectMapper.writeValueAsString(toAlgoliaRecord(producto));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/" + objectID))
                    .header("Content-Type", "application/json")
                    .header("X-Algolia-Application-Id", appId)
                    .header("X-Algolia-API-Key", adminKey)
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Algolia] indexarProducto id=" + objectID + " → status " + response.statusCode());
        } catch (Exception e) {
            System.err.println("[Algolia] Error al indexar producto: " + e.getMessage());
        }
    }

    /**
     * Elimina un producto del índice de Algolia.
     * Llamar desde ProductoService al eliminar un producto.
     *
     * @param idProducto ID del producto a eliminar.
     */
    public void eliminarProducto(Integer idProducto) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/" + idProducto))
                    .header("X-Algolia-Application-Id", appId)
                    .header("X-Algolia-API-Key", adminKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Algolia] eliminarProducto id=" + idProducto + " → status " + response.statusCode());
        } catch (Exception e) {
            System.err.println("[Algolia] Error al eliminar producto de Algolia: " + e.getMessage());
        }
    }

    /**
     * Indexa todos los productos en lote (batch).
     * Llamar una vez al arrancar la app (desde DataSeeder o un @PostConstruct)
     * para llenar el índice con los datos existentes en la BD.
     *
     * @param productos lista completa de productos.
     */
    public void indexarTodos(List<Producto> productos) {
        try {
            // Algolia batch espera: { "requests": [ { "action": "updateObject", "body": {...} }, ... ] }
            List<Map<String, Object>> requests = productos.stream().map(p -> {
                Map<String, Object> req = new HashMap<>();
                req.put("action", "updateObject");
                req.put("body", toAlgoliaRecord(p));
                return req;
            }).toList();

            Map<String, Object> payload = new HashMap<>();
            payload.put("requests", requests);
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/batch"))
                    .header("Content-Type", "application/json")
                    .header("X-Algolia-Application-Id", appId)
                    .header("X-Algolia-API-Key", adminKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Algolia] indexarTodos " + productos.size() + " productos → status " + response.statusCode());
        } catch (Exception e) {
            System.err.println("[Algolia] Error en indexación masiva: " + e.getMessage());
        }
    }
}
