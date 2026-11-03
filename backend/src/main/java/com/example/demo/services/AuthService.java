package com.example.demo.services;

import com.example.demo.models.Cupon;
import com.example.demo.models.EmailVerificationCode;
import com.example.demo.models.PasswordResetToken;
import com.example.demo.models.User;
import com.example.demo.repositories.CuponRepository;
import com.example.demo.repositories.EmailVerificationCodeRepository;
import com.example.demo.repositories.PasswordResetTokenRepository;
import com.example.demo.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de autenticación y gestión de usuarios.
 * 
 * Centraliza la lógica de negocio relacionada con:
 * - Registro de nuevos usuarios (con validaciones, verificación de correo por código y cupón de bienvenida).
 * - Login y generación de tokens JWT.
 * - Recuperación de contraseña por email (flujo de reset por token).
 * - Cambio de contraseña (tanto por token como autenticado).
 * 
 * Las contraseñas nunca se almacenan en texto plano; se hashean con BCrypt
 * antes de guardarlas en la base de datos.
 */
@Service
public class AuthService {

    /** Versión vigente de los Términos y Condiciones (debe coincidir con TerminosComponent en el frontend). */
    public static final String VERSION_TERMINOS = "1.0";

    /** Minutos de vigencia del código de verificación enviado al correo. */
    public static final int CODIGO_EXPIRACION_MINUTOS = 15;

    /** Intentos máximos permitidos para acertar el código de verificación. */
    private static final int CODIGO_MAX_INTENTOS = 5;

    /** Segundos mínimos entre envíos de código para un mismo correo. */
    private static final int CODIGO_REENVIO_COOLDOWN_SEGUNDOS = 60;


    /** Repositorio de usuarios clientes. */
    @Autowired
    private UserRepository userRepository;

    /** Repositorio de códigos temporales de verificación de correo. */
    @Autowired
    private EmailVerificationCodeRepository emailVerificationCodeRepository;

    /** Repositorio de tokens de recuperación de contraseña. */
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    /** Generador seguro de códigos de verificación. */
    private final SecureRandom secureRandom = new SecureRandom();

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
     * Indica si un email está libre para registrarse.
     *
     * Solo comprueba la disponibilidad del correo; no crea ni reserva nada.
     * La unicidad definitiva se vuelve a validar en {@link #registrar(String, String, String, String, String, String, String, boolean, String)}
     * y por la restricción unique de la base de datos.
     *
     * @param email correo electrónico a consultar.
     * @return {@code true} si no existe ningún usuario con ese email.
     */
    public boolean emailDisponible(String email) {
        return email != null && !email.isBlank() && !userRepository.existsByEmail(email.trim());
    }

    /**
     * Genera un código de 6 dígitos y lo envía al correo indicado.
     *
     * Flujo del registro:
     * - Paso 1 (credenciales): el frontend llama a este método después de
     *   comprobar que el correo está disponible.
     * - Paso 3 (código): el registro definitivo valida el código con
     *   {@link #validarCodigoVerificacion(String, String)} y recién ahí crea la cuenta.
     *
     * El código se guarda hasheado con BCrypt y vence a los
     * {@value #CODIGO_EXPIRACION_MINUTOS} minutos. Se permite un solo código
     * vigente por correo y se aplica un cooldown entre envíos para evitar spam.
     *
     * Si ya existe un código vigente y {@code reenviar} es {@code false}, el
     * código se reutiliza y no se envía un correo nuevo. Esto evita correos
     * duplicados cuando el usuario recarga la página o vuelve al paso 1.
     *
     * @param email correo al que se enviará el código.
     * @param reenviar {@code true} para forzar un código nuevo (botón "Reenviar código").
     * @return {@code true} si se envió un correo nuevo; {@code false} si se
     *         reutilizó un código vigente.
     * @throws RuntimeException si el correo ya está registrado, si se solicita
     *                          demasiado rápido o si el envío del correo falla.
     */
    public boolean enviarCodigoVerificacion(String email, boolean reenviar) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("El email es requerido");
        }
        email = email.trim();

