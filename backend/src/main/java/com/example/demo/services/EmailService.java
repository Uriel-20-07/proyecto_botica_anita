package com.example.demo.services;

import com.example.demo.models.Pedido;
import com.example.demo.models.DetallePedido;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Servicio para el envío de correos electrónicos transaccionales.
 * 
 * Utiliza {@link JavaMailSender} de Spring Mail, configurado en application.properties
 * con los datos del servidor SMTP (host, puerto, credenciales).
 * 
 * Los correos son de tipo "texto plano" (SimpleMailMessage). Para emails HTML
 * más elaborados se usaría MimeMessage/MimeMessageHelper.
 * 
 * Los errores de envío son capturados y registrados en consola sin lanzar
 * excepción, para que un fallo de email no interrumpa el flujo principal.
 * 
 * Tipos de correos soportados:
 * - Recuperación de contraseña.
 * - Verificación de email.
 * - Bienvenida simple.
 * - Bienvenida con cupón de descuento del 30%.
 */
@Service
public class EmailService {

    /** Componente de Spring Mail para enviar correos mediante SMTP. */
    @Autowired
    private JavaMailSender mailSender;

    /**
     * Envía un correo con el token de recuperación de contraseña.
     * 
     * El token es un código numérico de 5 dígitos que expira en 1 hora.
     *
     * @param destinatario email del usuario que olvidó su contraseña.
     * @param token        código de recuperación de 5 dígitos.
     */
    public void sendPasswordResetEmail(String destinatario, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@mifarmacode.com");  // Remitente de la plataforma
            message.setTo(destinatario);
            message.setSubject("Código de Recuperación de Contraseña - MiFarmaCode");

            String contenido = "Hola,\n\n" +
                    "Hemos recibido una solicitud para recuperar tu contraseña.\n\n" +
                    "Tu código de recuperación es: " + token + "\n\n" +
                    "Por favor, ingresa este código en la sección de recuperación de contraseña.\n" +
                    "Este código expirará en 1 hora.\n\n" +
                    "Si no solicitaste esta recuperación, por favor ignora este correo.\n\n" +
                    "Saludos,\nEquipo MiFarmaCode";

            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            // No se interrumpe el flujo principal si el email falla
            System.out.println("Error enviando email: " + e.getMessage());
        }
    }

    /**
     * Envía un correo para verificar la dirección de email del usuario.
     * 
     * Nota: La verificación de email está preparada pero no está completamente
     * integrada en el flujo de registro actual (emailVerificado siempre es false
     * al registrarse, pero no se envía este email automáticamente).
     *
     * @param destinatario     email a verificar.
     * @param verificationLink URL con el token de verificación.
     */
    public void sendVerificationEmail(String destinatario, String verificationLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@mifarmacode.com");
            message.setTo(destinatario);
            message.setSubject("Verifica tu Correo - MiFarmaCode");

            String contenido = "Hola,\n\n" +
                    "Gracias por registrarte en MiFarmaCode. " +
                    "Haz clic en el siguiente enlace para verificar tu correo:\n\n" +
                    verificationLink + "\n\n" +
                    "Saludos,\nEquipo MiFarmaCode";

            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de verificación: " + e.getMessage());
        }
    }

    /**
     * Envía un correo de bienvenida simple al usuario recién registrado.
     * 
     * Nota: actualmente no se invoca en el flujo de registro. En su lugar
     * se usa {@link #sendWelcomeCouponEmail} que incluye el cupón de descuento.
     *
     * @param destinatario email del nuevo usuario.
     * @param nombre       nombre del usuario para personalizar el saludo.
     */
    public void sendWelcomeEmail(String destinatario, String nombre) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@mifarmacode.com");
            message.setTo(destinatario);
            message.setSubject("Bienvenido a MiFarmaCode");

            String contenido = "Hola " + nombre + ",\n\n" +
                    "Te damos la bienvenida a MiFarmaCode. " +
                    "Tu cuenta ha sido creada exitosamente.\n\n" +
                    "Ahora puedes acceder a nuestras herramientas y servicios.\n\n" +
                    "Saludos,\nEquipo MiFarmaCode";

            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de bienvenida: " + e.getMessage());
        }
    }

    /**
     * Envía un correo de bienvenida que incluye el código de cupón de descuento del 30%.
     * 
     * Este es el email principal enviado al registrarse un nuevo usuario.
     * Explica cómo usar el cupón y su vigencia (30 días, un solo uso).
     *
     * @param destinatario email del nuevo usuario.
     * @param nombre       nombre del usuario para personalizar el saludo.
     * @param codigoCupon  código único del cupón (formato: "BIENVENIDA-XXXXXXXXXX").
     */
    public void sendWelcomeCouponEmail(String destinatario, String nombre, String codigoCupon) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@mifarmacode.com");
            message.setTo(destinatario);
            message.setSubject("¡Bienvenido a MiFarmaCode! Tu cupón de 30% ya está listo");

            String contenido = "Hola " + nombre + ",\n\n" +
                    "Gracias por unirte a MiFarmaCode. Como bienvenida, te hemos creado un cupón exclusivo con 30% de descuento para tu primera compra.\n\n"
                    +
                    "Tu código es: " + codigoCupon + "\n" +
                    "Es válido por 30 días y solo puede usarse una vez.\n\n" +
                    "Instrucciones:\n" +
                    "1. Agrega productos a tu carrito.\n" +
                    "2. Ingresa el código al finalizar la compra.\n" +
                    "3. Disfruta del 30% de descuento.\n\n" +
                    "Si tienes dudas, responde a este correo.\n\n" +
                    "Saludos,\nEquipo MiFarmaCode";

            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de cupón de bienvenida: " + e.getMessage());
        }
    }

    /**
     * Envía un correo de confirmación de compra al usuario con los detalles de su pedido.
     *
     * @param destinatario email del usuario.
     * @param nombre       nombre completo del usuario.
     * @param pedido       pedido creado con el total y fecha.
     * @param detalles     lista de detalles del pedido.
     * @param codigoCupon  código de cupón usado (opcional).
     */
    public void sendOrderConfirmationEmail(String destinatario, String nombre, Pedido pedido, List<DetallePedido> detalles, String codigoCupon, String nroBoleta) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@mifarmacode.com");
            message.setTo(destinatario);
            message.setSubject("Confirmación de tu compra #" + nroBoleta + " - MiFarmaCode");

            StringBuilder contenido = new StringBuilder();
            contenido.append("Hola ").append(nombre).append(",\n\n")
                    .append("¡Muchas gracias por tu compra! Tu pago ha sido procesado con éxito.\n\n")
                    .append("Detalles de tu pedido:\n")
                    .append("--------------------------------------------------\n")
                    .append("Código de pedido: #").append(nroBoleta).append("\n")
                    .append("Fecha de compra: ").append(pedido.getFecha().toString()).append("\n");

            if (pedido.getDireccionEnvio() != null && !pedido.getDireccionEnvio().trim().isEmpty()) {
                if (pedido.isEsUrgente()) {
                    contenido.append("Dirección de envío a domicilio: ").append(pedido.getDireccionEnvio()).append("\n");
                } else {
                    contenido.append("Establecimiento de recojo: ").append(pedido.getDireccionEnvio()).append("\n");
                }
            }

            contenido.append("\nProductos:\n");

            double subtotalProductos = 0.0;
            for (DetallePedido det : detalles) {
                BigDecimal subtotalProducto = det.getPrecioHistorico().multiply(BigDecimal.valueOf(det.getCantidad()));
                subtotalProductos += subtotalProducto.doubleValue();
                contenido.append("- ")
                        .append(det.getProducto().getNombre())
                        .append(" x")
                        .append(det.getCantidad())
                        .append(" : S/ ")
                        .append(subtotalProducto)
                        .append("\n");
            }

            double costoEnvio = pedido.isEsUrgente() ? 10.0 : ((subtotalProductos > 50.0) ? 0.0 : 5.0);

            contenido.append("\n");
            contenido.append("Costo de envío: ").append(costoEnvio == 10.0 ? "S/ 10.00 (Urgente)" : (costoEnvio == 0.0 ? "Gratis" : "S/ 5.00")).append("\n");
            if (codigoCupon != null && !codigoCupon.trim().isEmpty()) {
                contenido.append("Cupón aplicado: ").append(codigoCupon.toUpperCase()).append("\n");
            }
            contenido.append("Total pagado: S/ ").append(pedido.getTotal()).append("\n")
                    .append("--------------------------------------------------\n\n");

            if (pedido.getDireccionEnvio() != null && !pedido.getDireccionEnvio().trim().isEmpty()) {
                if (pedido.isEsUrgente()) {
                    contenido.append("Tu pedido urgente está siendo enviado a tu domicilio.\n\n");
                } else {
                    contenido.append("Tu pedido está siendo enviado al punto de recojo seleccionado para que puedas retirarlo.\n\n");
                }
            } else {
                contenido.append("Tu pedido ya está siendo preparado para su envío.\n\n");
            }

            contenido.append("Si tienes alguna duda o consulta, puedes responder a este correo o escribir a soporte@mifarmacode.com.\n\n")
                    .append("Saludos,\nEquipo MiFarmaCode");

            message.setText(contenido.toString());
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de confirmación de pedido: " + e.getMessage());
        }
    }

    /**
     * Envía un correo electrónico con un cupón de descuento promocional de campaña.
     *
     * @param destinatario email del usuario.
     * @param nombre       nombre del usuario.
     * @param codigoCupon  código del cupón generado.
     * @param descuento    porcentaje de descuento del cupón.
     * @param descripcion  descripción o motivo de la campaña.
     */
    public void sendCampaignCouponEmail(String destinatario, String nombre, String codigoCupon, double descuento, String descripcion) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@mifarmacode.com");
            message.setTo(destinatario);
            message.setSubject("¡Regalo especial para ti! Cupón de Descuento en MiFarmaCode");

            String contenido = "Hola " + nombre + ",\n\n" +
                    "Queremos premiar tu preferencia y por ello te obsequiamos un cupón de descuento exclusivo:\n\n" +
                    "Cupón: " + codigoCupon + "\n" +
                    "Descuento: " + (int)descuento + "% de descuento en tu próxima compra.\n" +
                    "Campaña: " + descripcion + "\n\n" +
                    "Este cupón es de un solo uso.\n\n" +
                    "¿Cómo aplicarlo?\n" +
                    "1. Ingresa a la tienda de MiFarmaCode y agrega tus productos.\n" +
                    "2. En el checkout, introduce el código del cupón antes de proceder con el pago.\n" +
                    "3. ¡Listo! Se aplicará el descuento automáticamente.\n\n" +
                    "Saludos cordiales,\nEquipo MiFarmaCode";

            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de cupón de campaña: " + e.getMessage());
        }
    }
}
