package com.example.demo.controllers;

import com.example.demo.models.Favorito;
import com.example.demo.models.Producto;
import com.example.demo.models.User;
import com.example.demo.repositories.FavoritoRepository;
import com.example.demo.repositories.ProductoRepository;
import com.example.demo.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favoritos")
public class FavoritoController {

    @Autowired
    private FavoritoRepository favoritoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private AuthService authService;

    /**
     * Lista todos los productos favoritos del usuario autenticado.
     */
    @GetMapping
    public ResponseEntity<?> listarFavoritos(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Usuario no autenticado");
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        List<Favorito> favoritos = favoritoRepository.findByUsuarioId(usuario.getId());
        List<Producto> productos = favoritos.stream()
                .map(Favorito::getProducto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productos);
    }

    /**
     * Retorna únicamente los IDs de los productos favoritos del usuario autenticado.
     * Útil para resaltar los corazones en el catálogo de forma rápida.
     */
    @GetMapping("/ids")
    public ResponseEntity<?> obtenerIdsFavoritos(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Usuario no autenticado");
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        List<Favorito> favoritos = favoritoRepository.findByUsuarioId(usuario.getId());
        List<Integer> ids = favoritos.stream()
                .map(f -> f.getProducto().getIdProducto())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ids);
    }

    /**
     * Agrega o elimina un producto de la lista de favoritos (Toggle).
     * Retorna true si el producto fue agregado, o false si fue eliminado.
     */
    @PostMapping("/toggle")
    public ResponseEntity<?> toggleFavorito(@RequestParam Integer idProducto, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Usuario no autenticado");
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        Optional<Producto> productoOpt = productoRepository.findById(idProducto);

        if (productoOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("El producto no existe");
        }

        Producto producto = productoOpt.get();
        Optional<Favorito> favoritoOpt = favoritoRepository.findByUsuarioIdAndProductoIdProducto(usuario.getId(), idProducto);

        if (favoritoOpt.isPresent()) {
            favoritoRepository.delete(favoritoOpt.get());
            return ResponseEntity.ok(false); // Retorna false si fue eliminado de favoritos
        } else {
            Favorito nuevoFavorito = new Favorito();
            nuevoFavorito.setUsuario(usuario);
            nuevoFavorito.setProducto(producto);
            favoritoRepository.save(nuevoFavorito);
            return ResponseEntity.ok(true); // Retorna true si fue agregado a favoritos
        }
    }
}