        if (!emailDisponible(email)) {
            throw new RuntimeException("Este correo ya está registrado");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Optional<EmailVerificationCode> existenteOpt = emailVerificationCodeRepository.findByEmail(email);

        if (existenteOpt.isPresent() && !existenteOpt.get().isExpired()) {
            EmailVerificationCode existente = existenteOpt.get();

            // Ya hay un código vigente: se reutiliza salvo que el usuario pida reenviar.
            if (!reenviar) {
                return false;
            }

            boolean enCooldown = existente.getFechaCreacion()
                    .plusSeconds(CODIGO_REENVIO_COOLDOWN_SEGUNDOS).isAfter(ahora);
            if (enCooldown) {
                long segundos = CODIGO_REENVIO_COOLDOWN_SEGUNDOS
                        - Duration.between(existente.getFechaCreacion(), ahora).getSeconds();
                throw new RuntimeException("Espera " + Math.max(segundos, 1)
                        + " segundos antes de solicitar otro código");
            }
        }

        // Código numérico de 6 dígitos (100000-999999), sin depender del locale
        String codigo = String.valueOf(100000 + secureRandom.nextInt(900000));

        EmailVerificationCode registro = existenteOpt.orElseGet(EmailVerificationCode::new);
        registro.setEmail(email);
        registro.setCodigoHash(passwordEncoder.encode(codigo));
        registro.setFechaCreacion(ahora);
        registro.setFechaExpiracion(ahora.plusMinutes(CODIGO_EXPIRACION_MINUTOS));
        registro.setIntentos(0);
        emailVerificationCodeRepository.save(registro);

        boolean enviado = emailService.sendVerificationCodeEmail(email, codigo, CODIGO_EXPIRACION_MINUTOS);
        if (!enviado) {
            // Si el correo no salió, se elimina el código para permitir reintentar de inmediato.
            emailVerificationCodeRepository.delete(registro);
            throw new RuntimeException("No se pudo enviar el correo de verificación. Inténtalo de nuevo");
        }

        return true;
    }

