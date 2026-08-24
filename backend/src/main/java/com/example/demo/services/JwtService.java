package com.example.demo.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Servicio para la creación, validación y parsing de tokens JWT (JSON Web Tokens).
 * 
 * Los JWT se utilizan como mecanismo de autenticación stateless: el servidor
 * genera un token firmado al hacer login, y el cliente lo envía en cada
 * petición para identificarse sin necesidad de sesiones en el servidor.
 * 
 * Estructura de un JWT: [Header].[Payload].[Signature]
 * - Header: algoritmo de firma (HS256).
 * - Payload (claims): email del usuario, fechas de emisión y expiración.
 * - Signature: firma HMAC-SHA256 que garantiza la integridad del token.
 * 
 * Configuración (application.properties):
 *   jwt.secret      → clave secreta para firmar (mínimo 256 bits para HS256).
 *   jwt.expiration  → duración del token en milisegundos (default: 1 hora).
 */
@Service
public class JwtService {

    /**
     * Clave secreta para firmar los tokens JWT.
     * Se lee de application.properties. Si no existe, usa el valor por defecto.
     * IMPORTANTE: En producción, esta clave debe ser larga, aleatoria y mantenerse en secreto.
     */
    @Value("${jwt.secret:mi_clave_secreta_super_segura_de_al_menos_256_bits_para_HS256}")
    private String jwtSecret;

    /**
     * Duración del token en milisegundos.
     * Default: 3600000 ms = 1 hora.
     */
    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    /**
     * Genera la clave criptográfica HMAC-SHA256 a partir del secreto configurado.
     * Esta clave se usa tanto para firmar como para verificar tokens.
     *
     * @return SecretKey lista para usar con la librería JJWT.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Genera un token JWT de autenticación para un usuario dado.
     * 
     * Claims del token:
     * - subject: email del usuario (identificador único).
     * - issuedAt: fecha/hora de emisión del token.
     * - expiration: fecha/hora de expiración (issuedAt + jwtExpirationMs).
     *
     * @param email correo electrónico del usuario autenticado.
     * @return token JWT firmado como String compacto (Base64URL).
     */
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)                                                          // Quién es el usuario
                .issuedAt(new Date())                                                    // Cuándo se emitió
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))     // Cuándo expira
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)                     // Firma con HMAC-SHA256
                .compact();
    }

    /**
     * Genera un token JWT especial para el flujo de recuperación de contraseña.
     * 
     * Diferencia con {@link #generateToken}: agrega el claim "type" = "password-reset"
     * para distinguirlo de tokens de autenticación normales.
     * Duración fija: 1 hora (3600000 ms).
     *
     * @param email correo electrónico del usuario que solicita el reset.
     * @return token JWT de recuperación firmado.
     */
    public String generatePasswordResetToken(String email) {
        long resetTokenExpiration = 3600000; // 1 hora en milisegundos
        return Jwts.builder()
                .subject(email)
                .claim("type", "password-reset")  // Claim personalizado para identificar el propósito
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + resetTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extrae el email (subject) del payload de un token JWT.
     * 
     * Si el token es inválido, expirado o mal formado, retorna {@code null}
     * en lugar de lanzar una excepción (manejo silencioso).
     *
     * @param token el token JWT del que se extrae el email.
     * @return el email del usuario, o {@code null} si el token no es válido.
     */
    public String getEmailFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())   // Verifica la firma usando la clave secreta
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();            // El subject es el email del usuario
        } catch (Exception e) {
            return null; // Token inválido o expirado
        }
    }

    /**
     * Verifica si un token JWT es válido (bien formado, firma correcta y no expirado).
     *
     * @param token el token JWT a validar.
     * @return {@code true} si el token es válido; {@code false} en cualquier otro caso.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token); // Lanza excepción si el token es inválido
            return true;
        } catch (Exception e) {
            return false; // ExpiredJwtException, MalformedJwtException, SignatureException, etc.
        }
    }
}
