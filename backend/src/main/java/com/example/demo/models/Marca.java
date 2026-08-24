package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "marcas")
@Data
public class Marca {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca")
    private Integer idMarca;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "img_url", length = 500)
    private String imgUrl;
}
