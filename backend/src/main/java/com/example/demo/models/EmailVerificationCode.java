package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un código temporal de verificación de correo.
 *
 * Tabla en BD: "codigos_verificacion_email"
 *
 * Se usa durante el registro de una cuenta nueva: el código se envía al correo
 * en el paso 1 y se valida en el paso 2. La cuenta recién se crea cuando el
 * código es correcto, por lo que esta tabla existe independientemente de
 * {@link User} (todavía no hay usuario que referenciar).
 *
 * El código se guarda hasheado con BCrypt; nunca en texto plano.
 */
@Entity
@Table(name = "codigos_verificacion_email")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationCode {

    /** Identificador único del registro (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correo al que se envió el código. Solo puede haber un código vigente por correo. */
    @Column(name = "correo", nullable = false, unique = true)
    private String email;

    /** Hash BCrypt del código de 6 dígitos. */
    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    /** Fecha y hora en que se generó el código. */
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    /** Fecha y hora en que el código deja de ser válido. */
    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    /** Cantidad de intentos fallidos de validación. */
    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    /** Indica si el código ya venció. */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(fechaExpiracion);
    }
}
