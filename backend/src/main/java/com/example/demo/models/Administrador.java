package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad JPA que representa un administrador del sistema.
 * 
 * Tabla en BD: "administradores"
 * 
 * Los administradores son usuarios internos del sistema con acceso
 * al panel de administración. Se diferencian de los usuarios clientes
 * en que usan correo corporativo y tienen un rol asignado.
 * 
 * Roles disponibles:
 * - "admin": acceso completo (puede ver y modificar stock, ver ventas).
 * - "vendedor": acceso limitado (solo puede ver productos y ventas, no modificar stock).
 */
@Entity
@Table(name = "administradores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Administrador {

    /** Identificador único del administrador (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_admin")
    private Integer idAdmin;

    /** Nombre completo del administrador. Máximo 100 caracteres. */
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /**
     * Correo electrónico corporativo del administrador.
     * Formato esperado: nombre@correo_corp.com
     * Es el identificador único para el login de administradores.
     */
    @Column(name = "correo_corp", unique = true, nullable = false, length = 100)
    private String correoCorp;

    /** Contraseña hasheada con BCrypt. Nunca se almacena en texto plano. */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    /** Rol del administrador: "admin" o "vendedor". Determina los permisos en el panel. */
    @Column(name = "rol", length = 50)
    private String rol;
}
