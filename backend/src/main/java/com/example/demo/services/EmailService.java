package com.example.demo.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.demo.models.DetallePedido;
import com.example.demo.models.Pedido;

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
            message.setFrom("noreply@boticaanita.com");  // Remitente de la plataforma
            message.setTo(destinatario);
            message.setSubject("Código de Recuperación de Contraseña - BoticaAnita");

            String contenido = "Hola,\n\n" +
                    "Hemos recibido una solicitud para recuperar tu contraseña.\n\n" +
                    "Tu código de recuperación es: " + token + "\n\n" +
                    "Por favor, ingresa este código en la sección de recuperación de contraseña.\n" +
                    "Este código expirará en 1 hora.\n\n" +
                    "Si no solicitaste esta recuperación, por favor ignora este correo.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";

            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            // No se interrumpe el flujo principal si el email falla
            System.out.println("Error enviando email: " + e.getMessage());
        }
    }

    /**
     * Envía el código de 6 dígitos que confirma el correo durante el registro.
     *
     * A diferencia de {@link #sendVerificationEmail(String, String)}, este método
     * devuelve si el envío fue exitoso para que el flujo de registro pueda avisar
     * al usuario cuando el SMTP falla.
     *
     * @param destinatario      email a verificar.
     * @param codigo            código numérico de 6 dígitos.
     * @param expiracionMinutos minutos de vigencia del código.
     * @return {@code true} si el correo se envió; {@code false} si hubo un error.
     */
    public boolean sendVerificationCodeEmail(String destinatario, String codigo, int expiracionMinutos) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Confirma tu correo - BoticaAnita");

            String contenido = "Hola,\n\n" +
                    "Gracias por registrarte en BoticaAnita.\n\n" +
                    "Tu código de verificación es: " + codigo + "\n\n" +
                    "Ingresa este código en la ventana de registro para confirmar tu correo.\n" +
                    "El código expirará en " + expiracionMinutos + " minutos.\n\n" +
                    "Si no solicitaste este registro, puedes ignorar este correo.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";

            message.setText(contenido);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            System.out.println("Error enviando email de verificación: " + e.getMessage());
            return false;
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
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Verifica tu Correo - BoticaAnita");

            String contenido = "Hola,\n\n" +
                    "Gracias por registrarte en BoticaAnita. " +
                    "Haz clic en el siguiente enlace para verificar tu correo:\n\n" +
                    verificationLink + "\n\n" +
                    "Saludos,\nEquipo BoticaAnita";

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
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Bienvenido a BoticaAnita");

            String contenido = "Hola " + nombre + ",\n\n" +
                    "Te damos la bienvenida a BoticaAnita. " +
                    "Tu cuenta ha sido creada exitosamente.\n\n" +
                    "Ahora puedes acceder a nuestras herramientas y servicios.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";

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
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("¡Bienvenido a BoticaAnita! Tu cupón de 30% ya está listo");

            String contenido = "Hola " + nombre + ",\n\n" +
                    "Gracias por unirte a BoticaAnita. Como bienvenida, te hemos creado un cupón exclusivo con 30% de descuento para tu primera compra.\n\n"
                    +
                    "Tu código es: " + codigoCupon + "\n" +
                    "Es válido por 30 días y solo puede usarse una vez.\n\n" +
                    "Instrucciones:\n" +
                    "1. Agrega productos a tu carrito.\n" +
                    "2. Ingresa el código al finalizar la compra.\n" +
                    "3. Disfruta del 30% de descuento.\n\n" +
                    "Si tienes dudas, responde a este correo.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";

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
        // Plin y SIP (transferencias) reciben un correo propio de verificación de la compra
        if (esTransferencia(pedido.getMetodoPago())) {
            sendTransferPurchaseVerificationEmail(destinatario, nombre, pedido, detalles, codigoCupon, nroBoleta);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Confirmación de tu compra #" + nroBoleta + " - BoticaAnita");

            StringBuilder contenido = new StringBuilder();
            contenido.append("Hola ").append(nombre).append(",\n\n")
                    .append("¡Muchas gracias por tu compra! Tu pago ha sido procesado con éxito.\n\n")
                    .append("Detalles de tu pedido:\n")
                    .append("--------------------------------------------------\n")
                    .append("Código de pedido: #").append(nroBoleta).append("\n")
                    .append("Fecha de compra: ").append(formatearFecha(pedido)).append("\n");

            if (pedido.getDireccionEnvio() != null && !pedido.getDireccionEnvio().trim().isEmpty()) {
                if (pedido.isEsUrgente()) {
                    contenido.append("Dirección de envío a domicilio: ").append(pedido.getDireccionEnvio()).append("\n");
                } else {
                    contenido.append("Establecimiento de recojo: ").append(pedido.getDireccionEnvio()).append("\n");
                }
            }

            agregarResumenPedido(contenido, pedido, detalles, codigoCupon);

            if (pedido.getDireccionEnvio() != null && !pedido.getDireccionEnvio().trim().isEmpty()) {
                if (pedido.isEsUrgente()) {
                    contenido.append("Tu pedido urgente está siendo enviado a tu domicilio.\n\n");
                } else {
                    contenido.append("Tu pedido está siendo enviado al punto de recojo seleccionado para que puedas retirarlo.\n\n");
                }
            } else {
                contenido.append("Tu pedido ya está siendo preparado para su envío.\n\n");
            }

            contenido.append("Si tienes alguna duda o consulta, puedes responder a este correo o escribir a soporte@boticaanita.com.\n\n")
                    .append("Saludos,\nEquipo BoticaAnita");

            message.setText(contenido.toString());
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de confirmación de pedido: " + e.getMessage());
        }
    }

    // ───────────────────────── Verificación de compra: Plin y SIP ─────────────────────────

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Costo del delivery; el recojo en tienda es gratis (mismo valor que usa PagoService). */
    private static final BigDecimal COSTO_DELIVERY = new BigDecimal("5.00");

    private boolean esTransferencia(String metodoPago) {
        return "PLIN".equalsIgnoreCase(metodoPago) || "SIP".equalsIgnoreCase(metodoPago);
    }

    /**
     * Correo de verificación de la compra para pagos por Plin o SIP.
     * Resume el pago (método, número de operación y monto) y el pedido, para que el cliente
     * lo guarde como comprobante y lo compare con la constancia de su transferencia.
     */
    public void sendTransferPurchaseVerificationEmail(String destinatario, String nombre, Pedido pedido,
            List<DetallePedido> detalles, String codigoCupon, String nroBoleta) {
        try {
            String metodo = "SIP".equalsIgnoreCase(pedido.getMetodoPago()) ? "SIP" : "Plin";

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Verificación de tu compra #" + nroBoleta + " - BoticaAnita");

            StringBuilder contenido = new StringBuilder();
            contenido.append("Hola ").append(nombre).append(",\n\n")
                    .append("Recibimos tu pago por ").append(metodo)
                    .append(" y registramos tu compra. Este correo es la verificación de tu compra.\n\n")
                    .append("Datos del pago:\n")
                    .append("--------------------------------------------------\n")
                    .append("Método de pago: ").append(metodo).append("\n")
                    .append("Número de operación: ")
                    .append(pedido.getNumeroOperacion() == null ? "-" : pedido.getNumeroOperacion()).append("\n")
                    .append("Monto: ").append(soles(pedido.getTotal())).append("\n")
                    .append("Fecha: ").append(formatearFecha(pedido)).append("\n")
                    .append("Código de pedido: #").append(nroBoleta).append("\n");

            if (pedido.getDireccionEnvio() != null && !pedido.getDireccionEnvio().trim().isEmpty()) {
                contenido.append(pedido.isEsUrgente() ? "Dirección de envío a domicilio: " : "Establecimiento de recojo: ")
                        .append(pedido.getDireccionEnvio()).append("\n");
            }

            agregarResumenPedido(contenido, pedido, detalles, codigoCupon);

            if ("EN_ESPERA".equals(pedido.getEstado())) {
                contenido.append("Tu pedido contiene productos que requieren receta médica, por lo que quedará en espera ")
                        .append("hasta validar tu receta.\n\n");
            } else if (pedido.isEsUrgente()) {
                contenido.append("Tu pedido está siendo preparado para enviarse a tu domicilio.\n\n");
            } else {
                contenido.append("Tu pedido está siendo preparado para que lo recojas en la tienda seleccionada.\n\n");
            }

            contenido.append("Guarda este correo y tu número de operación como comprobante. ")
                    .append("Si algún dato no coincide con la constancia de tu transferencia, ")
                    .append("responde a este correo o escribe a soporte@boticaanita.com indicando tu número de operación.\n\n")
                    .append("Saludos,\nEquipo BoticaAnita");

            message.setText(contenido.toString());
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de verificación de compra (Plin/SIP): " + e.getMessage());
        }
    }

    /**
     * Productos, envío, descuentos y total del pedido (compartido por los correos de compra).
     * Los descuentos (promociones y cupón) se calculan como la diferencia entre lo que suman
     * los productos más el envío y el total realmente cobrado.
     */
    private void agregarResumenPedido(StringBuilder contenido, Pedido pedido, List<DetallePedido> detalles,
            String codigoCupon) {
        contenido.append("\nProductos:\n");

        BigDecimal subtotalProductos = BigDecimal.ZERO;
        for (DetallePedido det : detalles) {
            BigDecimal subtotalProducto = det.getPrecioHistorico().multiply(BigDecimal.valueOf(det.getCantidad()));
            subtotalProductos = subtotalProductos.add(subtotalProducto);
            contenido.append("- ")
                    .append(det.getProducto().getNombre())
                    .append(" x")
                    .append(det.getCantidad())
                    .append(" : ")
                    .append(soles(subtotalProducto))
                    .append("\n");
        }

        BigDecimal envio = pedido.isEsUrgente() ? COSTO_DELIVERY : BigDecimal.ZERO;
        BigDecimal descuentos = subtotalProductos.add(envio).subtract(pedido.getTotal());

        contenido.append("\n")
                .append("Costo de envío: ")
                .append(pedido.isEsUrgente() ? soles(envio) + " (delivery)" : "Gratis (recojo en tienda)")
                .append("\n");
        if (descuentos.compareTo(new BigDecimal("0.01")) >= 0) {
            contenido.append("Descuentos aplicados (promociones y cupón): -").append(soles(descuentos)).append("\n");
        }
        if (codigoCupon != null && !codigoCupon.trim().isEmpty()) {
            contenido.append("Cupón aplicado: ").append(codigoCupon.toUpperCase()).append("\n");
        }
        contenido.append("Total pagado: ").append(soles(pedido.getTotal())).append("\n")
                .append("--------------------------------------------------\n\n");
    }

    private String soles(BigDecimal monto) {
        return "S/ " + (monto == null ? BigDecimal.ZERO : monto).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** La fecha del pedido se guarda en UTC; en el correo se muestra en hora de Lima. */
    private String formatearFecha(Pedido pedido) {
        if (pedido.getFecha() == null) {
            return "-";
        }
        return pedido.getFecha().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONA_LIMA).format(FORMATO_FECHA);
    }

    /**
     * Envía un correo al usuario cuando su pedido pasa a estado "EN CAMINO"
     * (el repartidor ya salió de la tienda hacia su domicilio).
     *
     * @param destinatario email del usuario.
     * @param nombre       nombre del usuario.
     * @param pedido       pedido en camino.
     * @param nroBoleta    número de boleta/pedido.
     */
    public void sendEnCaminoEmail(String destinatario, String nombre, Pedido pedido, String nroBoleta) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Tu pedido #" + nroBoleta + " está en camino - BoticaAnita");

            StringBuilder contenido = new StringBuilder();
            contenido.append("Hola ").append(nombre).append(",\n\n")
                    .append("¡Buenas noticias! Tu pedido ya está en camino.\n")
                    .append("El repartidor ha salido de la tienda y se dirige a tu domicilio.\n\n")
                    .append("Detalles de tu pedido:\n")
                    .append("--------------------------------------------------\n")
                    .append("Código de pedido: #").append(nroBoleta).append("\n")
                    .append("Dirección de envío: ").append(pedido.getDireccionEnvio() != null ? pedido.getDireccionEnvio() : "No especificada").append("\n")
                    .append("Distrito: ").append(pedido.getDistrito() != null ? pedido.getDistrito() : "No especificado").append("\n");

            if (pedido.getLatitudEntrega() != null && pedido.getLongitudEntrega() != null) {
                contenido.append("Ubicación de entrega: https://www.google.com/maps?q=")
                        .append(pedido.getLatitudEntrega()).append(",").append(pedido.getLongitudEntrega()).append("\n");
            }

            contenido.append("\n")
                    .append("Puedes seguir el recorrido en tiempo real desde tu cuenta en BoticaAnita.\n\n")
                    .append("Si tienes alguna duda o consulta, puedes responder a este correo o escribir a soporte@boticaanita.com.\n\n")
                    .append("Saludos,\nEquipo BoticaAnita");

            message.setText(contenido.toString());
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email 'en camino': " + e.getMessage());
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
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("¡Regalo especial para ti! Cupón de Descuento en BoticaAnita");

            String contenido = "Hola " + nombre + ",\n\n" +
                    "Queremos premiar tu preferencia y por ello te obsequiamos un cupón de descuento exclusivo:\n\n" +
                    "Cupón: " + codigoCupon + "\n" +
                    "Descuento: " + (int)descuento + "% de descuento en tu próxima compra.\n" +
                    "Campaña: " + descripcion + "\n\n" +
                    "Este cupón es de un solo uso.\n\n" +
                    "¿Cómo aplicarlo?\n" +
                    "1. Ingresa a la tienda de BoticaAnita y agrega tus productos.\n" +
                    "2. En el checkout, introduce el código del cupón antes de proceder con el pago.\n" +
                    "3. ¡Listo! Se aplicará el descuento automáticamente.\n\n" +
                    "Saludos cordiales,\nEquipo BoticaAnita";

            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Error enviando email de cupón de campaña: " + e.getMessage());
        }
    }
}