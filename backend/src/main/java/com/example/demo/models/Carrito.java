package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.dto.ItemExpiradoDTO;

/**
 * Entidad JPA que representa el carrito de compras de un usuario.
 * 
 * Tabla en BD: "carrito"
 * 
 * Relaciones:
 * - OneToOne con User: cada usuario tiene como máximo UN carrito.
 * - OneToMany con DetalleCarrito: el carrito contiene una lista de líneas
 *   (cada línea = un producto con su cantidad).
 * 
 * La relación con DetalleCarrito usa:
 * - CascadeType.ALL: las operaciones (save, delete) se propagan a los detalles.
 * - orphanRemoval = true: si un detalle se quita de la lista, se elimina de la BD.
 */
@Entity
@Table(name = "carrito")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Carrito {

    /** Identificador único del carrito (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_carrito")
    private Integer idCarrito;

    /**
     * Usuario propietario del carrito.
     * OneToOne: un usuario → un carrito (unique = true en la FK).
     * referencedColumnName enlaza con la PK de la tabla "usuarios".
     */
    @OneToOne
    @JoinColumn(name = "id_usuario", referencedColumnName = "id_usuario", unique = true)
    private User usuario;

    /**
     * Lista de productos (con cantidades) dentro del carrito.
     * mappedBy = "carrito": el campo "carrito" en DetalleCarrito es el dueño de la relación.
     * cascade = ALL: cambios en el carrito se propagan a los detalles.
     * orphanRemoval = true: detalles sin carrito asociado se eliminan de la BD.
     *
     * @OrderBy("idDetalleCarrito ASC"): orden determinista y estable por ID de creación.
     * Sin este orden, la BD puede devolver las líneas en orden físico de fila y un
     * UPDATE de cantidad reordena los ítems del carrito en cada respuesta.
     */
    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idDetalleCarrito ASC")
    private List<DetalleCarrito> detalles;

    /**
     * Líneas que se eliminaron por vencer su reserva de 2 horas.
     * No se persiste: se llena en la respuesta para que el frontend notifique.
     */
    @Transient
    private List<ItemExpiradoDTO> expirados = new ArrayList<>();
}
