package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Calificación de satisfacción que un cliente le da a un pedido (1 a 5 estrellas),
 * con un comentario opcional. Cada pedido solo puede calificarse una vez.
 */
@Entity
@Table(name = "calificaciones", uniqueConstraints = @UniqueConstraint(columnNames = "id_pedido"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calificacion")
    private Integer idCalificacion;

    /** Pedido calificado (único: un pedido, una calificación). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    /** Cliente que calificó. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User usuario;

    /** Puntaje de 1 a 5. */
    @Column(name = "estrellas", nullable = false)
    private Integer estrellas;

    /** Comentario opcional (máximo 500 caracteres). */
    @Column(name = "comentario", length = 500)
    private String comentario;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
