package com.example.demo.controllers;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.models.Carrito;
import com.example.demo.models.User;
import com.example.demo.services.AuthService;
import com.example.demo.services.CarritoService;

/**
 * Controlador REST para la gestión del carrito de compras.
 * 
 * Ruta base: /api/carrito
 * CORS: permite peticiones desde Angular (localhost:4200).
 * 
 * TODOS los endpoints de este controlador requieren autenticación JWT
 * (configurado en SecurityConfig). El usuario se identifica mediante
 * el objeto {@link Principal} que Spring Security inyecta automáticamente
 * con el email extraído del token JWT.
 * 
 * Endpoints disponibles:
 * - GET    /api/carrito          → Obtener el carrito del usuario.
 * - POST   /api/carrito/agregar  → Agregar un producto al carrito.
 * - POST   /api/carrito/disminuir → Disminuir en 1 la cantidad de un producto.
 * - DELETE /api/carrito/eliminar → Eliminar un producto del carrito.
 * - DELETE /api/carrito/vaciar   → Vaciar completamente el carrito.
 */
@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    /** Servicio con la lógica del carrito (agregar, eliminar, vaciar, etc.). */
    @Autowired
    private CarritoService carritoService;

    /** Servicio de autenticación para resolver el email del usuario autenticado. */
    @Autowired
    private AuthService authService;

    /**
     * Retorna el carrito actual del usuario autenticado.
     * Si el usuario no tiene carrito, lo crea vacío automáticamente.
     *
     * @param principal usuario autenticado (inyectado por Spring Security con el email del JWT).
     * @return 200 con el carrito del usuario, o 401 si no está autenticado.
     */
    @GetMapping
    public ResponseEntity<Carrito> obtenerCarrito(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build(); // No autenticado
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        Carrito carrito = carritoService.obtenerOCrearCarrito(usuario.getId());
        return ResponseEntity.ok(carrito);
    }

    /**
     * Agrega un producto al carrito del usuario, o incrementa su cantidad si ya existe.
     *
     * Query params:
     * - idProducto: ID del producto a agregar.
     * - cantidad: número de unidades a agregar.
     *
     * @param idProducto ID del producto.
     * @param cantidad   cantidad de unidades.
     * @param principal  usuario autenticado.
     * @return 200 con el carrito actualizado, o 401 si no está autenticado.
     */
    @PostMapping("/agregar")
    public ResponseEntity<Carrito> agregarProducto(
            @RequestParam Integer idProducto,
            @RequestParam Integer cantidad,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        Carrito carrito = carritoService.agregarProductoAlCarrito(usuario.getId(), idProducto, cantidad);
        return ResponseEntity.ok(carrito);
    }

    /**
     * Disminuye en 1 la cantidad de un producto en el carrito.
     * Si la cantidad llega a 0, el producto se elimina automáticamente del carrito.
     *
     * Query params:
     * - idProducto: ID del producto a disminuir.
     *
     * @param idProducto ID del producto.
     * @param principal  usuario autenticado.
     * @return 200 con el carrito actualizado, o 401 si no está autenticado.
     */
    @PostMapping("/disminuir")
    public ResponseEntity<Carrito> disminuirProducto(
            @RequestParam Integer idProducto,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        Carrito carrito = carritoService.disminuirProductoDelCarrito(usuario.getId(), idProducto);
        return ResponseEntity.ok(carrito);
    }

    /**
     * Elimina completamente un producto del carrito (sin importar la cantidad).
     *
     * Query params:
     * - idProducto: ID del producto a eliminar.
     *
     * @param idProducto ID del producto a eliminar.
     * @param principal  usuario autenticado.
     * @return 200 con el carrito actualizado, o 401 si no está autenticado.
     */
    @DeleteMapping("/eliminar")
    public ResponseEntity<Carrito> eliminarProducto(
            @RequestParam Integer idProducto,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        Carrito carrito = carritoService.eliminarProductoDelCarrito(usuario.getId(), idProducto);
        return ResponseEntity.ok(carrito);
    }

    /**
     * Vacía completamente el carrito del usuario (elimina todos los productos).
     * Se invoca automáticamente después de un pago exitoso.
     *
     * @param principal usuario autenticado.
     * @return 200 con el carrito vacío, o 401 si no está autenticado.
     */
    @DeleteMapping("/vaciar")
    public ResponseEntity<Carrito> vaciarCarrito(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        Carrito carrito = carritoService.vaciarCarrito(usuario.getId());
        return ResponseEntity.ok(carrito);
    }
}
