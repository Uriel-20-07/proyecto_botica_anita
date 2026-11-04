package com.example.demo.services;

import com.example.demo.dto.PromocionDTO;
import com.example.demo.dto.PromocionDTO.ItemPromo;
import com.example.demo.models.Carrito;
import com.example.demo.models.DetalleCarrito;
import com.example.demo.models.Producto;
import com.example.demo.repositories.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Promociones de la botica.
 *
 * El descuento NO se guarda: se DERIVA siempre del contenido actual del carrito.
 * Por eso, si el cliente quita un producto (o baja su cantidad) y ya no se cumple
 * la regla, la promoción deja de aplicarse sola.
 *
 * Los productos de cada promoción se buscan por nombre en el catálogo. Para cambiar
 * qué producto entra en una promoción, o los porcentajes, solo se edita DEFINICIONES.
 */
@Service
public class PromocionService {

    private static final Logger log = LoggerFactory.getLogger(PromocionService.class);

    @Autowired
    private ProductoRepository productoRepository;

    /** Producto buscado por nombre (ya normalizado: minúsculas y sin tildes) y cantidad requerida. */
    private record Regla(Pattern incluye, Pattern excluye, int cantidad) {
        static Regla de(String incluye, int cantidad) {
            return new Regla(Pattern.compile(incluye), null, cantidad);
        }

        static Regla de(String incluye, String excluye, int cantidad) {
            return new Regla(Pattern.compile(incluye), Pattern.compile(excluye), cantidad);
        }
    }

    private record Definicion(String id, String titulo, String etiqueta, String descripcion,
            int unidadesGratis, double porcentaje, List<Regla> reglas) {
    }

    // El orden importa: es el orden del carrusel y, si dos promociones podrían usar el mismo
    // producto, se lo queda la primera (un producto nunca está en dos promociones a la vez).
    private static final List<Definicion> DEFINICIONES = List.of(
            // Pañales Pampers: lleva 3, paga 2.
            new Definicion(
                    "pampers-3x2", "Pañales Pampers 3x2", "Cuidado del bebé",
                    "Lleva 3 paquetes y paga solo 2.",
                    1, 0,
                    List.of(Regla.de("pampers", "toallit|wipe|humed", 3))),

            // Shampoo Johnson's: 15% menos al comprar 3.
            new Definicion(
                    "johnson-shampoo-3", "Shampoo Johnson's baby", "Baño del bebé",
                    "Compra 3 shampoos y llévalos con 15% de descuento.",
                    0, 15,
                    List.of(Regla.de("(johnson|jhonson).*(shampoo|champu)|(shampoo|champu).*(johnson|jhonson)", 3))),

            // Combo Omega 3 + Vitamina D + Vitamina E: 10% menos llevando los tres.
            new Definicion(
                    "omega-vitaminas", "Combo Omega 3 + Vitamina D + Vitamina E", "Vitaminas y suplementos",
                    "Llévate los tres juntos y ahorra 10%.",
                    0, 10,
                    List.of(
                            Regla.de("omega", 1),
                            Regla.de("\\bvitamina\\s*d\\d?\\b", 1),
                            Regla.de("\\bvitamina\\s*e\\b", 1))),

            // Botiquín básico: Paracetamol + Ibuprofeno con 10% menos.
            new Definicion(
                    "botiquin-basico", "Botiquín básico", "Medicamentos",
                    "Paracetamol + Ibuprofeno juntos con 10% de descuento.",
                    0, 10,
                    List.of(
                            Regla.de("paracetamol", 1),
                            Regla.de("ibuprofeno", 1))),

            // Higiene bucal: pasta dental Colgate + cepillo Oral-B con 12% menos.
            new Definicion(
                    "sonrisa-completa", "Sonrisa completa", "Higiene bucal",
                    "Pasta dental Colgate + cepillo Oral-B con 12% de descuento.",
                    0, 12,
                    List.of(
                            Regla.de("colgate", "cepillo", 1),
                            Regla.de("cepillo.*oral|oral.*cepillo", 1))),

            // Jabón Dove: lleva 3, paga 2.
            new Definicion(
                    "dove-3x2", "Jabón Dove 3x2", "Higiene personal",
                    "Lleva 3 jabones y paga solo 2.",
                    1, 0,
                    List.of(Regla.de("jabon.*dove|dove.*jabon", 3))),

            // Dúo Nivea: crema facial + crema corporal con 15% menos.
            new Definicion(
                    "nivea-duo", "Dúo Nivea facial + corporal", "Cuidado de la piel",
                    "Crema facial y crema corporal Nivea con 15% de descuento.",
                    0, 15,
                    List.of(
                            Regla.de("facial.*nivea|nivea.*facial", 1),
                            Regla.de("corporal.*nivea|nivea.*corporal", 1))),

            // Pack Huggies: pañales + toallitas húmedas con 10% menos.
            new Definicion(
                    "huggies-pack", "Pack Huggies para bebé", "Cuidado del bebé",
                    "Pañales + toallitas húmedas Huggies con 10% de descuento.",
                    0, 10,
                    List.of(
                            Regla.de("huggies", "toallit|wipe|humed", 1),
                            Regla.de("toallit.*huggies|huggies.*toallit", 1))),

            // Shampoo Pantene: 15% menos al llevar 2.
            new Definicion(
                    "pantene-2", "Shampoo Pantene Pro-V", "Cuidado capilar",
                    "Lleva 2 shampoos Pantene y ahorra 15%.",
                    0, 15,
                    List.of(Regla.de("pantene", 2))));

