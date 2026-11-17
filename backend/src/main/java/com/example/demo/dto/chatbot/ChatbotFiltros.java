package com.example.demo.dto.chatbot;

import lombok.Data;

/**
 * Filtros que el cliente va acumulando mientras avanza por el flujo guiado del chatbot.
 *
 * Es el único "estado" de la conversación guiada y vive en el frontend: en cada petición
 * el cliente envía los filtros que lleva y el backend responde con el siguiente paso.
 * Por eso el backend no necesita sesión.
 *
 * Los nombres (nombreCategoria, etc.) solo se usan para redactar mensajes amables y para
 * mostrar los filtros como "chips"; las consultas a la base de datos usan únicamente los ids.
 */
@Data
public class ChatbotFiltros {

    private Integer idCategoria;
    private String nombreCategoria;

    private Integer idSubcategoria;
    private String nombreSubcategoria;

    private Integer idMarca;
    private String nombreMarca;

    /** true = mostrar únicamente productos con oferta vigente. */
    private boolean soloOfertas;

    /** true = el cliente pidió ver todo sin elegir subcategoría. */
    private boolean omitirSubcategoria;

    /** true = el cliente pidió ver todo sin elegir marca. */
    private boolean omitirMarca;

    /** Página de resultados (0 = primeros productos, 1 = "ver más"...). */
    private int pagina;

    /** Indica si ya eligió algo que acote el catálogo (categoría, subcategoría, marca u ofertas). */
    public boolean tieneFiltros() {
        return idCategoria != null || idSubcategoria != null || idMarca != null || soloOfertas;
    }
}
