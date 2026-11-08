package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.ToString;
@Entity
@Table(name = "categorias")
@Data
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCategoria;

    @Column(nullable = false, length = 100)
    private String nombre;

    // Esto es opcional: permite ver los productos desde la categoría
    // Usamos @JsonIgnore para evitar bucles infinitos al convertir a JSON
    @OneToMany(mappedBy = "categoria")
    @JsonIgnore
    private List<Producto> productos;

    // Subcategorías de esta categoría (se envían en GET /api/categorias), ordenadas por "orden"
    @OneToMany(mappedBy = "categoria", fetch = FetchType.EAGER)
    @OrderBy("orden ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Subcategoria> subcategorias;
}
