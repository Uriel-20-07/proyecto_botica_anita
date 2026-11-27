package com.example.demo.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.PresentacionDTO;
import com.example.demo.models.Producto;
import com.example.demo.repositories.ProductoRepository;
import com.example.demo.services.PresentacionService;

/**
 * Presentaciones (blíster, caja...) de un producto, para el selector de la tienda.
 * Una lista vacía significa que el producto se vende normalmente, sin presentaciones.
 */
@RestController
@RequestMapping("/api/productos")
public class PresentacionController {

    private final ProductoRepository productoRepository;
    private final PresentacionService presentacionService;

    public PresentacionController(ProductoRepository productoRepository, PresentacionService presentacionService) {
        this.productoRepository = productoRepository;
        this.presentacionService = presentacionService;
    }

    @GetMapping("/{idProducto}/presentaciones")
    public ResponseEntity<List<PresentacionDTO>> presentaciones(@PathVariable Integer idProducto) {
        Producto producto = productoRepository.findById(idProducto).orElse(null);
        if (producto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(presentacionService.paraMostrar(producto));
    }
}
