package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "detalle_carrito")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCarrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_carrito")
    private Integer idDetalleCarrito;

    @ManyToOne
    @JoinColumn(name = "id_carrito", nullable = false)
    @JsonIgnore
    private Carrito carrito;

    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;
}
