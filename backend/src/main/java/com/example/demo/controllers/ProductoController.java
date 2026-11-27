package com.example.demo.controllers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ConteosDTO;
import com.example.demo.dto.FiltrosCatalogo;
import com.example.demo.dto.PageDTO;
import com.example.demo.dto.VarianteDTO;
import com.example.demo.models.Producto;
import com.example.demo.services.ProductoService;

/**
 * Controlador REST para el catálogo de productos.
 * 
 * Ruta base: /api/productos
 * CORS: permite peticiones desde Angular (localhost:4200).
 * 
 * Estos endpoints son PÚBLICOS (no requieren JWT) para que cualquier
 * visitante pueda ver el catálogo sin necesidad de registrarse.
 * 
 * Endpoints disponibles:
 * - GET /api/productos                → Listar todos los productos.
 * - GET /api/productos?nombre=xxx     → Buscar productos por nombre.
 * - GET /api/productos/paginado       → Catálogo paginado + filtros (30 por página).
 * - GET /api/productos/conteos        → Conteos por subcategoría/marca (menú lateral).
 * - GET /api/productos/categoria/{id} → Filtrar productos por categoría.
 * - GET /api/productos/{id}           → Un producto (cualquier talla).
 * - GET /api/productos/{id}/variantes → Tallas de la línea del producto (vacío si no tiene).
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    /** Servicio con la lógica de negocio de productos. */
    @Autowired
    private ProductoService productoService;

    /**
     * Lista todos los productos o filtra por nombre si se proporciona el parámetro.
     * 
     * Comportamiento:
     * - Sin parámetros → retorna todos los productos del catálogo.
     * - Con ?nombre=xxx → retorna productos cuyo nombre contiene "xxx" (búsqueda parcial).
     *
     * @param nombre parámetro de búsqueda opcional (case-insensitive recomendado).
     * @return lista de productos que coinciden con el criterio.
     */
    @GetMapping
    public List<Producto> listar(@RequestParam(required = false) String nombre) {
        if (nombre != null) {
            return productoService.buscarPorNombre(nombre);
        }
        return productoService.listarTodos();
    }

    /**
     * Catálogo paginado con filtros: PostgreSQL filtra PRIMERO sobre todos los
     * productos y recién después pagina de 30 en 30.
     *
     * Todos los filtros son opcionales; los que vienen vacíos no agregan
     * condición, así que la misma llamada sirve para todo el catálogo.
     *
     * Ejemplo: GET /api/productos/paginado?page=0&size=30&categoriaId=5&marcaId=2&precioMax=150
     *
     * @param page           índice de página, base 0 (default 0).
     * @param size           cantidad de productos por página (default 30).
     * @param categoriaId    categoría (null = todas).
     * @param subcategoriaId una o varias subcategorías (?subcategoriaId=1&subcategoriaId=2).
     * @param marcaId        una o varias marcas (?marcaId=3&marcaId=4).
     * @param search         texto de búsqueda (nombre, descripción o categoría).
     * @param enOferta       true = solo productos en oferta vigente.
     * @param precioMax      precio efectivo máximo (sin tope si no se envía).
     * @param orden          relevancia | precio_asc | precio_desc | nombre_asc.
     * @return DTO paginado con content, totalPages, totalElements, number y size.
     */
    @GetMapping("/paginado")
    public PageDTO<Producto> listarPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) List<Integer> subcategoriaId,
            @RequestParam(required = false) List<Integer> marcaId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enOferta,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(required = false) String orden) {

        FiltrosCatalogo filtros = new FiltrosCatalogo(
                categoriaId, subcategoriaId, marcaId, search, enOferta, precioMax, orden);
        return PageDTO.from(productoService.listarPaginado(filtros, page, size));
    }

    /**
     * Conteos reales del catálogo para el menú lateral: cuántos productos hay
     * en cada subcategoría y en cada marca (de la categoría elegida si se pasa
     * ?categoriaId=), más el precio más alto del catálogo para el slider.
     *
     * Se calcula con GROUP BY en la BD para que los contadores no dependan de
     * la página de 30 productos que tenga cargada el frontend.
     *
     * Ejemplo: GET /api/productos/conteos?categoriaId=5
     *
     * @param categoriaId categoría opcional para acotar los conteos de marcas.
     * @return subcategorias[], marcas[] y precioMaximo.
     */
    @GetMapping("/conteos")
    public ConteosDTO conteos(@RequestParam(required = false) Integer categoriaId) {
        return productoService.contarProductos(categoriaId);
    }

    /**
     * Filtra y retorna los productos de una categoría específica.
     * 
     * Ejemplo: GET /api/productos/categoria/1 → productos de la categoría ID 1.
     *
     * @param id ID de la categoría por la que filtrar.
     * @return lista de productos que pertenecen a la categoría indicada.
     */
    @GetMapping("/categoria/{id}")
    public List<Producto> filtrarPorCategoria(@PathVariable Integer id) {
        return productoService.filtrarPorCategoria(id);
    }

    /**
     * Un producto por ID. Sirve para el detalle: devuelve cualquier talla, aunque no dé la cara en el catálogo.
     *
     * @return 200 con el producto, o 404 si no existe.
     */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Producto> obtener(@PathVariable Integer id) {
        return productoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Tallas de la línea a la que pertenece el producto (de menor a mayor), con su precio y disponibilidad.
     * Lista vacía si el producto no tiene variantes.
     */
    @GetMapping("/{id:\\d+}/variantes")
    public List<VarianteDTO> variantes(@PathVariable Integer id) {
        return productoService.variantesDe(id);
    }
}
