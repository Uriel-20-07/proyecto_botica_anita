package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(nullable = false)
    private Boolean utilizado = false;

    @Column(nullable = true)
    private LocalDateTime fechaUtilizacion;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(fechaExpiracion) || utilizado;
    }
}
