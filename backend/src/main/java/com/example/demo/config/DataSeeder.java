package com.example.demo.config;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Componente de inicialización de datos (seed) que se ejecuta automáticamente
 * al arrancar la aplicación.
 * 
 * Implementa {@link CommandLineRunner}, lo que permite ejecutar código
 * personalizado justo después de que el contexto de Spring esté completamente
 * iniciado.
 * 
 * Propósito: poblar la base de datos con datos iniciales de prueba si las
 * tablas están vacías. Esto evita duplicar datos en reinicios sucesivos.
 * 
 * Orden de inserción:
 *  1. Administradores (admin general + vendedor).
 *  2. Cliente de prueba.
 *  3. Categorías de productos.
 *  4. Productos del catálogo.
 *  5. Pedidos históricos simulados (para el dashboard de ventas).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    /** Repositorio para gestionar administradores del sistema. */
    @Autowired
    private AdministradorRepository administradorRepository;

    /** Repositorio para gestionar usuarios clientes. */
    @Autowired
    private UserRepository userRepository;

    /** Repositorio para gestionar categorías de productos. */
    @Autowired
    private CategoriaRepository categoriaRepository;

    /** Repositorio para gestionar productos del catálogo. */
    @Autowired
    private ProductoRepository productoRepository;

    /** Repositorio para gestionar pedidos (órdenes de compra). */
    @Autowired
    private PedidoRepository pedidoRepository;

    /** Repositorio para gestionar los items de cada pedido. */
    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    /** Fuente de datos JDBC para operaciones directas en base de datos. */
    @Autowired
    private DataSource dataSource;

    /** Encriptador de contraseñas usando el algoritmo BCrypt (hash seguro). */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Método principal del seeder. Se ejecuta una sola vez al arrancar la app.
     * Verifica si cada tabla ya tiene datos antes de insertar para evitar duplicados.
     *
     * @param args argumentos de línea de comandos (no se usan).
     * @throws Exception si ocurre algún error durante la inserción.
     */
    @Override
    public void run(String... args) throws Exception {
        // 0. Crear tabla combos si no existe (antes de que la use Hibernate)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS combos (" +
                "    id_combo SERIAL PRIMARY KEY," +
                "    producto_principal_id INTEGER NOT NULL REFERENCES productos(id_producto)," +
                "    producto_asociado_id INTEGER NOT NULL REFERENCES productos(id_producto)," +
                "    descuento INTEGER DEFAULT 10," +
                "    descripcion VARCHAR(500) NOT NULL," +
                "    activo BOOLEAN NOT NULL DEFAULT TRUE" +
                ")"
            );
            System.out.println("DataSeeder: Tabla 'combos' verificada/creada exitosamente.");
        } catch (Exception e) {
            System.err.println("DataSeeder: Error al crear tabla combos: " + e.getMessage());
        }

        // 1. Crear administrador general si no existe
        if (administradorRepository.findByCorreoCorp("admin@correo_corp.com").isEmpty()) {
            Administrador admin = new Administrador();
            admin.setNombre("Administrador General");
            admin.setCorreoCorp("admin@correo_corp.com");
            admin.setPassword(passwordEncoder.encode("admin123")); // Contraseña encriptada con BCrypt
            admin.setRol("admin");
            administradorRepository.save(admin);
            System.out.println("DataSeeder: Administrador General creado.");
        }

        // 1b. Crear vendedor si no existe
        if (administradorRepository.findByCorreoCorp("vendedor@correo_corp.com").isEmpty()) {
            Administrador vendedor = new Administrador();
            vendedor.setNombre("Vendedor Principal");
            vendedor.setCorreoCorp("vendedor@correo_corp.com");
            vendedor.setPassword(passwordEncoder.encode("vendedor123")); // Contraseña encriptada
            vendedor.setRol("vendedor");
            administradorRepository.save(vendedor);
            System.out.println("DataSeeder: Vendedor Principal creado.");
        }

        // 2. Crear cliente de prueba si no existen usuarios
        User cliente = null;
        if (userRepository.count() == 0) {
            cliente = new User();
            cliente.setEmail("cliente@gmail.com");
            cliente.setPassword(passwordEncoder.encode("cliente123"));
            cliente.setNombre("Juan");
            cliente.setApellido("Perez");
            cliente.setTelefono("987654321");
            cliente.setActivo(true);
            cliente.setEmailVerificado(true);
            // Se crea con fecha de registro 15 días atrás para simular un usuario antiguo
            cliente.setFechaRegistro(LocalDateTime.now().minusDays(15));
            cliente = userRepository.save(cliente);
            System.out.println("DataSeeder: Cliente común creado con éxito.");
        } else {
            // Si ya existen usuarios, toma el primero para asociarlo a los pedidos de prueba
            cliente = userRepository.findAll().get(0);
        }

        // 3. Crear categorías si no existen
        List<Categoria> categorias = new ArrayList<>();
        if (categoriaRepository.count() == 0) {
            String[] nombresCat = {"Analgésicos", "Antibióticos", "Vitaminas", "Cuidado Personal"};
            for (String nombre : nombresCat) {
                Categoria cat = new Categoria();
                cat.setNombre(nombre);
                categorias.add(categoriaRepository.save(cat));
            }
            System.out.println("DataSeeder: Categorías creadas.");
        } else {
            categorias = categoriaRepository.findAll();
        }

        // 4. Crear productos del catálogo si no existen y hay categorías disponibles
        List<Producto> productos = new ArrayList<>();
        if (productoRepository.count() == 0 && !categorias.isEmpty()) {
            // Producto 1: Paracetamol (categoría Analgésicos)
            Producto p1 = new Producto();
            p1.setNombre("Paracetamol 500mg");
            p1.setDescripcion("Alivio del dolor y la fiebre");
            p1.setPrecioVenta(BigDecimal.valueOf(5.50));
            p1.setStock(120);
            p1.setCategoria(categorias.get(0));
            p1.setImgUrl("https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300");
            productos.add(productoRepository.save(p1));

            // Producto 2: Ibuprofeno (categoría Analgésicos)
            Producto p2 = new Producto();
            p2.setNombre("Ibuprofeno 400mg");
            p2.setDescripcion("Antiinflamatorio y analgésico");
            p2.setPrecioVenta(BigDecimal.valueOf(6.20));
            p2.setStock(85);
            p2.setCategoria(categorias.get(0));
            p2.setImgUrl("https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300");
            productos.add(productoRepository.save(p2));

            // Producto 3: Amoxicilina (categoría Antibióticos)
            Producto p3 = new Producto();
            p3.setNombre("Amoxicilina 500mg");
            p3.setDescripcion("Antibiótico de amplio espectro");
            p3.setPrecioVenta(BigDecimal.valueOf(15.00));
            p3.setStock(50);
            p3.setCategoria(categorias.get(1));
            p3.setImgUrl("https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300");
            productos.add(productoRepository.save(p3));

            // Producto 4: Vitamina C (categoría Vitaminas)
            Producto p4 = new Producto();
            p4.setNombre("Vitamina C 1g");
            p4.setDescripcion("Suplemento vitamínico antioxidante");
            p4.setPrecioVenta(BigDecimal.valueOf(12.50));
            p4.setStock(200);
            p4.setCategoria(categorias.get(2));
            p4.setImgUrl("https://images.unsplash.com/photo-1616679911721-eff6eec18fcd?w=300");
            productos.add(productoRepository.save(p4));

            // Producto 5: Jabón Germicida (categoría Cuidado Personal)
            Producto p5 = new Producto();
            p5.setNombre("Jabón Germicida");
            p5.setDescripcion("Jabón antibacterial para cuidado personal");
            p5.setPrecioVenta(BigDecimal.valueOf(8.90));
            p5.setStock(60);
            p5.setCategoria(categorias.get(3));
            p5.setImgUrl("https://images.unsplash.com/photo-1607006342411-92427f3f47e9?w=300");
            productos.add(productoRepository.save(p5));

            System.out.println("DataSeeder: Productos creados.");
        } else {
            productos = productoRepository.findAll();
        }

        // 5. Crear pedidos históricos de prueba para el dashboard de ventas (últimos 7 días)
        if (pedidoRepository.count() == 0 && cliente != null && !productos.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();

            // Cada llamada crea un pedido con los pares (producto, cantidad) indicados
            crearPedidoHistorico(cliente, now.minusDays(6), BigDecimal.valueOf(22.00), productos.get(0), 4);
            crearPedidoHistorico(cliente, now.minusDays(5), BigDecimal.valueOf(30.00), productos.get(2), 2);
            crearPedidoHistorico(cliente, now.minusDays(4), BigDecimal.valueOf(24.80), productos.get(1), 4);
            crearPedidoHistorico(cliente, now.minusDays(3), BigDecimal.valueOf(50.00), productos.get(3), 4);
            crearPedidoHistorico(cliente, now.minusDays(2), BigDecimal.valueOf(37.80), productos.get(4), 2, productos.get(0), 4);
            crearPedidoHistorico(cliente, now.minusDays(1), BigDecimal.valueOf(75.00), productos.get(2), 5);
            crearPedidoHistorico(cliente, now, BigDecimal.valueOf(42.50), productos.get(3), 2, productos.get(0), 2, productos.get(1), 1);

            System.out.println("DataSeeder: Ventas históricas simuladas creadas.");
        }
    }

    /**
     * Crea un pedido histórico con uno o varios productos (pares producto-cantidad).
     * 
     * Utiliza varargs para aceptar un número variable de pares (Producto, Integer),
     * lo que permite crear pedidos con múltiples líneas de detalle en una sola llamada.
     * 
     * Ejemplo de uso:
     *   crearPedidoHistorico(cliente, fecha, total, prod1, 2, prod2, 3)
     *   → Pedido con 2 unidades de prod1 y 3 unidades de prod2.
     *
     * @param usuario        usuario al que se asocia el pedido.
     * @param fecha          fecha y hora del pedido (histórica simulada).
     * @param total          total monetario del pedido.
     * @param prodCantPairs  pares alternados de (Producto, Integer cantidad).
     */
    private void crearPedidoHistorico(User usuario, LocalDateTime fecha, BigDecimal total, Object... prodCantPairs) {
        // Crear y persistir la cabecera del pedido
        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setFecha(fecha);
        pedido.setEstado("COMPLETADO");
        pedido.setTotal(total);
        pedido.setDireccionEnvio("Tienda Jerusalén - Calle Jerusalén 408, Arequipa (Cercado)");
        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        // Iterar los pares (Producto, Integer) de forma alternada (índice par = producto, impar = cantidad)
        for (int i = 0; i < prodCantPairs.length; i += 2) {
            Producto prod = (Producto) prodCantPairs[i];
            Integer cant = (Integer) prodCantPairs[i + 1];

            // Crear el detalle de pedido con el precio histórico al momento de la venta
            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedidoGuardado);
            detalle.setProducto(prod);
            detalle.setCantidad(cant);
            detalle.setPrecioHistorico(prod.getPrecioVenta()); // Precio en el momento de la compra
            detallePedidoRepository.save(detalle);
        }
    }
}
