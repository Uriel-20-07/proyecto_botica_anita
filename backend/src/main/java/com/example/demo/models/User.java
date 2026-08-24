package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa a un usuario cliente de la farmacia.
 * 
 * Tabla en BD: "usuarios"
 * 
 * Lombok reduce el código boilerplate:
 * - @Data: genera getters, setters, equals, hashCode y toString.
 * - @NoArgsConstructor: constructor sin argumentos (requerido por JPA).
 * - @AllArgsConstructor: constructor con todos los campos.
 * 
 * El ciclo de vida @PrePersist establece valores por defecto
 * automáticamente antes de insertar el registro en la BD.
 */
@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Identificador único del usuario (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    /** Correo electrónico del usuario. Único y obligatorio. */
    @Column(name = "correo", unique = true, nullable = false)
    private String email;

    /** Contraseña hasheada con BCrypt. Nunca se almacena en texto plano. */
    @Column(nullable = false)
    private String password;

    /** Nombre del usuario. */
    @Column(nullable = false)
    private String nombre;

    /** Apellido del usuario. */
    @Column(nullable = false)
    private String apellido;

    /** Número de teléfono del usuario (opcional). */
    @Column(name = "telefono", nullable = true)
    private String telefono;

    /** Indica si la cuenta está activa. Una cuenta inactiva no puede iniciar sesión. */
    @Column(nullable = false)
    private Boolean activo;

    /** Fecha y hora en que se registró el usuario. */
    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    /** Fecha y hora del último inicio de sesión (nullable: null si nunca ha iniciado sesión). */
    @Column(nullable = true)
    private LocalDateTime ultimaConexion;

    /** Indica si el usuario ha verificado su email. Actualmente siempre false al registrarse. */
    @Column(nullable = false)
    private Boolean emailVerificado;

    /**
     * Callback ejecutado automáticamente por JPA ANTES de insertar el registro.
     * Establece valores por defecto para campos que podrían ser null
     * si no fueron explícitamente asignados.
     */
    @PrePersist
    protected void onCreate() {
        if (activo == null) {
            activo = true; // Las cuentas se crean activas por defecto
        }
        if (emailVerificado == null) {
            emailVerificado = false; // Email no verificado por defecto
        }
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now(); // Fecha de registro = ahora
        }
    }
}
