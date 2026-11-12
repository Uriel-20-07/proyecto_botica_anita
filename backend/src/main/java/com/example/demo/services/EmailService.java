package com.example.demo.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.demo.models.DetallePedido;
import com.example.demo.models.Pedido;

import jakarta.mail.internet.MimeMessage;

/**
 * Servicio para el envio de correos electronicos transaccionales.
 */
@Service
public class EmailService {

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
            System.out.println("Error enviando email: " + e.getMessage());
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
            System.out.println("Error enviando email de verificacion: " + e.getMessage());
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
            System.out.println("Error enviando email de verificacion: " + e.getMessage());
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
            System.out.println("Error enviando email de bienvenida: " + e.getMessage());
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
            System.out.println("Error enviando email de cupon de bienvenida: " + e.getMessage());
        }
    }

    public void sendOrderConfirmationEmail(String destinatario, String nombre, Pedido pedido, List<DetallePedido> detalles, String codigoCupon, String nroBoleta) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(destinatario);
            helper.setSubject("Confirmacion de tu compra #" + nroBoleta + " - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#1a2e4a,#2d4a7a);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#cbd5e1;margin:4px 0 0;font-size:0.9rem;'>Confirmacion de Compra</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(nombre).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Muchas gracias por tu compra. Tu pago ha sido procesado con exito.</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de pedido:</td><td style='padding:4px 0;text-align:right;'>#").append(nroBoleta).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Fecha:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getFecha() != null ? pedido.getFecha().toString() : "-").append("</td></tr>");
            if (pedido.getDireccionEnvio() != null && !pedido.getDireccionEnvio().trim().isEmpty()) {
                if (pedido.isEsUrgente()) {
                    html.append("<tr><td style='padding:4px 0;font-weight:600;'>Direccion de envio:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getDireccionEnvio()).append("</td></tr>");
                } else {
                    html.append("<tr><td style='padding:4px 0;font-weight:600;'>Establecimiento de recojo:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getDireccionEnvio()).append("</td></tr>");
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
            for (DetallePedido det : detalles) {
                java.math.BigDecimal sub = det.getPrecioHistorico().multiply(java.math.BigDecimal.valueOf(det.getCantidad()));
                subtotalProductos += sub.doubleValue();
                html.append("<tr style='border-bottom:1px solid #f1f5f9;'>");
                html.append("<td style='padding:8px;color:#334155;'>").append(det.getProducto().getNombre()).append("</td>");
                html.append("<td style='padding:8px;text-align:center;color:#334155;'>").append(det.getCantidad()).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;'>S/ ").append(det.getPrecioHistorico()).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;font-weight:600;'>S/ ").append(sub).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");

            double costoEnvio = pedido.isEsUrgente() ? 5.0 : 0.0;

            html.append("<div style='margin-top:16px;padding:16px;background:#f8fafc;border-radius:8px;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.95rem;'>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Subtotal productos:</td><td style='padding:4px 0;text-align:right;color:#334155;'>S/ ").append(String.format("%.2f", subtotalProductos)).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Costo de envio:</td><td style='padding:4px 0;text-align:right;color:#334155;'>").append(costoEnvio == 0.0 ? "Gratis" : "S/ 5.00").append("</td></tr>");
            if (codigoCupon != null && !codigoCupon.trim().isEmpty()) {
                html.append("<tr><td style='padding:4px 0;color:#64748b;'>Cupon aplicado:</td><td style='padding:4px 0;text-align:right;color:#334155;'>").append(codigoCupon.toUpperCase()).append("</td></tr>");
            }
            html.append("<tr><td style='padding:8px 0 0;font-weight:700;color:#1a2e4a;font-size:1.1rem;'>Total pagado:</td><td style='padding:8px 0 0;text-align:right;font-weight:800;color:#e85d04;font-size:1.2rem;'>S/ ").append(pedido.getTotal()).append("</td></tr>");
            html.append("</table></div>");

            if (pedido.getDireccionEnvio() != null && !pedido.getDireccionEnvio().trim().isEmpty()) {
                if (pedido.isEsUrgente()) {
                    html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Tu pedido urgente esta siendo enviado a tu domicilio.</p>");
                } else {
                    html.append("<p style='color:#64748b;font-size:0.85rem;margin-top:16px;'>Tu pedido esta siendo enviado al punto de recojo seleccionado.</p>");
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
            System.out.println("Error enviando email de confirmacion de pedido: " + e.getMessage());
        }
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
            System.out.println("Error enviando email de cupon de campana: " + e.getMessage());
        }
    }

    public void sendEnCaminoEmail(String destinatario, String nombre, Pedido pedido, String nroBoleta) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(destinatario);
            helper.setSubject("Tu pedido #" + nroBoleta + " esta en camino - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#1a2e4a,#2d4a7a);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#cbd5e1;margin:4px 0 0;font-size:0.9rem;'>Tu pedido esta en camino</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(nombre).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Buenas noticias! Tu pedido ya esta en camino. El repartidor ha salido de la tienda y se dirige a tu domicilio.</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de pedido:</td><td style='padding:4px 0;text-align:right;'>#").append(nroBoleta).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Direccion de envio:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getDireccionEnvio() != null ? pedido.getDireccionEnvio() : "No especificada").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Distrito:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getDistrito() != null ? pedido.getDistrito() : "No especificado").append("</td></tr>");
            if (pedido.getLatitudEntrega() != null && pedido.getLongitudEntrega() != null) {
                html.append("<tr><td style='padding:4px 0;font-weight:600;'>Ubicacion de entrega:</td><td style='padding:4px 0;text-align:right;'><a href='https://www.google.com/maps?q=").append(pedido.getLatitudEntrega()).append(",").append(pedido.getLongitudEntrega()).append("' style='color:#2563eb;'>Ver en Google Maps</a></td></tr>");
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
            System.out.println("Error enviando email 'en camino': " + e.getMessage());
        }
    }

    public void sendBoletaPresencialEmail(String destinatario, String nombre, Pedido pedido,
                                          List<DetallePedido> detalles, String nroBoleta) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(destinatario);
            helper.setSubject("Boleta de Venta #" + nroBoleta + " - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#1a2e4a,#2d4a7a);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#cbd5e1;margin:4px 0 0;font-size:0.9rem;'>Boleta de Venta Presencial</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(nombre).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Gracias por tu compra en nuestra tienda. Aqui tienes el detalle:</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de boleta:</td><td style='padding:4px 0;text-align:right;'>#").append(nroBoleta).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Fecha:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getFecha() != null ? pedido.getFecha().toString() : "-").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Metodo de pago:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getMetodoPago() != null ? pedido.getMetodoPago() : "Efectivo").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Tipo:</td><td style='padding:4px 0;text-align:right;'>Venta en tienda</td></tr>");
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
            for (DetallePedido det : detalles) {
                java.math.BigDecimal sub = det.getPrecioHistorico().multiply(java.math.BigDecimal.valueOf(det.getCantidad()));
                subtotalProductos += sub.doubleValue();
                html.append("<tr style='border-bottom:1px solid #f1f5f9;'>");
                html.append("<td style='padding:8px;color:#334155;'>").append(det.getProducto().getNombre()).append("</td>");
                html.append("<td style='padding:8px;text-align:center;color:#334155;'>").append(det.getCantidad()).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;'>S/ ").append(det.getPrecioHistorico()).append("</td>");
                html.append("<td style='padding:8px;text-align:right;color:#334155;font-weight:600;'>S/ ").append(sub).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");

            html.append("<div style='margin-top:16px;padding:16px;background:#f8fafc;border-radius:8px;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.95rem;'>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Subtotal productos:</td><td style='padding:4px 0;text-align:right;color:#334155;'>S/ ").append(String.format("%.2f", subtotalProductos)).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;color:#64748b;'>Costo de envio:</td><td style='padding:4px 0;text-align:right;color:#334155;'>Gratis (en tienda)</td></tr>");
            html.append("<tr><td style='padding:8px 0 0;font-weight:700;color:#1a2e4a;font-size:1.1rem;'>Total pagado:</td><td style='padding:8px 0 0;text-align:right;font-weight:800;color:#e85d04;font-size:1.2rem;'>S/ ").append(pedido.getTotal()).append("</td></tr>");
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
            System.out.println("Error enviando boleta presencial HTML: " + e.getMessage());
        }
    }

    public void sendEntregadoEmail(String destinatario, String nombre, Pedido pedido, String nroBoleta) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom("noreply@boticaanita.com");
            helper.setTo(destinatario);
            helper.setSubject("Tu pedido #" + nroBoleta + " ha sido entregado - BoticaAnita");

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;background:#f4f6f9;'>");
            html.append("<div style='max-width:600px;margin:20px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);'>");
            html.append("<div style='background:linear-gradient(135deg,#10b981,#059669);padding:24px;text-align:center;'>");
            html.append("<h1 style='color:#fff;margin:0;font-size:1.6rem;'>BoticaAnita</h1>");
            html.append("<p style='color:#d1fae5;margin:4px 0 0;font-size:0.9rem;'>Pedido Entregado</p>");
            html.append("</div>");
            html.append("<div style='padding:24px;'>");
            html.append("<p style='color:#334155;font-size:1rem;'>Hola <strong>").append(nombre).append("</strong>,</p>");
            html.append("<p style='color:#64748b;font-size:0.95rem;'>Te informamos que tu pedido ha sido entregado exitosamente. Gracias por confiar en BoticaAnita.</p>");

            html.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;margin:16px 0;'>");
            html.append("<table style='width:100%;border-collapse:collapse;font-size:0.9rem;color:#334155;'>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Codigo de pedido:</td><td style='padding:4px 0;text-align:right;'>#").append(nroBoleta).append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Direccion de envio:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getDireccionEnvio() != null ? pedido.getDireccionEnvio() : "No especificada").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Distrito:</td><td style='padding:4px 0;text-align:right;'>").append(pedido.getDistrito() != null ? pedido.getDistrito() : "No especificado").append("</td></tr>");
            html.append("<tr><td style='padding:4px 0;font-weight:600;'>Total:</td><td style='padding:4px 0;text-align:right;font-weight:700;color:#e85d04;'>S/ ").append(pedido.getTotal()).append("</td></tr>");
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
            System.out.println("Error enviando email 'entregado': " + e.getMessage());
        }
    }
}
