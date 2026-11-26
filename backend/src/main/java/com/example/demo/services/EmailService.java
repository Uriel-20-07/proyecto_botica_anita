package com.example.demo.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.demo.models.Pedido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.demo.dto.ItemPedidoEmailDTO;
import com.example.demo.dto.PedidoEmailDTO;

import jakarta.mail.internet.MimeMessage;

/**
 * Servicio para el envio de correos electronicos transaccionales.
 * Los correos de pedidos se envian de forma asincrona (@Async) y reciben
 * unicamente DTOs con datos planos, para no acceder a entidades JPA desde
 * otro hilo. Cualquier fallo de SMTP se registra en el log y nunca se propaga
 * a la respuesta del cliente.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /** Correo de contacto que se muestra al cliente en avisos de suspendido/cancelado. */
    private static final String CORREO_CONTACTO = "boticaanita1980@gmail.com";

    @Autowired
    private JavaMailSender mailSender;

    public void sendPasswordResetEmail(String destinatario, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Codigo de Recuperacion de Contrasena - BoticaAnita");
            String contenido = "Hola,\n\n" +
                    "Hemos recibido una solicitud para recuperar tu contrasena.\n\n" +
                    "Tu codigo de recuperacion es: " + token + "\n\n" +
                    "Este codigo expirara en 1 hora.\n\n" +
                    "Si no solicitaste esta recuperacion, por favor ignora este correo.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";
            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error enviando email de recuperacion de contrasena: {}", e.getMessage(), e);
        }
    }

    public boolean sendVerificationCodeEmail(String destinatario, String codigo, int expiracionMinutos) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Confirma tu correo - BoticaAnita");
            String contenido = "Hola,\n\n" +
                    "Gracias por registrarte en BoticaAnita.\n\n" +
                    "Tu codigo de verificacion es: " + codigo + "\n\n" +
                    "El codigo expirara en " + expiracionMinutos + " minutos.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";
            message.setText(contenido);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Error enviando email de verificacion: {}", e.getMessage(), e);
            return false;
        }
    }

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
            log.error("Error enviando email de verificacion: {}", e.getMessage(), e);
        }
    }

    public void sendWelcomeEmail(String destinatario, String nombre) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Bienvenido a BoticaAnita");
            String contenido = "Hola " + nombre + ",\n\n" +
                    "Te damos la bienvenida a BoticaAnita. " +
                    "Tu cuenta ha sido creada exitosamente.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";
            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error enviando email de bienvenida: {}", e.getMessage(), e);
        }
    }

    public void sendWelcomeCouponEmail(String destinatario, String nombre, String codigoCupon) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Bienvenido a BoticaAnita! Tu cupon de 30% ya esta listo");
            String contenido = "Hola " + nombre + ",\n\n" +
                    "Gracias por unirte a BoticaAnita. Como bienvenida, te hemos creado un cupon exclusivo con 30% de descuento.\n\n" +
                    "Tu codigo es: " + codigoCupon + "\n" +
                    "Es valido por 30 dias y solo puede usarse una vez.\n\n" +
                    "Saludos,\nEquipo BoticaAnita";
            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error enviando email de cupon de bienvenida: {}", e.getMessage(), e);
        }
    }

    /**
     * Confirmacion de compra. Se ejecuta en el pool "emailTaskExecutor".
     * Recibe datos planos: no accede a entidades ni a la base de datos.
     */
    @Async("emailTaskExecutor")
    public void sendOrderConfirmationEmail(PedidoEmailDTO dto) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(dto.getDestinatario());
            helper.setSubject("Confirmacion de tu compra #" + dto.getNumeroPedido() + " - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#1a2e4a,#2d4a7a);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#cbd5e1;margin:4px 0 0;font-size:0.9rem;'>Confirmacion de Compra</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(dto.getNombreCliente()).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Muchas gracias por tu compra. Tu pago ha sido procesado con exito.</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de pedido:</td><td style='padding:4px 0;text-align:right;'>#").append(dto.getNumeroPedido()).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Fecha:</td><td style='padding:4px 0;text-align:right;'>").append(textoFecha(dto.getFecha())).append("</td></tr>");
            if (dto.getDireccionEnvio() != null && !dto.getDireccionEnvio().trim().isEmpty()) {
                if (dto.isEsUrgente()) {
                    html.append("<tr><td style='padding:4px 0;font-weight:600;'>Direccion de envio:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getDireccionEnvio()).append("</td></tr>");
                } else {
                    html.append("<tr><td style='padding:4px 0;font-weight:600;'>Establecimiento de recojo:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getDireccionEnvio()).append("</td></tr>");
                }
            }
            html.append("</table></div>");

            html.append("<h3 style='color:#1a2e4a;font-size:1rem;margin:20px 0 8px;'>Productos</h3>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;'>");
            html.append("<thead><tr style='background:#f1f5f9;'>");
            html.append("<th style='padding:8px;text-align:left;color:#475569;font-weight:600;'>Producto</th>");
            html.append("<th style='padding:8px;text-align:center;color:#475569;font-weight:600;'>Cant.</th>");
            html.append("<th style='padding:8px;text-align:right;color:#475569;font-weight:600;'>P. Unit.</th>");
            html.append("<th style='padding:8px;text-align:right;color:#475569;font-weight:600;'>Subtotal</th>");
            html.append("</tr></thead><tbody>");

            double subtotalProductos = 0.0;
            for (ItemPedidoEmailDTO det : itemsDe(dto)) {
                BigDecimal precio = det.getPrecioUnitario() != null ? det.getPrecioUnitario() : BigDecimal.ZERO;
                BigDecimal sub = precio.multiply(BigDecimal.valueOf(det.getCantidad()));
                subtotalProductos += sub.doubleValue();
                html.append("<tr style='border-bottom:1px solid #f1f5f9;'>");
                html.append("<td style='padding:8px;color:#334155;'>").append(det.getNombre()).append("</td>");
                html.append("<td style='padding:8px;text-align:center;color:#334155;'>").append(det.getCantidad()).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;'>S/ ").append(precio).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;font-weight:600;'>S/ ").append(sub).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");

            double costoEnvio = dto.isEsUrgente() ? 5.0 : 0.0;

            html.append("<div style='margin-top:16px;padding:16px;background:#f8fafc;border-radius:8px;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.95rem;'>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Subtotal productos:</td><td style='padding:4px 0;text-align:right;color:#334155;'>S/ ").append(String.format("%.2f", subtotalProductos)).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Costo de envio:</td><td style='padding:4px 0;text-align:right;color:#334155;'>").append(costoEnvio == 0.0 ? "Gratis" : "S/ 5.00").append("</td></tr>");
            if (dto.getCodigoCupon() != null && !dto.getCodigoCupon().trim().isEmpty()) {
                html.append("<tr><td style='padding:4px 0;color:#64748b;'>Cupon aplicado:</td><td style='padding:4px 0;text-align:right;color:#334155;'>").append(dto.getCodigoCupon().toUpperCase()).append("</td></tr>");
            }
            html.append("<tr><td style='padding:8px 0 0;font-weight:700;color:#1a2e4a;font-size:1.1rem;'>Total pagado:</td><td style='padding:8px 0 0;text-align:right;font-weight:800;color:#e85d04;font-size:1.2rem;'>S/ ").append(textoTotal(dto.getTotal())).append("</td></tr>");
            html.append("</table></div>");

            if (dto.getDireccionEnvio() != null && !dto.getDireccionEnvio().trim().isEmpty()) {
                if (dto.isEsUrgente()) {
                    html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Tu pedido urgente esta siendo enviado a tu domicilio.</p>");
                } else {
                    html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Tu pedido esta siendo enviado al punto de recojo seleccionado.</p>");
                }
            }

            if (dto.getCodigoCupon() != null && !dto.getCodigoCupon().trim().isEmpty()) {
                double descuentos = subtotalProductos + costoEnvio - (dto.getTotal() == null ? 0.0 : dto.getTotal().doubleValue());
                if (descuentos >= 0.01) {
                    html.append("<tr><td style='padding:4px 0;color:#64748b;'>Descuentos (promociones y cupon):</td><td style='padding:4px 0;text-align:right;color:#16a34a;'>- S/ ").append(String.format(java.util.Locale.US, "%.2f", descuentos)).append("</td></tr>");
                }
            }

            html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Si tienes alguna duda, responde a este correo o escribe a soporte@boticaanita.com.</p>");
            html.append("<p style='color:#64748b;font-size:0.85rem;'>Saludos,<br><strong>Equipo BoticaAnita</strong></p>");
            html.append("</div>");
            html.append("<div style='background:#f1f5f9;padding:16px;text-align:center;font-size:0.75rem;color:#94a3b8;'>");
            html.append("BoticaAnita (c) 2026 - Confirmacion de compra.");
            html.append("</div></div></body></html>");

            helper.setText(html.toString(), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Error enviando email de confirmacion del pedido {}: {}", dto != null ? dto.getNumeroPedido() : "-", e.getMessage(), e);
        }
    }

    // ----- Apoyo para el correo de verificacion (Plin, SIP y Yape con QR) -----

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Plin y SIP siempre; Yape solo cuando se pago con QR (tiene numero de operacion). */
    private static boolean esTransferencia(Pedido pedido) {
        String metodo = pedido.getMetodoPago();
        if ("PLIN".equalsIgnoreCase(metodo) || "SIP".equalsIgnoreCase(metodo)) {
            return true;
        }
        return "YAPE".equalsIgnoreCase(metodo)
                && pedido.getNumeroOperacion() != null && !pedido.getNumeroOperacion().trim().isEmpty();
    }

    private String nombreMetodo(String metodoPago) {
        if (metodoPago == null) {
            return "-";
        }
        switch (metodoPago.toUpperCase()) {
            case "PLIN": return "Plin";
            case "SIP": return "SIP";
            case "YAPE": return "Yape";
            case "TARJETA": return "Tarjeta";
            case "EFECTIVO": return "Efectivo";
            default: return metodoPago;
        }
    }

    /** La fecha del pedido se guarda en UTC; en el correo se muestra en hora de Lima. */
    private String formatearFecha(Pedido pedido) {
        if (pedido.getFecha() == null) {
            return "-";
        }
        return pedido.getFecha().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONA_LIMA).format(FORMATO_FECHA);
    }

    public void sendCampaignCouponEmail(String destinatario, String nombre, String codigoCupon, double descuento, String descripcion) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@boticaanita.com");
            message.setTo(destinatario);
            message.setSubject("Regalo especial para ti! Cupon de Descuento en BoticaAnita");
            String contenido = "Hola " + nombre + ",\n\n" +
                    "Queremos premiar tu preferencia y por ello te obsequiamos un cupon de descuento exclusivo:\n\n" +
                    "Cupon: " + codigoCupon + "\n" +
                    "Descuento: " + (int) descuento + "% de descuento en tu proxima compra.\n" +
                    "Campana: " + descripcion + "\n\n" +
                    "Saludos cordiales,\nEquipo BoticaAnita";
            message.setText(contenido);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error enviando email de cupon de campana: {}", e.getMessage(), e);
        }
    }

    /** Aviso de pedido en camino. Se ejecuta de forma asincrona con datos planos. */
    @Async("emailTaskExecutor")
    public void sendEnCaminoEmail(PedidoEmailDTO dto) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(dto.getDestinatario());
            helper.setSubject("Tu pedido #" + dto.getNumeroPedido() + " esta en camino - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#1a2e4a,#2d4a7a);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#cbd5e1;margin:4px 0 0;font-size:0.9rem;'>Tu pedido esta en camino</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(dto.getNombreCliente()).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Buenas noticias! Tu pedido ya esta en camino. El repartidor ha salido de la tienda y se dirige a tu domicilio.</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de pedido:</td><td style='padding:4px 0;text-align:right;'>#").append(dto.getNumeroPedido()).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Direccion de envio:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getDireccionEnvio() != null ? dto.getDireccionEnvio() : "No especificada").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Distrito:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getDistrito() != null ? dto.getDistrito() : "No especificado").append("</td></tr>");
            if (dto.getLatitudEntrega() != null && dto.getLongitudEntrega() != null) {
                html.append("<tr><td style='padding:4px 0;font-weight:600;'>Ubicacion de entrega:</td><td style='padding:4px 0;text-align:right;'><a href='https://www.google.com/maps?q=").append(dto.getLatitudEntrega()).append(",").append(dto.getLongitudEntrega()).append("' style='color:#2563eb;'>Ver en Google Maps</a></td></tr>");
            }
            html.append("</table></div>");

            html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Puedes seguir el recorrido en tiempo real desde tu cuenta en BoticaAnita.</p>");
            html.append("<p style='color:#64748b;font-size:0.85rem;'>Si tienes alguna duda, responde a este correo o escribe a soporte@boticaanita.com.</p>");
            html.append("<p style='color:#64748b;font-size:0.85rem;'>Saludos,<br><strong>Equipo BoticaAnita</strong></p>");
            html.append("</div>");
            html.append("<div style='background:#f1f5f9;padding:16px;text-align:center;font-size:0.75rem;color:#94a3b8;'>");
            html.append("BoticaAnita (c) 2026 - Tu pedido esta en camino.");
            html.append("</div></div></body></html>");

            helper.setText(html.toString(), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Error enviando email 'en camino' del pedido {}: {}", dto != null ? dto.getNumeroPedido() : "-", e.getMessage(), e);
        }
    }

    /** Boleta de venta presencial. Se ejecuta de forma asincrona con datos planos. */
    @Async("emailTaskExecutor")
    public void sendBoletaPresencialEmail(PedidoEmailDTO dto) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(dto.getDestinatario());
            helper.setSubject("Boleta de Venta #" + dto.getNumeroPedido() + " - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#1a2e4a,#2d4a7a);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#cbd5e1;margin:4px 0 0;font-size:0.9rem;'>Boleta de Venta Presencial</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(dto.getNombreCliente()).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Gracias por tu compra en nuestra tienda. Aqui tienes el detalle:</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de boleta:</td><td style='padding:4px 0;text-align:right;'>#").append(dto.getNumeroPedido()).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Fecha:</td><td style='padding:4px 0;text-align:right;'>").append(textoFecha(dto.getFecha())).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Metodo de pago:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getMetodoPago() != null ? dto.getMetodoPago() : "Efectivo").append("</td></tr>");
            html.append("</table></div>");

            html.append("<h3 style='color:#1a2e4a;font-size:1rem;margin:20px 0 8px;'>Productos</h3>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;'>");
            html.append("<thead><tr style='background:#f1f5f9;'>");
            html.append("<th style='padding:8px;text-align:left;color:#475569;font-weight:600;'>Producto</th>");
            html.append("<th style='padding:8px;text-align:center;color:#475569;font-weight:600;'>Cant.</th>");
            html.append("<th style='padding:8px;text-align:right;color:#475569;font-weight:600;'>P. Unit.</th>");
            html.append("<th style='padding:8px;text-align:right;color:#475569;font-weight:600;'>Subtotal</th>");
            html.append("</tr></thead><tbody>");

            double subtotalProductos = 0.0;
            for (ItemPedidoEmailDTO det : itemsDe(dto)) {
                BigDecimal precio = det.getPrecioUnitario() != null ? det.getPrecioUnitario() : BigDecimal.ZERO;
                BigDecimal sub = precio.multiply(BigDecimal.valueOf(det.getCantidad()));
                subtotalProductos += sub.doubleValue();
                html.append("<tr style='border-bottom:1px solid #f1f5f9;'>");
                html.append("<td style='padding:8px;color:#334155;'>").append(det.getNombre()).append("</td>");
                html.append("<td style='padding:8px;text-align:center;color:#334155;'>").append(det.getCantidad()).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;'>S/ ").append(precio).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;font-weight:600;'>S/ ").append(sub).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");

            html.append("<div style='margin-top:16px;padding:16px;background:#f8fafc;border-radius:8px;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.95rem;'>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Subtotal productos:</td><td style='padding:4px 0;text-align:right;color:#334155;'>S/ ").append(String.format("%.2f", subtotalProductos)).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Costo de envio:</td><td style='padding:4px 0;text-align:right;color:#334155;'>Gratis (en tienda)</td></tr>");
            html.append("<tr><td style='padding:8px 0 0;font-weight:700;color:#1a2e4a;font-size:1.1rem;'>Total pagado:</td><td style='padding:8px 0 0;text-align:right;font-weight:800;color:#e85d04;font-size:1.2rem;'>S/ ").append(textoTotal(dto.getTotal())).append("</td></tr>");
            html.append("</table></div>");

            html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:20px;'>Si tienes alguna duda, responde a este correo o escribe a soporte@boticaanita.com.</p>");
            html.append("<p style='color:#64748b;font-size:0.85rem;'>Saludos,<br><strong>Equipo BoticaAnita</strong></p>");
            html.append("</div>");
            html.append("<div style='background:#f1f5f9;padding:16px;text-align:center;font-size:0.75rem;color:#94a3b8;'>");
            html.append("BoticaAnita (c) 2026 - Este correo es una boleta de venta presencial.");
            html.append("</div></div></body></html>");

            helper.setText(html.toString(), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Error enviando boleta presencial del pedido {}: {}", dto != null ? dto.getNumeroPedido() : "-", e.getMessage(), e);
        }
    }

    /** Confirmacion de entrega. Se ejecuta de forma asincrona con datos planos. */
    @Async("emailTaskExecutor")
    public void sendEntregadoEmail(PedidoEmailDTO dto) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(dto.getDestinatario());
            helper.setSubject("Tu pedido #" + dto.getNumeroPedido() + " ha sido entregado - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#10b981,#059669);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#d1fae5;margin:4px 0 0;font-size:0.9rem;'>Pedido Entregado</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(dto.getNombreCliente()).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Te informamos que tu pedido ha sido entregado exitosamente. Gracias por confiar en BoticaAnita.</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de pedido:</td><td style='padding:4px 0;text-align:right;'>#").append(dto.getNumeroPedido()).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Direccion de envio:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getDireccionEnvio() != null ? dto.getDireccionEnvio() : "No especificada").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Distrito:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getDistrito() != null ? dto.getDistrito() : "No especificado").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Total:</td><td style='padding:4px 0;text-align:right;font-weight:700;color:#e85d04;'>S/ ").append(textoTotal(dto.getTotal())).append("</td></tr>");
            html.append("</table></div>");

            html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Esperamos verte pronto. Si tienes alguna duda, responde a este correo o escribe a soporte@boticaanita.com.</p>");
            html.append("<p style='color:#64748b;font-size:0.85rem;'>Saludos,<br><strong>Equipo BoticaAnita</strong></p>");
            html.append("</div>");
            html.append("<div style='background:#f1f5f9;padding:16px;text-align:center;font-size:0.75rem;color:#94a3b8;'>");
            html.append("BoticaAnita (c) 2026 - Pedido entregado.");
            html.append("</div></div></body></html>");

            helper.setText(html.toString(), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Error enviando email 'entregado' del pedido {}: {}", dto != null ? dto.getNumeroPedido() : "-", e.getMessage(), e);
        }
    }

    /** Aviso de pedido suspendido (no entregado porque el cliente no estaba). */
    @Async("emailTaskExecutor")
    public void sendPedidoSuspendidoEmail(PedidoEmailDTO dto) {
        enviarPedidoNoEntregado(
                dto,
                "Pedido suspendido",
                "No pudimos entregar tu pedido porque no habia nadie en la direccion al momento de la entrega. "
                        + "Coordina con la tienda para reprogramar la entrega.",
                "Cliente ausente en la direccion",
                "#f59e0b",
                "#d97706");
    }

    /** Aviso de pedido cancelado (receta medica incorrecta). */
    @Async("emailTaskExecutor")
    public void sendPedidoCanceladoEmail(PedidoEmailDTO dto) {
        enviarPedidoNoEntregado(
                dto,
                "Pedido cancelado",
                "Tu pedido fue cancelado porque la receta medica presentada no era correcta. "
                        + "Acercate a la tienda para regularizarlo.",
                "Receta medica incorrecta",
                "#ef4444",
                "#dc2626");
    }

    /**
     * Correo comun para pedidos que no se entregaron. Incluye la boleta/pedido,
     * el motivo y el correo de contacto de la botica.
     */
    private void enviarPedidoNoEntregado(PedidoEmailDTO dto, String titulo, String motivo,
                                         String motivoCorto, String colorInicio, String colorFin) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(dto.getDestinatario());
            helper.setSubject(titulo + " - Pedido #" + dto.getNumeroPedido() + " - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,").append(colorInicio).append(",").append(colorFin).append(");padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#fff;opacity:.9;margin:4px 0 0;font-size:0.9rem;'>").append(titulo).append("</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(dto.getNombreCliente()).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>").append(motivo).append("</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Boleta / Pedido:</td><td style='padding:4px 0;text-align:right;'>#").append(dto.getNumeroPedido()).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Motivo:</td><td style='padding:4px 0;text-align:right;'>").append(motivoCorto).append("</td></tr>");
            if (dto.getDireccionEnvio() != null && !dto.getDireccionEnvio().isBlank()) {
                html.append("<tr><td style='padding:4px 0;font-weight:600;'>Direccion:</td><td style='padding:4px 0;text-align:right;'>").append(dto.getDireccionEnvio()).append("</td></tr>");
            }
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Total:</td><td style='padding:4px 0;text-align:right;font-weight:700;color:#e85d04;'>S/ ").append(textoTotal(dto.getTotal())).append("</td></tr>");
            html.append("</table></div>");

            html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Si tienes alguna duda, escribenos a <strong>").append(CORREO_CONTACTO).append("</strong>.</p>");
            html.append("<p style='color:#64748b;font-size:0.85rem;'>Saludos,<br><strong>Equipo BoticaAnita</strong></p>");
            html.append("</div>");
            html.append("<div style='background:#f1f5f9;padding:16px;text-align:center;font-size:0.75rem;color:#94a3b8;'>");
            html.append("BoticaAnita (c) 2026 - ").append(titulo).append(".");
            html.append("</div></div></body></html>");

            helper.setText(html.toString(), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Error enviando email '{}' del pedido {}: {}", titulo,
                    dto != null ? dto.getNumeroPedido() : "-", e.getMessage(), e);
        }
    }

    private static List<ItemPedidoEmailDTO> itemsDe(PedidoEmailDTO dto) {
        return dto != null && dto.getItems() != null ? dto.getItems() : List.of();
    }

    private static String textoFecha(LocalDateTime fecha) {
        return fecha != null ? fecha.toString() : "-";
    }

    private static String textoTotal(BigDecimal total) {
        return total != null ? total.toPlainString() : "0.00";
    }
}