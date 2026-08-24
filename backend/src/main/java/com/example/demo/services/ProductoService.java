package com.example.demo.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.models.Producto;
import com.example.demo.repositories.ProductoRepository;

import jakarta.annotation.PostConstruct;

/**
 * Servicio de Productos actualizado para sincronizar con Algolia.
 *
 * Cambios respecto al original:
 * - Se inyecta AlgoliaService.
 * - Al guardar o eliminar un producto, se actualiza Algolia automáticamente.
 * - @PostConstruct indexarTodosEnAlgolia() llena el índice al arrancar el servidor.
 */
@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private AlgoliaService algoliaService;

    // ─── Métodos originales (sin cambios) ────────────────────────────────────

    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    public List<Producto> buscarPorNombre(String nombre) {
        return productoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    public List<Producto> filtrarPorCategoria(Integer idCategoria) {
        return productoRepository.findByCategoria_IdCategoria(idCategoria);
    }

    // ─── Métodos nuevos con sincronización a Algolia ──────────────────────────

    /**
     * Guarda o actualiza un producto en la BD y lo indexa en Algolia.
     *
     * @param producto producto a guardar.
     * @return el producto guardado (con ID generado si es nuevo).
     */
    public Producto guardar(Producto producto) {
        Producto guardado = productoRepository.save(producto);
        algoliaService.indexarProducto(guardado); // Sincronizar con Algolia
        return guardado;
    }

    /**
     * Elimina un producto por ID de la BD y lo borra del índice de Algolia.
     *
     * @param id ID del producto a eliminar.
     */
    public void eliminar(Integer id) {
        productoRepository.deleteById(id);
        algoliaService.eliminarProducto(id); // Eliminar de Algolia
    }

    /**
     * Al arrancar el servidor, indexa TODOS los productos existentes en Algolia.
     * Esto asegura que el índice esté siempre sincronizado con la BD.
     *
     * Si el índice ya tiene datos, Algolia los sobreescribe (updateObject).
     * Ejecuta una sola vez por inicio de aplicación.
     */
    @PostConstruct
    public void indexarTodosEnAlgolia() {
        try {
            List<Producto> todos = productoRepository.findAll();
            if (!todos.isEmpty()) {
                algoliaService.indexarTodos(todos);
                System.out.println("[Algolia] Indexación inicial: " + todos.size() + " productos enviados.");
            }
        } catch (Exception e) {
            System.err.println("[Algolia] Error en indexación inicial: " + e.getMessage());
        }
    }
}