    // ───────────────────────── Catálogo de promociones ─────────────────────────

    /**
     * Promociones disponibles ahora mismo: solo aparecen las que tienen TODOS sus
     * productos en el catálogo, con stock suficiente y sin estar vencidos.
     */
    public List<PromocionDTO> listarDisponibles() {
        return resolver(true);
    }

    /**
     * Igual que {@link #listarDisponibles()} pero sin exigir stock. La usa el carrito del
     * frontend para calcular el descuento con las mismas reglas que aplica el pago.
     */
    public List<PromocionDTO> listarReglas() {
        return resolver(false);
    }

    private List<PromocionDTO> resolver(boolean exigirStock) {
        List<Producto> productos = productoRepository.findAll();
        List<PromocionDTO> resultado = new ArrayList<>();
        // Un producto solo puede estar en UNA promoción: si no, se le sumarían dos descuentos.
        Set<Integer> usados = new HashSet<>();

        for (Definicion def : DEFINICIONES) {
            List<ItemPromo> items = new ArrayList<>();
            Set<Integer> idsDeEstaPromo = new HashSet<>();
            boolean completa = true;
            boolean hayStock = true;

            for (Regla regla : def.reglas()) {
                // El producto se elige SIN mirar el stock, así el carrusel y el cálculo del
                // carrito/pago siempre hablan del mismo producto.
                Optional<Producto> encontrado = productos.stream()
                        .filter(p -> coincide(p, regla))
                        .filter(p -> !estaVencido(p))
                        .filter(p -> precioEfectivo(p) != null)
                        .filter(p -> !usados.contains(p.getIdProducto()) && !idsDeEstaPromo.contains(p.getIdProducto()))
                        .min(Comparator.comparing(Producto::getIdProducto));

                if (encontrado.isEmpty()) {
                    completa = false;
                    break;
                }
                Producto p = encontrado.get();
                idsDeEstaPromo.add(p.getIdProducto());
                if (stock(p) < regla.cantidad()) {
                    hayStock = false;
                }
                items.add(new ItemPromo(p.getIdProducto(), p.getNombre(), p.getImgUrl(),
                        precioEfectivo(p), regla.cantidad()));
            }

            if (!completa) {
                if (exigirStock) {
                    log.warn("Promoción '{}' omitida: no se encontró en el catálogo un producto vigente "
                            + "para todos sus ítems (revisa los nombres en PromocionService).", def.id());
                }
                continue;
            }
            usados.addAll(idsDeEstaPromo);
            if (exigirStock && !hayStock) {
                continue; // existe, pero no alcanza el stock para ofrecerla
            }

            BigDecimal normal = BigDecimal.ZERO;
            for (ItemPromo it : items) {
                normal = normal.add(it.precioUnitario().multiply(BigDecimal.valueOf(it.cantidad())));
            }
            BigDecimal ahorro = ahorroPorConjunto(def.unidadesGratis(), def.porcentaje(), items);
            resultado.add(new PromocionDTO(def.id(), def.titulo(), def.etiqueta(), def.descripcion(),
                    def.unidadesGratis(), def.porcentaje(), items,
                    normal.setScale(2, RoundingMode.HALF_UP),
                    normal.subtract(ahorro).setScale(2, RoundingMode.HALF_UP),
                    ahorro));
        }
        return resultado;
    }

