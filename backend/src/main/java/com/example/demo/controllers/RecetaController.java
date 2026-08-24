package com.example.demo.controllers;

import com.example.demo.dto.RecetaRequest;
import com.example.demo.dto.RevisionRequest;
import com.example.demo.models.Administrador;
import com.example.demo.models.EstadoReceta;
import com.example.demo.models.Receta;
import com.example.demo.models.User;
import com.example.demo.repositories.AdministradorRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.RecetaService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recetas")
public class RecetaController {

    @Autowired
    private RecetaService recetaService;

    @Autowired
    private com.example.demo.services.SupabaseStorageService supabaseStorageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdministradorRepository administradorRepository;

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody RecetaRequest request, Authentication authentication) {
        User usuario = obtenerUsuarioDesdeToken(authentication);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión para subir una receta.");
        }

        Receta receta = new Receta();
        receta.setPacienteId(usuario.getId());
        receta.setMedicoId(request.getMedicoId());
        receta.setFechaEmision(request.getFechaEmision());
        receta.setDocumentoUrl(request.getDocumentoUrl());

        String medicamentosTexto = request.getMedicamentos() == null ? "" :
                request.getMedicamentos().stream()
                        .map(m -> m.getNombre() + " (" + m.getDosis() + ")")
                        .collect(Collectors.joining("; "));
        receta.setMedicamentos(medicamentosTexto);

        Receta guardada = recetaService.guardar(receta);
        return ResponseEntity.ok(guardada);
    }

    @GetMapping("/mias")
    public ResponseEntity<?> misRecetas(Authentication authentication) {
        User usuario = obtenerUsuarioDesdeToken(authentication);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }
        return ResponseEntity.ok(recetaService.listarPorPaciente(usuario.getId()));
    }

    @GetMapping
    public ResponseEntity<?> listarPorEstado(
            @RequestParam(defaultValue = "EN_ESPERA") String estado,
            Authentication authentication
    ) {
        Administrador admin = obtenerAdminDesdeToken(authentication);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso restringido a administradores.");
        }
        EstadoReceta estadoEnum = EstadoReceta.valueOf(estado.toUpperCase());
        return ResponseEntity.ok(recetaService.listarPorEstado(estadoEnum));
    }

    @PatchMapping("/{id}/aprobar")
    public ResponseEntity<?> aprobar(
            @PathVariable Integer id,
            @RequestBody(required = false) RevisionRequest body,
            Authentication authentication
    ) {
        Administrador admin = obtenerAdminDesdeToken(authentication);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso restringido a administradores.");
        }
        String comentario = body != null ? body.getComentario() : null;
        Receta actualizada = recetaService.actualizarEstado(id, EstadoReceta.APROBADA, admin.getIdAdmin(), comentario);
        return ResponseEntity.ok(actualizada);
    }

    @PatchMapping("/{id}/rechazar")
    public ResponseEntity<?> rechazar(
            @PathVariable Integer id,
            @RequestBody RevisionRequest body,
            Authentication authentication
    ) {
        Administrador admin = obtenerAdminDesdeToken(authentication);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso restringido a administradores.");
        }
        if (body == null || body.getComentario() == null || body.getComentario().isBlank()) {
            return ResponseEntity.badRequest().body("El comentario es obligatorio para rechazar una receta.");
        }
        Receta actualizada = recetaService.actualizarEstado(id, EstadoReceta.RECHAZADA, admin.getIdAdmin(), body.getComentario());
        return ResponseEntity.ok(actualizada);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> subirArchivo(
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo está vacío.");
        }

        try {
            String fileUrl = supabaseStorageService.subirArchivo(file);
            return ResponseEntity.ok(Collections.singletonMap("url", fileUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al subir el archivo a Supabase: " + e.getMessage());
        }
    }

    private User obtenerUsuarioDesdeToken(Authentication authentication) {
        if (authentication == null) return null;
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    private Administrador obtenerAdminDesdeToken(Authentication authentication) {
        if (authentication == null) return null;
        String email = authentication.getName();
        Administrador admin = administradorRepository.findByCorreoCorp(email).orElse(null);
        if (admin == null || !"admin".equalsIgnoreCase(admin.getRol())) {
            return null;
        }
        return admin;
    }
}
