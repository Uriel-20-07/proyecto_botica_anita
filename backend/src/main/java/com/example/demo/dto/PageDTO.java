package com.example.demo.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * DTO paginado genérico para los endpoints del catálogo.
 *
 * Expone exactamente la estructura que consume el frontend:
 * content, totalPages, totalElements, number y size.
 *
 * Se devuelve un DTO (y no PageImpl directamente) para que el formato
 * de serialización sea estable entre versiones de Spring Data.
 *
 * @param <T> tipo de los elementos de la página (ej. Producto).
 */
public class PageDTO<T> {

    /** Elementos de la página actual. */
    private final List<T> content;

    /** Cantidad total de páginas. */
    private final int totalPages;

    /** Cantidad total de elementos (sin paginar). */
    private final long totalElements;

    /** Índice de la página actual (base 0). */
    private final int number;

    /** Tamaño de página solicitado (ej. 30). */
    private final int size;

    public PageDTO(List<T> content, int totalPages, long totalElements, int number, int size) {
        this.content = content;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.number = number;
        this.size = size;
    }

    /** Convierte un {@link Page} de Spring Data en este DTO. */
    public static <T> PageDTO<T> from(Page<T> page) {
        return new PageDTO<>(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }

    public List<T> getContent() {
        return content;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getNumber() {
        return number;
    }

    public int getSize() {
        return size;
    }
}