    // ───────────────────────── Descuento del carrito ─────────────────────────

    /** Promoción que se está aplicando en el carrito y cuánto descuenta. */
    public record Aplicada(String id, String titulo, int veces, BigDecimal descuento) {
    }

    public record ResultadoDescuento(BigDecimal total, List<Aplicada> aplicadas) {
    }

    /**
     * Calcula cuánto se descuenta por promociones según lo que hay HOY en el carrito.
     * Cada vez que se completa el conjunto (3 pañales, el trío de vitaminas, etc.)
     * se aplica el descuento; lo que sobra se cobra a precio normal.
     */
    public ResultadoDescuento calcularDescuento(Carrito carrito) {
        Map<Integer, Integer> cantidades = new HashMap<>();
        for (DetalleCarrito d : carrito.getDetalles()) {
            if (d.getProducto() == null || d.getCantidad() == null) {
                continue;
            }
            cantidades.merge(d.getProducto().getIdProducto(), d.getCantidad(), Integer::sum);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<Aplicada> aplicadas = new ArrayList<>();

        for (PromocionDTO promo : resolver(false)) {
            int veces = Integer.MAX_VALUE;
            for (ItemPromo it : promo.items()) {
                veces = Math.min(veces, cantidades.getOrDefault(it.idProducto(), 0) / it.cantidad());
            }
            if (veces <= 0) {
                continue; // falta algún producto o no alcanza la cantidad: la promo se pierde
            }
            BigDecimal descuento = promo.ahorro().multiply(BigDecimal.valueOf(veces));
            total = total.add(descuento);
            aplicadas.add(new Aplicada(promo.id(), promo.titulo(), veces, descuento));
        }
        return new ResultadoDescuento(total, aplicadas);
    }

    // ───────────────────────── Utilidades ─────────────────────────

    /** Ahorro por UNA vez la promoción (redondeado a 2 decimales). */
    private BigDecimal ahorroPorConjunto(int unidadesGratis, double porcentaje, List<ItemPromo> items) {
        if (unidadesGratis > 0) {
            return items.get(0).precioUnitario()
                    .multiply(BigDecimal.valueOf(unidadesGratis))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal normal = BigDecimal.ZERO;
        for (ItemPromo it : items) {
            normal = normal.add(it.precioUnitario().multiply(BigDecimal.valueOf(it.cantidad())));
        }
        return normal.multiply(BigDecimal.valueOf(porcentaje))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private boolean coincide(Producto p, Regla regla) {
        String nombre = normalizar(p.getNombre());
        if (!regla.incluye().matcher(nombre).find()) {
            return false;
        }
        return regla.excluye() == null || !regla.excluye().matcher(nombre).find();
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private boolean estaVencido(Producto p) {
        return p.getFechaCaducidad() != null && p.getFechaCaducidad().isBefore(LocalDate.now());
    }

    private int stock(Producto p) {
        return p.getStock() != null ? p.getStock() : 0;
    }

    private BigDecimal precioEfectivo(Producto p) {
        return p.getPrecioConDescuento() != null ? p.getPrecioConDescuento() : p.getPrecioVenta();
    }
}
