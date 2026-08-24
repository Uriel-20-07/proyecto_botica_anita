package com.example.demo.services;

import com.example.demo.models.Cupon;
import com.example.demo.models.PasswordResetToken;
import com.example.demo.models.User;
import com.example.demo.repositories.CuponRepository;
import com.example.demo.repositories.PasswordResetTokenRepository;
import com.example.demo.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de autenticación y gestión de usuarios.
 * 
 * Centraliza la lógica de negocio relacionada con:
 * - Registro de nuevos usuarios (con validaciones y cupón de bienvenida).
 * - Login y generación de tokens JWT.
 * - Recuperación de contraseña por email (flujo de reset por token).
 * - Cambio de contraseña (tanto por token como autenticado).
 * 
 * Las contraseñas nunca se almacenan en texto plano; se hashean con BCrypt
 * antes de guardarlas en la base de datos.
 */
@Service
public class AuthService {

    /** Repositorio de usuarios clientes. */
    @Autowired
    private UserRepository userRepository;

    /** Repositorio de tokens de recuperación de contraseña. */
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    /** Servicio para generar y validar tokens JWT. */
    @Autowired
    private JwtService jwtService;

    /** Servicio para enviar correos electrónicos (bienvenida, recuperación). */
    @Autowired
    private EmailService emailService;

    /** Repositorio de cupones de descuento. */
    @Autowired
    private CuponRepository cuponRepository;

    /**
     * URL base del frontend Angular.
     * Se usa para construir el enlace de recuperación de contraseña en los emails.
     * Valor por defecto: http://localhost:4200 (desarrollo local).
     */
    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    /** Encriptador de contraseñas con BCrypt (factor de costo por defecto: 10). */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Registra un nuevo usuario en el sistema.
     * 
     * Proceso:
     * 1. Verifica que el email no esté ya registrado.
     * 2. Valida que la contraseña tenga al menos 6 caracteres.
     * 3. Crea el usuario con contraseña hasheada.
     * 4. Crea un cupón de bienvenida con 30% de descuento (válido 30 días).
     * 5. Envía el código del cupón por email.
     *
     * @param email    correo electrónico único del nuevo usuario.
     * @param password contraseña en texto plano (se hashea antes de guardar).
     * @param nombre   nombre del usuario.
     * @param apellido apellido del usuario.
     * @param telefono número de teléfono (opcional).
     * @return el usuario recién creado y persistido.
     * @throws RuntimeException si el email ya existe o la contraseña es muy corta.
     */
    public User registrar(String email, String password, String nombre, String apellido, String telefono) {
        // Verificar unicidad del email
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Validar longitud mínima de contraseña
        if (password == null || password.length() < 6) {
            throw new RuntimeException("La contraseña debe tener al menos 6 caracteres");
        }

        // Crear y configurar el nuevo usuario
        User usuario = new User();
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(password)); // Hash BCrypt
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setTelefono(telefono);
        usuario.setActivo(true);
        usuario.setEmailVerificado(false); // El email no está verificado al registrarse

        User usuarioGuardado = userRepository.save(usuario);

        // Crear cupón de bienvenida y notificar al usuario por correo
        Cupon cuponBienvenida = crearCuponBienvenida(usuarioGuardado);
        emailService.sendWelcomeCouponEmail(email, nombre, cuponBienvenida.getCodigo());

