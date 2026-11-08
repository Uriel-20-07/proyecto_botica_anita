package com.example.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "subcategorias")
@Getter
@Setter
public class Subcategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_subcategoria")
    private Integer idSubcategoria;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private Integer orden = 0;

    // Se ignora en el JSON para evitar bucles (Categoria -> Subcategoria -> Categoria)
    @ManyToOne
    @JoinColumn(name = "id_categoria", nullable = false)
    @JsonIgnore
    private Categoria categoria;
}
