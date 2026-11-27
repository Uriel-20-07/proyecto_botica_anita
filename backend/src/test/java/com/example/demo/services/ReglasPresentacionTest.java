package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.demo.services.ReglasPresentacion.LimiteAgregar;

/** Reglas de presentaciones (blíster / caja) con stock compartido en tabletas. */
class ReglasPresentacionTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    // ---------- productos SIN presentaciones: igual que antes ----------

    @Test
    void sinPresentacionesElLimiteEsElMenorEntreDiezYElStock() {
        // Antes: min(10 - enCarrito, stock - enCarrito)
        LimiteAgregar l = ReglasPresentacion.limiteParaAgregar(25, 1, 0, 0);
        assertEquals(10, l.maxAdicional());
        assertTrue(l.topeEsElLimite());

        l = ReglasPresentacion.limiteParaAgregar(4, 1, 1, 0);
        assertEquals(3, l.maxAdicional());
        assertFalse(l.topeEsElLimite());

        l = ReglasPresentacion.limiteParaAgregar(5, 1, 10, 0);
        assertEquals(0, l.maxAdicional());
        assertEquals(0, l.limitePorProducto());
    }

    @Test
    void sinStockNoSePuedeAgregarNada() {
        assertEquals(0, ReglasPresentacion.limiteParaAgregar(0, 1, 0, 0).maxAdicional());
        // Si el stock bajó por debajo de lo que ya hay en el carrito, el máximo es 0 (nunca negativo)
        LimiteAgregar l = ReglasPresentacion.limiteParaAgregar(2, 1, 5, 0);
        assertEquals(0, l.maxAdicional());
        assertEquals(-3, l.limitePorStock());
    }

    // ---------- presentaciones con stock compartido ----------

    @Test
    void laCajaDescuentaTodasSusTabletas() {
        // 250 tabletas: caja x100 -> alcanzan 2 cajas
        LimiteAgregar l = ReglasPresentacion.limiteParaAgregar(250, 100, 0, 0);
        assertEquals(2, l.maxAdicional());
        assertFalse(l.topeEsElLimite());
    }

    @Test
    void elBlisterYLaCajaCompartenElMismoStock() {
        // 150 tabletas. Ya hay 1 caja (100) en otra línea -> quedan 50 -> 5 blísteres de 10
        LimiteAgregar l = ReglasPresentacion.limiteParaAgregar(150, 10, 0, 100);
        assertEquals(5, l.maxAdicional());
        // Con 5 blísteres en el carrito (50 tabletas) quedan 100: todavía cabe UNA caja
        assertEquals(1, ReglasPresentacion.limiteParaAgregar(150, 100, 0, 50).maxAdicional());
        // Con 6 blísteres (60 tabletas) quedan 90: ya no cabe ninguna caja
        assertEquals(0, ReglasPresentacion.limiteParaAgregar(150, 100, 0, 60).maxAdicional());
        // Con una caja ya en el carrito (quedan 50), no cabe otra caja
        assertEquals(0, ReglasPresentacion.limiteParaAgregar(150, 100, 1, 0).maxAdicional());
    }

    @Test
    void conMuchoStockElTopeEsElLimiteDeDiezPorPresentacion() {
        LimiteAgregar l = ReglasPresentacion.limiteParaAgregar(10_000, 100, 3, 0);
        assertEquals(7, l.maxAdicional());
        assertTrue(l.topeEsElLimite());
    }

    @Test
    void unidadesBaseDeUnaLinea() {
        assertEquals(200, ReglasPresentacion.unidadesDeLinea(2, 100));
        assertEquals(3, ReglasPresentacion.unidadesDeLinea(3, null)); // producto sin presentaciones
        assertEquals(3, ReglasPresentacion.unidadesDeLinea(3, 1));
    }

    @Test
    void disponibilidadYMaximoPorPedido() {
        assertTrue(ReglasPresentacion.disponible(100, 100));
        assertFalse(ReglasPresentacion.disponible(99, 100));
        assertEquals(9, ReglasPresentacion.maxPorPedido(99, 10));   // 9 blísteres, la caja ya no alcanza
        assertEquals(0, ReglasPresentacion.maxPorPedido(99, 100));
        assertEquals(10, ReglasPresentacion.maxPorPedido(9900, 100)); // 99 cajas, pero el tope es 10
        assertEquals(0, ReglasPresentacion.maxPorPedido(-5, 10));
    }

    // ---------- precios ----------

    @Test
    void laCajaUsaElPrecioDelProductoYSigueSusCambios() {
        // precio_venta NULL en la presentación: es el precio efectivo del producto
        assertEquals(d("12.50"), ReglasPresentacion.precioUnitario(null, d("12.50"), d("12.50")));
        // Si el producto entra en oferta, la caja también
        assertEquals(d("10.00"), ReglasPresentacion.precioUnitario(null, d("12.50"), d("10.00")));
    }

    @Test
    void elBlisterConservaSuPrecioSinOferta() {
        assertEquals(d("1.50"), ReglasPresentacion.precioUnitario(d("1.50"), d("12.50"), d("12.50")));
        assertEquals(d("1.50"), ReglasPresentacion.precioUnitario(d("1.50"), d("12.50"), null));
    }

    @Test
    void unaOfertaDelProductoAlcanzaAlBlisterConElMismoPorcentaje() {
        // Producto 12.50 -> 10.00 (20 % de descuento): el blíster de 1.50 pasa a 1.20
        assertEquals(d("1.20"), ReglasPresentacion.precioUnitario(d("1.50"), d("12.50"), d("10.00")));
        // Redondeo a 2 decimales
        assertEquals(d("5.00"), ReglasPresentacion.precioUnitario(d("5.20"), d("9.00"), d("8.65")));
    }

    @Test
    void sinPrecioDeVentaValidoNoHayDivisionPorCero() {
        assertEquals(d("1.50"), ReglasPresentacion.precioUnitario(d("1.50"), d("0.00"), d("0.00")));
        assertEquals(d("1.50"), ReglasPresentacion.precioUnitario(d("1.50"), null, null));
    }

    @Test
    void precioRegularEsElDeLaPresentacionOElDelProducto() {
        assertEquals(d("1.50"), ReglasPresentacion.precioRegular(d("1.50"), d("12.50")));
        assertEquals(d("12.50"), ReglasPresentacion.precioRegular(null, d("12.50")));
    }
}
