package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    /** ACTIVA mientras la reserva de stock esté vigente. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDetalleCarrito estado = EstadoDetalleCarrito.ACTIVA;

    /** Momento en que el producto entró al carrito (UTC). */
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    /** Momento en que vence la reserva: 2 horas después de entrar al carrito (UTC). */
    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (fechaExpiracion == null) {
            fechaExpiracion = fechaCreacion.plusHours(2);
        }
        if (estado == null) {
            estado = EstadoDetalleCarrito.ACTIVA;
        }
    }

    /** Segundos que le quedan a la reserva (0 si ya venció). Lo usa el frontend para la cuenta regresiva. */
    public long getSegundosRestantes() {
        if (fechaExpiracion == null) {
            return 0;
        }
        long segundos = Duration.between(LocalDateTime.now(ZoneOffset.UTC), fechaExpiracion).getSeconds();
        return Math.max(0, segundos);
    }

    /** true cuando la reserva ya venció (sin importar el estado guardado). */
    public boolean isReservaVencida() {
        return fechaExpiracion != null
                && !fechaExpiracion.isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }
}
