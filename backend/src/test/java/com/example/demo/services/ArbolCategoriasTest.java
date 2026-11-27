package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Pruebas con las categorías y subcategorías reales de BoticaAnita. */
class ArbolCategoriasTest {

    private static final String[][] CATALOGO = {
        {"BEBÉ", "Baño e higiene"}, {"BEBÉ", "Biberones y accesorios"}, {"BEBÉ", "Cremas y cuidado de la piel"},
        {"BEBÉ", "Leches y alimentación"}, {"BEBÉ", "Pañales y toallitas"},
        {"BELLEZA", "Cuidado corporal y manos"}, {"BELLEZA", "Cuidado facial"}, {"BELLEZA", "Maquillaje"},
        {"BELLEZA", "Perfumes y colonias"}, {"BELLEZA", "Uñas"},
        {"CUIDADO PERSONAL", "Afeitado y cuidado masculino"}, {"CUIDADO PERSONAL", "Cabello"},
        {"CUIDADO PERSONAL", "Desodorantes y talcos"}, {"CUIDADO PERSONAL", "Higiene bucal"},
        {"CUIDADO PERSONAL", "Higiene íntima y salud sexual"}, {"CUIDADO PERSONAL", "Jabones y baño"},
        {"CUIDADO PERSONAL", "Protección solar y repelentes"},
        {"EQUIPOS MÉDICOS", "Control de glucosa"}, {"EQUIPOS MÉDICOS", "Ortopedia y diagnóstico"},
        {"EQUIPOS MÉDICOS", "Presión, temperatura y oxígeno"}, {"EQUIPOS MÉDICOS", "Primeros auxilios"},
        {"MEDICAMENTOS", "Alergias"}, {"MEDICAMENTOS", "Antibióticos y antiparasitarios"},
        {"MEDICAMENTOS", "Crónicos y tratamientos especiales"}, {"MEDICAMENTOS", "Digestivo"},
        {"MEDICAMENTOS", "Dolor, fiebre e inflamación"}, {"MEDICAMENTOS", "Gripe, tos y garganta"},
        {"MEDICAMENTOS", "Ojos y oídos"}, {"MEDICAMENTOS", "Piel y heridas"},
        {"VITAMINAS / SUPLEMENTOS", "Probióticos"}, {"VITAMINAS / SUPLEMENTOS", "Vitaminas y minerales"},
    };

    private static ArbolCategorias arbol() {
        List<Object[]> filas = new ArrayList<>();
        for (String[] f : CATALOGO) {
            filas.add(new Object[] {f[0], f[1]});
        }
        return ArbolCategorias.desdeFilas(filas);
    }

    @Test
    void resuelveNombresExactosSinImportarMayusculasNiTildes() {
        assertEquals(List.of("digestivo"), arbol().resolver(List.of("Digestivo")));
        assertEquals(List.of("digestivo"), arbol().resolver(List.of("DIGESTIVO")));
        assertEquals(List.of("medicamentos"), arbol().resolver(List.of("medicamentos")));
        assertEquals(List.of("bebe"), arbol().resolver(List.of("BEBÉ")));
        assertEquals(List.of("vitaminas / suplementos"), arbol().resolver(List.of("Vitaminas / Suplementos")));
        assertEquals(List.of("dolor, fiebre e inflamacion"), arbol().resolver(List.of("Dolor, fiebre e inflamación")));
    }

    @Test
    void resuelveNombresAproximadosSiHayUnaSolaOpcionClara() {
        assertEquals(List.of("dolor, fiebre e inflamacion"), arbol().resolver(List.of("Dolor")));
        assertEquals(List.of("gripe, tos y garganta"), arbol().resolver(List.of("gripe y tos")));
        assertEquals(List.of("bebe"), arbol().resolver(List.of("bebe")));
    }

    @Test
    void descartaNombresInventadosOAmbiguos() {
        assertEquals(List.of(), arbol().resolver(List.of("Aspirinas")));
        assertEquals(List.of(), arbol().resolver(List.of("   ", "")));
        // "cuidado" coincide con demasiadas secciones distintas: no se adivina
        assertEquals(List.of(), arbol().resolver(List.of("cuidado")));
        assertEquals(List.of(), arbol().resolver(null));
    }

    @Test
    void ampliaUnaSubcategoriaASuCategoria() {
        assertEquals(List.of("medicamentos"), arbol().padresDe(List.of("digestivo")));
        assertEquals(List.of(), arbol().padresDe(List.of("medicamentos")));
        // Si ya se pidió la categoría, no es una ampliación
        assertEquals(List.of(), arbol().padresDe(List.of("digestivo", "medicamentos")));
    }

    @Test
    void unaBusquedaPorSintomaNuncaSaleDeMedicamentos() {
        List<String> salud = List.of("MEDICAMENTOS");
        // El caso real: el cliente buscaba por "cólico" y aparecieron biberones
        assertEquals(List.of("digestivo"), arbol().dentroDeSalud(List.of("digestivo", "biberones y accesorios"), salud));
        assertEquals(List.of("medicamentos"), arbol().dentroDeSalud(List.of("biberones y accesorios"), salud));
        assertEquals(List.of("medicamentos"), arbol().dentroDeSalud(List.of("bebe"), salud));
        // Sin categorías pedidas por SofIA: se busca en toda la categoría de salud
        assertEquals(List.of("medicamentos"), arbol().dentroDeSalud(List.of(), salud));
        assertEquals(List.of("gripe, tos y garganta"),
                arbol().dentroDeSalud(List.of("gripe, tos y garganta"), List.of("Medicamentos")));
    }

    @Test
    void siLaCategoriaDeSaludConfiguradaNoExisteNoSeRestringe() {
        assertEquals(List.of("bebe"), arbol().dentroDeSalud(List.of("bebe"), List.of("FARMACIA")));
        assertEquals(List.of("bebe"), ArbolCategorias.vacio().dentroDeSalud(List.of("bebe"), List.of("MEDICAMENTOS")));
    }

    @Test
    void generaElTextoDelPromptConTodasLasSecciones() {
        String texto = arbol().comoTexto();
        assertTrue(texto.contains("- BEBÉ: Baño e higiene; Biberones y accesorios;"));
        assertTrue(texto.contains("- MEDICAMENTOS: Alergias; Antibióticos y antiparasitarios;"));
        // Una línea por categoría: BEBÉ, BELLEZA, CUIDADO PERSONAL, EQUIPOS MÉDICOS, MEDICAMENTOS y VITAMINAS / SUPLEMENTOS
        assertEquals(6L, texto.lines().count());
    }

    @Test
    void ignoraFilasVaciasRepetidasYSubcategoriasNulas() {
        List<Object[]> filas = new ArrayList<>();
        filas.add(new Object[] {"BEBÉ", "Pañales y toallitas"});
        filas.add(new Object[] {"BEBÉ", "Pañales y toallitas"});
        filas.add(new Object[] {"BEBÉ", null});
        filas.add(new Object[] {null, "Huérfana"});
        filas.add(new Object[] {"  ", "Vacía"});
        filas.add(null);
        filas.add(new Object[] {"OFERTAS", null});
        ArbolCategorias arbol = ArbolCategorias.desdeFilas(filas);
        assertEquals("- BEBÉ: Pañales y toallitas\n- OFERTAS", arbol.comoTexto());
        assertTrue(ArbolCategorias.desdeFilas(null).estaVacio());
    }
}
