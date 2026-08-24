package com.example.demo.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Servicio encargado de gestionar la subida de archivos al bucket de Supabase Storage.
 * Utiliza la API REST de Supabase mediante el cliente HTTP nativo de Java 11+.
 */
@Service
public class SupabaseStorageService {

    @Value("${supabase.url:https://vhopbfmflwbfpqkvcjtv.supabase.co}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Value("${supabase.bucket:recetas}")
    private String supabaseBucket;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Sube un archivo a un bucket de Supabase Storage y retorna su URL de visualización pública.
     *
     * @param file archivo recibido en el endpoint de multipart.
     * @return URL pública absoluta del archivo subido.
     * @throws Exception si la clave no está configurada o si el servidor de Supabase devuelve un error.
     */
    public String subirArchivo(MultipartFile file) throws Exception {
        if (supabaseKey == null || supabaseKey.isBlank()) {
            throw new IllegalStateException("La clave de API de Supabase (supabase.key) no está configurada en application.properties.");
        }

        // Obtener la extensión y generar un nombre único para evitar colisiones
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + extension;

        // Limpiar la URL de Supabase eliminando barras finales
        String cleanUrl = supabaseUrl;
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }

        // URL del endpoint de subida de objetos en Supabase Storage
        String uploadUrl = cleanUrl + "/storage/v1/object/" + supabaseBucket + "/" + newFilename;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", file.getContentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // El endpoint devuelve 200 OK en caso de éxito
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error al subir a Supabase Storage (Status " + response.statusCode() + "): " + response.body());
        }

        // Retornar la URL de descarga o visualización pública del objeto
        return cleanUrl + "/storage/v1/object/public/" + supabaseBucket + "/" + newFilename;
    }
}
