package com.example.demo.repositories;

import com.example.demo.models.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de códigos temporales de verificación de correo.
 */
@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    /** Busca el código vigente asociado a un correo. */
    Optional<EmailVerificationCode> findByEmail(String email);
}