    /**
     * Valida el código de verificación asociado a un correo.
     *
     * Si el código es incorrecto incrementa el contador de intentos; al llegar
     * al máximo se elimina el código y se obliga a solicitar uno nuevo.
     * Si el código es correcto, devuelve el registro para que el registro
     * definitivo lo elimine una vez creada la cuenta.
     *
     * @param email  correo al que se envió el código.
     * @param codigo código de 6 dígitos ingresado por el usuario.
     * @return el registro del código validado.
     * @throws RuntimeException si el código no existe, expiró, es incorrecto
     *                          o se agotaron los intentos.
     */
    private EmailVerificationCode validarCodigoVerificacion(String email, String codigo) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            throw new RuntimeException("El código de verificación debe tener 6 dígitos");
        }

        EmailVerificationCode registro = emailVerificationCodeRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Código inválido o expirado. Solicita uno nuevo"));

        if (registro.isExpired()) {
            emailVerificationCodeRepository.delete(registro);
            throw new RuntimeException("El código expiró. Solicita uno nuevo");
        }

        int intentosActuales = registro.getIntentos() == null ? 0 : registro.getIntentos();
        if (intentosActuales >= CODIGO_MAX_INTENTOS) {
            emailVerificationCodeRepository.delete(registro);
            throw new RuntimeException("Demasiados intentos. Solicita un nuevo código");
        }

        if (!passwordEncoder.matches(codigo, registro.getCodigoHash())) {
            int intentos = intentosActuales + 1;
            if (intentos >= CODIGO_MAX_INTENTOS) {
                emailVerificationCodeRepository.delete(registro);
                throw new RuntimeException("Demasiados intentos. Solicita un nuevo código");
            }
            registro.setIntentos(intentos);
            emailVerificationCodeRepository.save(registro);
            throw new RuntimeException("Código incorrecto. Te quedan "
                    + (CODIGO_MAX_INTENTOS - intentos) + " intentos");
        }

        return registro;
    }

    /**
     * Registra un nuevo usuario en el sistema, previa confirmación del correo.
     *
     * Proceso:
     * 1. Verifica que el email no esté ya registrado.
     * 2. Valida el código de verificación enviado al correo en el paso 1.
     * 3. Valida la contraseña y el documento.
     * 4. Crea el usuario con contraseña hasheada y email ya verificado.
     * 5. Elimina el código de verificación utilizado.
     * 6. Crea un cupón de bienvenida con 30% de descuento (válido 30 días).
     * 7. Envía el código del cupón por email.
     *
     * @param email    correo electrónico único del nuevo usuario.
     * @param password contraseña en texto plano (se hashea antes de guardar).
     * @param nombre   nombre del usuario.
     * @param apellido apellido del usuario.
     * @param telefono número de teléfono (opcional).
     * @param tipoDocumento "DNI" (8 dígitos) o "CE" (9 dígitos).
     * @param numeroDocumento número del documento, solo dígitos.
     * @param aceptaTerminos indica si el usuario aceptó los Términos y Condiciones.
     * @param codigo   código de 6 dígitos enviado al correo.
     * @return el usuario recién creado y persistido.
     * @throws RuntimeException si el email ya existe, el código es inválido,
     *                          la contraseña es muy corta o el documento es inválido.
     */
    public User registrar(String email, String password, String nombre, String apellido,
                          String telefono, String tipoDocumento, String numeroDocumento,
                          boolean aceptaTerminos, String codigo) {
        // Sin aceptar los Términos y Condiciones no se puede crear la cuenta
        if (!aceptaTerminos) {
            throw new RuntimeException("Debes aceptar los Términos y Condiciones para registrarte");
        }

        if (email == null || email.isBlank()) {
            throw new RuntimeException("El email es requerido");
        }
        email = email.trim();

        // Verificar unicidad del email
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Validar el código enviado al correo antes de crear la cuenta
        EmailVerificationCode codigoVerificacion = validarCodigoVerificacion(email, codigo);

        // Validar longitud mínima de contraseña
        if (password == null || password.length() < 6) {
            throw new RuntimeException("La contraseña debe tener al menos 6 caracteres");
        }

        // Validar documento de identidad
        if (tipoDocumento == null || (!tipoDocumento.equals("DNI") && !tipoDocumento.equals("CE"))) {
            throw new RuntimeException("El tipo de documento debe ser DNI o CE");
        }
        String patron = tipoDocumento.equals("DNI") ? "\\d{8}" : "\\d{9}";
        if (numeroDocumento == null || !numeroDocumento.matches(patron)) {
            throw new RuntimeException(tipoDocumento.equals("DNI")
                    ? "El DNI debe tener 8 dígitos numéricos"
                    : "El carnet de extranjería debe tener 9 dígitos numéricos");
        }
        if (userRepository.existsByNumeroDocumento(numeroDocumento)) {
            throw new RuntimeException("El documento ya está registrado");
        }

        // Crear y configurar el nuevo usuario
        User usuario = new User();
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(password)); // Hash BCrypt
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setTelefono(telefono);
        usuario.setTipoDocumento(tipoDocumento);
        usuario.setNumeroDocumento(numeroDocumento);
        // Constancia de la aceptación de los Términos y Condiciones (fecha y versión)
        usuario.setTerminosAceptados(true);
        usuario.setFechaAceptacionTerminos(java.time.LocalDateTime.now());
        usuario.setVersionTerminos(VERSION_TERMINOS);
        usuario.setActivo(true);
        usuario.setEmailVerificado(true); // La cuenta se crea solo con el correo ya confirmado

        User usuarioGuardado = userRepository.save(usuario);

        // El código ya se usó: se elimina para que no pueda reutilizarse
        emailVerificationCodeRepository.delete(codigoVerificacion);

        // Crear cupón de bienvenida y notificar al usuario por correo.
        // Un fallo del cupón no debe invalidar una cuenta ya creada y verificada.
        try {
            Cupon cuponBienvenida = crearCuponBienvenida(usuarioGuardado);
            emailService.sendWelcomeCouponEmail(email, nombre, cuponBienvenida.getCodigo());
        } catch (Exception e) {
            System.out.println("No se pudo crear/enviar el cupón de bienvenida: " + e.getMessage());
        }

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
