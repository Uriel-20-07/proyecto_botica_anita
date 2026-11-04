package com.example.demo.controllers;

import com.example.demo.dto.PromocionDTO;
import com.example.demo.services.PromocionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST de promociones.
 *
 * Ruta base: /api/promociones (público, igual que el catálogo).
 * - GET /api/promociones              → promociones disponibles (con stock) para el carrusel.
 * - GET /api/promociones?reglas=true   → todas las promociones, sin exigir stock (para
 *                                        calcular el descuento del carrito).
 */
@RestController
@RequestMapping("/api/promociones")
public class PromocionController {

    @Autowired
    private PromocionService promocionService;

    @GetMapping
    public List<PromocionDTO> listar(@RequestParam(defaultValue = "false") boolean reglas) {
        return reglas ? promocionService.listarReglas() : promocionService.listarDisponibles();
    }
}