        return usuarioGuardado;
    }

    /**
     * Crea un cupón de bienvenida del 30% para un usuario recién registrado.
     * El cupón es de tipo PORCENTAJE, válido por 30 días desde la creación.
     *
     * @param usuario usuario al que se asocia el cupón.
     * @return el cupón creado y persistido.
     */
    private Cupon crearCuponBienvenida(User usuario) {
        Cupon cupon = new Cupon();
        cupon.setCodigo(generarCodigoCupon());         // Código único tipo "BIENVENIDA-XXXXXXXXXX"
        cupon.setUsuario(usuario);
        cupon.setTipoDescuento("PORCENTAJE");
        cupon.setValorDescuento(BigDecimal.valueOf(30)); // 30% de descuento
        cupon.setFechaCreacion(LocalDateTime.now());
        cupon.setFechaExpiracion(LocalDateTime.now().plusDays(30)); // Válido 30 días
        cupon.setUsado(false);
        cupon.setDescripcion("Cupón de bienvenida 30% descuento");
        cupon.setActivo(true);

        return cuponRepository.save(cupon);
    }

    /**
     * Genera un código de cupón único con el prefijo "BIENVENIDA-".
     * Usa UUID para garantizar unicidad, tomando los primeros 10 caracteres alfanuméricos.
     * 
     * Formato de salida: "BIENVENIDA-XXXXXXXXXXX" (todo en mayúsculas).
     *
     * @return código de cupón único.
     */
    private String generarCodigoCupon() {
        return "BIENVENIDA-" + UUID.randomUUID().toString().replaceAll("[-]", "").substring(0, 10).toUpperCase();
    }

    /**
     * Autentica a un usuario y genera un token JWT.
     * 
     * Validaciones:
     * 1. El usuario debe existir en la base de datos.
     * 2. La cuenta debe estar activa (no deshabilitada).
     * 3. La contraseña debe coincidir con el hash almacenado.
     * 4. Actualiza la fecha de última conexión.
     *
     * @param email    correo del usuario.
     * @param password contraseña en texto plano.
     * @return token JWT de autenticación (válido por 1 hora por defecto).
     * @throws RuntimeException si las credenciales son inválidas o el usuario está inactivo.
     */
    public String login(String email, String password) {
        Optional<User> usuarioOpt = userRepository.findByEmail(email);

        if (usuarioOpt.isEmpty()) {
            throw new RuntimeException("Usuario no encontrado");
        }

        User usuario = usuarioOpt.get();

        // Verificar que la cuenta esté activa
        if (!usuario.getActivo()) {
            throw new RuntimeException("Usuario inactivo");
        }

        // Verificar contraseña usando BCrypt (compara texto plano con hash)
        if (!passwordEncoder.matches(password, usuario.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        // Registrar la hora del login
        usuario.setUltimaConexion(LocalDateTime.now());
        userRepository.save(usuario);

        // Generar y retornar token JWT
        return jwtService.generateToken(email);
    }

    /**
     * Inicia el flujo de recuperación de contraseña enviando un enlace por email.
     * 
     * Por seguridad, si el email NO existe en el sistema, el método retorna
     * silenciosamente sin revelar si el email está o no registrado (evita
     * ataques de enumeración de usuarios).
     * 
     * El token generado es un UUID aleatorio (válido por 1 hora), diferente
     * al token JWT de autenticación.
     *
     * @param email correo del usuario que solicita recuperar su contraseña.
     */
    public void solicitarRecuperacionContraseña(String email) {
        Optional<User> usuarioOpt = userRepository.findByEmail(email);

        if (usuarioOpt.isEmpty()) {
            // No revelamos si el email existe o no (seguridad)
            return;
        }

        User usuario = usuarioOpt.get();

        // Generar token numérico de 5 dígitos único para el reset (evitando colisiones)
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        String token;
        do {
            int tokenInt = 10000 + secureRandom.nextInt(90000);
            token = String.valueOf(tokenInt);
        } while (passwordResetTokenRepository.findByToken(token).isPresent());

        // Persistir el token de reset con su fecha de expiración (1 hora)
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(usuario);
        resetToken.setFechaCreacion(LocalDateTime.now());
        resetToken.setFechaExpiracion(LocalDateTime.now().plusHours(1));
        resetToken.setUtilizado(false);

        passwordResetTokenRepository.save(resetToken);

        // Enviar email con el token de recuperación
        emailService.sendPasswordResetEmail(email, token);
    }

    /**
     * Valida si un token de recuperación de contraseña es válido y no ha expirado.
     * Se usa para verificar el token antes de mostrar el formulario de nueva contraseña.
     *
     * @param token el token UUID de recuperación.
     * @return {@code true} si el token existe y no ha expirado; {@code false} en caso contrario.
     */
    public boolean validarTokenRecuperacion(String token) {
        Optional<PasswordResetToken> resetTokenOpt = passwordResetTokenRepository.findByToken(token);

        if (resetTokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken resetToken = resetTokenOpt.get();
        return !resetToken.isExpired(); // Delega la lógica de expiración al modelo
    }

    /**
     * Cambia la contraseña de un usuario usando un token de recuperación.
     * 
     * Proceso:
     * 1. Verifica que el token exista y no haya expirado.
     * 2. Valida la nueva contraseña (mínimo 6 caracteres).
     * 3. Actualiza la contraseña del usuario con hash BCrypt.
     * 4. Marca el token como utilizado (para evitar reutilización).
     *
     * @param token          token UUID de recuperación.
     * @param nuevaContraseña nueva contraseña en texto plano.
     * @throws RuntimeException si el token es inválido, expirado, o la contraseña es muy corta.
     */
    public void cambiarContraseña(String token, String nuevaContraseña) {
        Optional<PasswordResetToken> resetTokenOpt = passwordResetTokenRepository.findByToken(token);

        if (resetTokenOpt.isEmpty()) {
            throw new RuntimeException("Token inválido");
        }

        PasswordResetToken resetToken = resetTokenOpt.get();

        if (resetToken.isExpired()) {
            throw new RuntimeException("Token expirado");
        }

        // Validar nueva contraseña
        if (nuevaContraseña == null || nuevaContraseña.length() < 6) {
            throw new RuntimeException("La contraseña debe tener al menos 6 caracteres");
        }

        // Actualizar la contraseña con hash BCrypt
        User usuario = resetToken.getUser();
        usuario.setPassword(passwordEncoder.encode(nuevaContraseña));
        userRepository.save(usuario);

        // Invalidar el token para que no pueda ser reutilizado
        resetToken.setUtilizado(true);
        resetToken.setFechaUtilizacion(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
    }

    /**
     * Cambia la contraseña de un usuario autenticado (conociendo la contraseña actual).
     * Este flujo no requiere token de reset, sino que el usuario proporciona su
     * contraseña actual como verificación adicional.
     *
     * @param email            email del usuario autenticado.
     * @param contraseñaActual contraseña actual en texto plano (para verificación).
     * @param nuevaContraseña  nueva contraseña en texto plano.
     * @throws RuntimeException si el usuario no existe, la contraseña actual es incorrecta,
     *                          o la nueva contraseña es muy corta.
     */
    public void cambiarContraseñaAutenticado(String email, String contraseñaActual, String nuevaContraseña) {
        Optional<User> usuarioOpt = userRepository.findByEmail(email);

        if (usuarioOpt.isEmpty()) {
            throw new RuntimeException("Usuario no encontrado");
        }

        User usuario = usuarioOpt.get();

        // Verificar que la contraseña actual sea correcta antes de permitir el cambio
        if (!passwordEncoder.matches(contraseñaActual, usuario.getPassword())) {
            throw new RuntimeException("Contraseña actual incorrecta");
        }

        if (nuevaContraseña == null || nuevaContraseña.length() < 6) {
            throw new RuntimeException("La contraseña debe tener al menos 6 caracteres");
        }

        usuario.setPassword(passwordEncoder.encode(nuevaContraseña));
        userRepository.save(usuario);
    }

    /**
     * Busca y retorna un usuario por su email.
     * Método utilitario usado por los controladores para obtener el usuario
     * autenticado a partir del email extraído del token JWT.
     *
     * @param email correo del usuario a buscar.
     * @return el usuario encontrado.
     * @throws RuntimeException si no existe ningún usuario con ese email.
     */
    public User obtenerUsuarioPorEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
