package com.example.demo.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.demo.dto.ItemPedidoEmailDTO;
import com.example.demo.dto.PedidoEmailDTO;

import jakarta.mail.internet.MimeMessage;

/**
 * Servicio para el envío de correos electrónicos transaccionales.
 *
 * Todos los correos comparten una misma plantilla HTML pensada para verse bien en el celular:
 * - Solo tablas y estilos en línea (lo único que respetan todos los clientes de correo, incluido Gmail).
 * - Un ancho máximo de 600 px que se reduce al de la pantalla, sin tablas de 4 columnas que se salgan.
 * - Los productos se muestran en 2 columnas: nombre (con la presentación y "cantidad × precio" debajo) y subtotal.
 * - Las fechas se muestran en hora de Lima y en español; los montos siempre con 2 decimales.
 * - Los datos que escribe el cliente (nombre, dirección) se escapan para que no rompan el HTML.
 *
 * Los correos de pedidos se envían de forma asíncrona (@Async) y reciben únicamente DTOs con datos planos,
 * para no acceder a entidades JPA desde otro hilo. Cualquier fallo de SMTP se registra en el log y nunca se
 * propaga a la respuesta del cliente.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String REMITENTE = "noreply@boticaanita.com";

    /** Correo de contacto que se muestra al cliente en avisos de suspendido/cancelado. */
    private static final String CORREO_CONTACTO = "boticaanita1980@gmail.com";
    private static final String CORREO_SOPORTE = "soporte@boticaanita.com";

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-PE"));

    private static final BigDecimal COSTO_ENVIO_URGENTE = new BigDecimal("5.00");

    // Colores de la marca
    private static final String AZUL_INICIO = "#1a2e4a";
    private static final String AZUL_FIN = "#2d4a7a";
    private static final String NARANJA = "#e85d04";
    private static final String TEXTO = "#334155";
    private static final String TEXTO_SUAVE = "#64748b";

    @Autowired
    private JavaMailSender mailSender;

    // ------------------------------------------------------------------
    // Cuenta: códigos, bienvenida y cupones
    // ------------------------------------------------------------------

    public void sendPasswordResetEmail(String destinatario, String token) {
        String cuerpo = parrafo("Hola,")
                + parrafo("Recibimos una solicitud para recuperar tu contraseña. Usa este código:")
                + cajaCodigo(token)
                + parrafoSuave("El código vence en 1 hora. Si no solicitaste la recuperación, ignora este correo: "
                        + "tu contraseña seguirá siendo la misma.")
                + despedida();
        enviarSeguro(destinatario, "Código para recuperar tu contraseña - BoticaAnita",
                plantilla("Recuperar contraseña", AZUL_INICIO, AZUL_FIN, cuerpo, "Recuperación de contraseña."),
                "recuperación de contraseña");
    }

    public boolean sendVerificationCodeEmail(String destinatario, String codigo, int expiracionMinutos) {
        String cuerpo = parrafo("Hola,")
                + parrafo("Gracias por registrarte en BoticaAnita. Este es tu código de verificación:")
                + cajaCodigo(codigo)
                + parrafoSuave("El código vence en " + expiracionMinutos + " minutos. Si no fuiste tú, ignora este correo.")
                + despedida();
        return enviarSeguro(destinatario, "Confirma tu correo - BoticaAnita",
                plantilla("Confirma tu correo", AZUL_INICIO, AZUL_FIN, cuerpo, "Verificación de correo."),
                "verificación");
    }

    public void sendVerificationEmail(String destinatario, String verificationLink) {
        String cuerpo = parrafo("Hola,")
                + parrafo("Gracias por registrarte en BoticaAnita. Toca el botón para verificar tu correo:")
                + boton(verificationLink, "Verificar mi correo")
                + despedida();
        enviarSeguro(destinatario, "Verifica tu correo - BoticaAnita",
                plantilla("Verifica tu correo", AZUL_INICIO, AZUL_FIN, cuerpo, "Verificación de correo."),
                "verificación por enlace");
    }

    public void sendWelcomeEmail(String destinatario, String nombre) {
        String cuerpo = saludo(nombre)
                + parrafo("Te damos la bienvenida a BoticaAnita. Tu cuenta se creó correctamente.")
                + despedida();
        enviarSeguro(destinatario, "¡Bienvenido a BoticaAnita!",
                plantilla("¡Bienvenido!", AZUL_INICIO, AZUL_FIN, cuerpo, "Bienvenida."),
                "bienvenida");
    }

    public void sendWelcomeCouponEmail(String destinatario, String nombre, String codigoCupon) {
        String cuerpo = saludo(nombre)
                + parrafo("Gracias por unirte a BoticaAnita. Como bienvenida te regalamos un cupón exclusivo "
                        + "con <strong>30% de descuento</strong>:")
                + cajaCodigo(codigoCupon)
                + parrafoSuave("Es válido por 30 días y se puede usar una sola vez. Ingrésalo al momento de pagar.")
                + despedida();
        enviarSeguro(destinatario, "¡Bienvenido a BoticaAnita! Tu cupón de 30% ya está listo",
                plantilla("Tu cupón de bienvenida", AZUL_INICIO, AZUL_FIN, cuerpo, "Cupón de bienvenida."),
                "cupón de bienvenida");
    }

    public void sendCampaignCouponEmail(String destinatario, String nombre, String codigoCupon, double descuento,
                                        String descripcion) {
        String cuerpo = saludo(nombre)
                + parrafo("Queremos premiar tu preferencia con un cupón de <strong>" + (int) descuento
                        + "% de descuento</strong> para tu próxima compra:")
                + cajaCodigo(codigoCupon)
                + (descripcion == null || descripcion.isBlank() ? ""
                        : cajaDatos(filaDato("Campaña", esc(descripcion))))
                + parrafoSuave("Ingrésalo al momento de pagar.")
                + despedida();
        enviarSeguro(destinatario, "¡Un regalo para ti! Cupón de descuento en BoticaAnita",
                plantilla("Un regalo para ti", AZUL_INICIO, AZUL_FIN, cuerpo, "Cupón de campaña."),
                "cupón de campaña");
    }

    // ------------------------------------------------------------------
    // Pedidos
    // ------------------------------------------------------------------

    /**
     * Confirmación de compra. Se ejecuta en el pool "emailTaskExecutor".
     * Recibe datos planos: no accede a entidades ni a la base de datos.
     */
    @Async("emailTaskExecutor")
    public void sendOrderConfirmationEmail(PedidoEmailDTO dto) {
        if (dto == null) {
            return;
        }
        boolean hayDireccion = noVacio(dto.getDireccionEnvio());
        BigDecimal envio = dto.isEsUrgente() ? COSTO_ENVIO_URGENTE : BigDecimal.ZERO;

        StringBuilder datos = new StringBuilder()
                .append(filaDato("N.º de pedido", "#" + esc(dto.getNumeroPedido())))
                .append(filaDato("Fecha", textoFecha(dto.getFecha())));
        if (hayDireccion) {
            datos.append(filaDato(dto.isEsUrgente() ? "Dirección de envío" : "Recojo en",
                    esc(dto.getDireccionEnvio())));
        }

        StringBuilder cuerpo = new StringBuilder()
                .append(saludo(dto.getNombreCliente()))
                .append(parrafo("¡Muchas gracias por tu compra! Tu pago se procesó con éxito."))
                .append(cajaDatos(datos.toString()))
                .append(tablaProductos(itemsDe(dto)))
                .append(resumen(itemsDe(dto), envio, dto.isEsUrgente() ? null : "Gratis",
                        dto.getCodigoCupon(), dto.getTotal()));
        if (hayDireccion) {
            cuerpo.append(parrafoSuave(dto.isEsUrgente()
                    ? "Tu pedido urgente se está preparando para enviarse a tu domicilio."
                    : "Tu pedido se está preparando para el punto de recojo que elegiste."));
        }
        cuerpo.append(ayuda()).append(despedida());

        enviarSeguro(dto.getDestinatario(), "Confirmación de tu compra #" + dto.getNumeroPedido() + " - BoticaAnita",
                plantilla("Confirmación de compra", AZUL_INICIO, AZUL_FIN, cuerpo.toString(), "Confirmación de compra."),
                "confirmación del pedido " + dto.getNumeroPedido());
    }

    /** Aviso de pedido en camino. Se ejecuta de forma asíncrona con datos planos. */
    @Async("emailTaskExecutor")
    public void sendEnCaminoEmail(PedidoEmailDTO dto) {
        if (dto == null) {
            return;
        }
        StringBuilder datos = new StringBuilder()
                .append(filaDato("N.º de pedido", "#" + esc(dto.getNumeroPedido())))
                .append(filaDato("Dirección de envío", noVacio(dto.getDireccionEnvio())
                        ? esc(dto.getDireccionEnvio()) : "No especificada"))
                .append(filaDato("Distrito", noVacio(dto.getDistrito()) ? esc(dto.getDistrito()) : "No especificado"));

        String cuerpo = saludo(dto.getNombreCliente())
                + parrafo("¡Buenas noticias! Tu pedido ya está en camino: el repartidor salió de la tienda "
                        + "y se dirige a tu domicilio.")
                + cajaDatos(datos.toString())
                + (dto.getLatitudEntrega() != null && dto.getLongitudEntrega() != null
                        ? boton("https://www.google.com/maps?q=" + dto.getLatitudEntrega() + "," + dto.getLongitudEntrega(),
                                "Ver la dirección en Google Maps")
                        : "")
                + parrafoSuave("Puedes seguir el recorrido en tiempo real desde tu cuenta en BoticaAnita.")
                + ayuda()
                + despedida();

        enviarSeguro(dto.getDestinatario(), "Tu pedido #" + dto.getNumeroPedido() + " está en camino - BoticaAnita",
                plantilla("Tu pedido está en camino", AZUL_INICIO, AZUL_FIN, cuerpo, "Pedido en camino."),
                "'en camino' del pedido " + dto.getNumeroPedido());
    }

    /** Boleta de venta presencial. Se ejecuta de forma asíncrona con datos planos. */
    @Async("emailTaskExecutor")
    public void sendBoletaPresencialEmail(PedidoEmailDTO dto) {
        if (dto == null) {
            return;
        }
        StringBuilder datos = new StringBuilder()
                .append(filaDato("N.º de boleta", "#" + esc(dto.getNumeroPedido())))
                .append(filaDato("Fecha", textoFecha(dto.getFecha())))
                .append(filaDato("Método de pago", noVacio(dto.getMetodoPago())
                        ? esc(nombreMetodo(dto.getMetodoPago())) : "Efectivo"));

        String cuerpo = saludo(dto.getNombreCliente())
                + parrafo("Gracias por tu compra en nuestra tienda. Este es el detalle:")
                + cajaDatos(datos.toString())
                + tablaProductos(itemsDe(dto))
                + resumen(itemsDe(dto), BigDecimal.ZERO, "Gratis (en tienda)", null, dto.getTotal())
                + ayuda()
                + despedida();

        enviarSeguro(dto.getDestinatario(), "Boleta de venta #" + dto.getNumeroPedido() + " - BoticaAnita",
                plantilla("Boleta de venta presencial", AZUL_INICIO, AZUL_FIN, cuerpo,
                        "Este correo es una boleta de venta presencial."),
                "boleta presencial del pedido " + dto.getNumeroPedido());
    }

    /** Confirmación de entrega. Se ejecuta de forma asíncrona con datos planos. */
    @Async("emailTaskExecutor")
    public void sendEntregadoEmail(PedidoEmailDTO dto) {
        if (dto == null) {
            return;
        }
        StringBuilder datos = new StringBuilder()
                .append(filaDato("N.º de pedido", "#" + esc(dto.getNumeroPedido())))
                .append(filaDato("Dirección de envío", noVacio(dto.getDireccionEnvio())
                        ? esc(dto.getDireccionEnvio()) : "No especificada"))
                .append(filaDato("Distrito", noVacio(dto.getDistrito()) ? esc(dto.getDistrito()) : "No especificado"))
                .append(filaDato("Total", "<strong style='color:" + NARANJA + ";'>S/ " + soles(dto.getTotal()) + "</strong>"));

        String cuerpo = saludo(dto.getNombreCliente())
                + parrafo("Tu pedido fue entregado con éxito. ¡Gracias por confiar en BoticaAnita!")
                + cajaDatos(datos.toString())
                + parrafoSuave("Esperamos verte pronto.")
                + ayuda()
                + despedida();

        enviarSeguro(dto.getDestinatario(), "Tu pedido #" + dto.getNumeroPedido() + " fue entregado - BoticaAnita",
                plantilla("Pedido entregado", "#10b981", "#059669", cuerpo, "Pedido entregado."),
                "'entregado' del pedido " + dto.getNumeroPedido());
    }

    /** Aviso de pedido suspendido (no entregado porque el cliente no estaba). */
    @Async("emailTaskExecutor")
    public void sendPedidoSuspendidoEmail(PedidoEmailDTO dto) {
        enviarPedidoNoEntregado(
                dto,
                "Pedido suspendido",
                "No pudimos entregar tu pedido porque no había nadie en la dirección al momento de la entrega. "
                        + "Coordina con la tienda para reprogramar la entrega.",
                "Cliente ausente en la dirección",
                "#f59e0b",
                "#d97706");
    }

    /** Aviso de pedido cancelado (receta médica incorrecta). */
    @Async("emailTaskExecutor")
    public void sendPedidoCanceladoEmail(PedidoEmailDTO dto) {
        enviarPedidoNoEntregado(
                dto,
                "Pedido cancelado",
                "Tu pedido fue cancelado porque la receta médica presentada no era la correcta. "
                        + "Acércate a la tienda para regularizarlo.",
                "Receta médica incorrecta",
                "#ef4444",
                "#dc2626");
    }

    /**
     * Correo común para pedidos que no se entregaron. Incluye la boleta/pedido,
     * el motivo y el correo de contacto de la botica.
     */
    private void enviarPedidoNoEntregado(PedidoEmailDTO dto, String titulo, String motivo,
                                         String motivoCorto, String colorInicio, String colorFin) {
        if (dto == null) {
            return;
        }
        StringBuilder datos = new StringBuilder()
                .append(filaDato("Boleta / pedido", "#" + esc(dto.getNumeroPedido())))
                .append(filaDato("Motivo", esc(motivoCorto)));
        if (noVacio(dto.getDireccionEnvio())) {
            datos.append(filaDato("Dirección", esc(dto.getDireccionEnvio())));
        }
        datos.append(filaDato("Total", "<strong style='color:" + NARANJA + ";'>S/ " + soles(dto.getTotal()) + "</strong>"));

        String cuerpo = saludo(dto.getNombreCliente())
                + parrafo(esc(motivo))
                + cajaDatos(datos.toString())
                + parrafoSuave("Si tienes alguna duda, escríbenos a <strong>" + CORREO_CONTACTO + "</strong>.")
                + despedida();

        enviarSeguro(dto.getDestinatario(), titulo + " - Pedido #" + dto.getNumeroPedido() + " - BoticaAnita",
                plantilla(titulo, colorInicio, colorFin, cuerpo, titulo + "."),
                "'" + titulo + "' del pedido " + dto.getNumeroPedido());
    }

    // ------------------------------------------------------------------
    // Envío
    // ------------------------------------------------------------------

    /** Envía un correo HTML en UTF-8. Nunca lanza excepciones: si falla, lo registra y devuelve false. */
    private boolean enviarSeguro(String destinatario, String asunto, String html, String descripcion) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(REMITENTE);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            log.error("Error enviando email de {}: {}", descripcion, e.getMessage(), e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Plantilla y bloques HTML (package-private para poder probarlos y previsualizarlos)
    // ------------------------------------------------------------------

    /**
     * Documento completo: fondo gris, tarjeta blanca de hasta 600 px con cabecera de color, el contenido y un pie.
     * La tarjeta ocupa todo el ancho en el celular (width:100% + max-width) y nada tiene ancho fijo mayor.
     */
    static String plantilla(String titulo, String colorInicio, String colorFin, String contenido, String pie) {
        return "<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>BoticaAnita</title></head>"
                + "<body style='margin:0;padding:0;background:#f4f6f9;-webkit-text-size-adjust:100%;'>"
                + "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' "
                + "style='background:#f4f6f9;'><tr><td align='center' style='padding:16px 8px;'>"
                + "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' "
                + "style='max-width:600px;width:100%;background:#ffffff;border-radius:12px;overflow:hidden;"
                + "font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;'>"
                // Cabecera
                + "<tr><td style='background:" + colorInicio + ";background:linear-gradient(135deg," + colorInicio + ","
                + colorFin + ");padding:22px 20px;text-align:center;'>"
                + "<div style='color:#ffffff;font-size:24px;font-weight:700;line-height:1.2;'>BoticaAnita</div>"
                + "<div style='color:#e2e8f0;font-size:14px;margin-top:4px;'>" + esc(titulo) + "</div>"
                + "</td></tr>"
                // Contenido
                + "<tr><td style='padding:20px 18px;color:" + TEXTO + ";font-size:15px;line-height:1.5;'>"
                + contenido
                + "</td></tr>"
                // Pie
                + "<tr><td style='background:#f1f5f9;padding:14px 18px;text-align:center;font-size:12px;color:#94a3b8;'>"
                + "BoticaAnita © " + LocalDateTime.now(ZONA_LIMA).getYear() + " · " + esc(pie)
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    static String saludo(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        return parrafo(limpio.isEmpty() ? "Hola," : "Hola <strong>" + esc(limpio) + "</strong>,");
    }

    static String parrafo(String html) {
        return "<p style='margin:0 0 12px;color:" + TEXTO + ";font-size:15px;line-height:1.5;'>" + html + "</p>";
    }

    static String parrafoSuave(String html) {
        return "<p style='margin:12px 0;color:" + TEXTO_SUAVE + ";font-size:13px;line-height:1.5;'>" + html + "</p>";
    }

    static String despedida() {
        return parrafoSuave("Saludos,<br><strong style='color:" + TEXTO + ";'>Equipo BoticaAnita</strong>");
    }

    static String ayuda() {
        return parrafoSuave("Si tienes alguna duda, responde a este correo o escribe a " + CORREO_SOPORTE + ".");
    }

    /** Código o cupón grande y fácil de copiar. */
    static String cajaCodigo(String codigo) {
        return "<div style='margin:16px 0;padding:16px;background:#f8fafc;border:2px dashed #cbd5e1;border-radius:10px;"
                + "text-align:center;font-size:26px;font-weight:800;letter-spacing:4px;color:" + AZUL_INICIO + ";"
                + "word-break:break-all;'>" + esc(codigo) + "</div>";
    }

    static String boton(String url, String texto) {
        return "<div style='margin:16px 0;text-align:center;'><a href='" + esc(url) + "' "
                + "style='display:inline-block;background:" + AZUL_FIN + ";color:#ffffff;text-decoration:none;"
                + "padding:12px 22px;border-radius:8px;font-weight:600;font-size:15px;'>" + esc(texto) + "</a></div>";
    }

    /** Recuadro gris con filas "etiqueta / valor". table-layout:fixed evita que un texto largo lo ensanche. */
    static String cajaDatos(String filas) {
        return "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' "
                + "style='width:100%;table-layout:fixed;border-collapse:collapse;margin:14px 0;background:#f8fafc;"
                + "border:1px solid #e2e8f0;border-radius:8px;font-size:14px;color:" + TEXTO + ";'>"
                + filas + "</table>";
    }

    /** Fila de cajaDatos. El valor ya debe venir escapado (puede llevar HTML propio, como un monto resaltado). */
    static String filaDato(String etiqueta, String valorHtml) {
        return "<tr>"
                + "<td style='width:42%;padding:8px 6px 8px 12px;vertical-align:top;font-weight:600;color:"
                + TEXTO_SUAVE + ";'>" + esc(etiqueta) + "</td>"
                + "<td style='padding:8px 12px 8px 6px;vertical-align:top;text-align:right;word-break:break-word;"
                + "overflow-wrap:anywhere;'>" + valorHtml + "</td>"
                + "</tr>";
    }

    /**
     * Productos en 2 columnas para que quepan en el celular: a la izquierda el nombre, la presentación y
     * "cantidad × precio unitario"; a la derecha el subtotal.
     */
    static String tablaProductos(List<ItemPedidoEmailDTO> items) {
        StringBuilder html = new StringBuilder()
                .append("<div style='margin:20px 0 6px;font-size:16px;font-weight:700;color:").append(AZUL_INICIO)
                .append(";'>Productos</div>")
                .append("<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' ")
                .append("style='width:100%;table-layout:fixed;border-collapse:collapse;font-size:14px;'>");
        for (ItemPedidoEmailDTO item : items) {
            BigDecimal precio = item.getPrecioUnitario() != null ? item.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(item.getCantidad()));
            html.append("<tr>")
                    .append("<td style='padding:10px 8px 10px 0;border-bottom:1px solid #eef2f6;vertical-align:top;")
                    .append("word-break:break-word;overflow-wrap:anywhere;'>")
                    .append("<div style='color:").append(TEXTO).append(";font-weight:600;'>")
                    .append(esc(item.getNombre())).append("</div>");
            if (noVacio(item.getPresentacion())) {
                html.append("<div style='margin-top:3px;'><span style='display:inline-block;background:#eff6ff;")
                        .append("color:#1d4ed8;border:1px solid #bfdbfe;border-radius:10px;padding:1px 8px;")
                        .append("font-size:12px;font-weight:600;'>").append(esc(item.getPresentacion()))
                        .append("</span></div>");
            }
            html.append("<div style='margin-top:3px;color:").append(TEXTO_SUAVE).append(";font-size:13px;'>")
                    // Con 1 unidad el precio unitario repetiría el subtotal: solo se indica la cantidad
                    .append(item.getCantidad() == 1 ? "1 unidad"
                            : item.getCantidad() + " × S/ " + soles(precio)).append("</div>")
                    .append("</td>")
                    .append("<td style='width:92px;padding:10px 0;border-bottom:1px solid #eef2f6;vertical-align:top;")
                    .append("text-align:right;white-space:nowrap;font-weight:700;color:").append(TEXTO).append(";'>")
                    .append("S/ ").append(soles(subtotal)).append("</td>")
                    .append("</tr>");
        }
        return html.append("</table>").toString();
    }

    /**
     * Totales del pedido. Los descuentos (promociones y cupón) se deducen de la diferencia entre lo que suman los
     * productos más el envío y el total pagado, así siempre cuadra con lo que se cobró.
     *
     * @param textoEnvioGratis texto cuando el envío no tiene costo (null = se muestra el costo aunque sea 0).
     */
    static String resumen(List<ItemPedidoEmailDTO> items, BigDecimal envio, String textoEnvioGratis,
                          String codigoCupon, BigDecimal total) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedidoEmailDTO item : items) {
            BigDecimal precio = item.getPrecioUnitario() != null ? item.getPrecioUnitario() : BigDecimal.ZERO;
            subtotal = subtotal.add(precio.multiply(BigDecimal.valueOf(item.getCantidad())));
        }
        BigDecimal pagado = total != null ? total : subtotal.add(envio);
        BigDecimal descuentos = subtotal.add(envio).subtract(pagado);

        StringBuilder filas = new StringBuilder()
                .append(filaResumen("Subtotal productos", "S/ " + soles(subtotal), TEXTO))
                .append(filaResumen("Envío", envio.signum() == 0 && textoEnvioGratis != null
                        ? esc(textoEnvioGratis) : "S/ " + soles(envio), TEXTO));
        if (noVacio(codigoCupon)) {
            filas.append(filaResumen("Cupón aplicado", esc(codigoCupon.trim().toUpperCase()), TEXTO));
        }
        if (descuentos.compareTo(new BigDecimal("0.01")) >= 0) {
            filas.append(filaResumen("Descuentos", "− S/ " + soles(descuentos), "#16a34a"));
        }
        filas.append("<tr>")
                .append("<td style='padding:10px 0 0;border-top:1px solid #e2e8f0;font-weight:700;font-size:16px;color:")
                .append(AZUL_INICIO).append(";'>Total pagado</td>")
                .append("<td style='padding:10px 0 0;border-top:1px solid #e2e8f0;text-align:right;white-space:nowrap;")
                .append("font-weight:800;font-size:18px;color:").append(NARANJA).append(";'>S/ ").append(soles(pagado))
                .append("</td></tr>");

        return "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' "
                + "style='width:100%;border-collapse:collapse;margin-top:14px;font-size:14px;'>"
                + "<tr><td style='padding:14px 16px;background:#f8fafc;border-radius:8px;'>"
                + "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' "
                + "style='width:100%;border-collapse:collapse;'>" + filas + "</table>"
                + "</td></tr></table>";
    }

    private static String filaResumen(String etiqueta, String valorHtml, String colorValor) {
        return "<tr><td style='padding:4px 0;color:" + TEXTO_SUAVE + ";'>" + esc(etiqueta) + "</td>"
                + "<td style='padding:4px 0;text-align:right;white-space:nowrap;color:" + colorValor + ";'>"
                + valorHtml + "</td></tr>";
    }

    // ------------------------------------------------------------------
    // Formato
    // ------------------------------------------------------------------

    private static List<ItemPedidoEmailDTO> itemsDe(PedidoEmailDTO dto) {
        return dto != null && dto.getItems() != null ? dto.getItems() : List.of();
    }

    /** La fecha del pedido se guarda en UTC; en el correo se muestra en hora de Lima: "28 de noviembre de 2026, 02:23". */
    static String textoFecha(LocalDateTime fechaUtc) {
        if (fechaUtc == null) {
            return "-";
        }
        return fechaUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONA_LIMA).format(FORMATO_FECHA);
    }

    /** Monto con 2 decimales: 4.4 → "4.40". */
    static String soles(BigDecimal monto) {
        return (monto == null ? BigDecimal.ZERO : monto).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nombreMetodo(String metodoPago) {
        switch (metodoPago.trim().toUpperCase(Locale.ROOT)) {
            case "PLIN": return "Plin";
            case "SIP": return "SIP";
            case "YAPE": return "Yape";
            case "TARJETA": return "Tarjeta";
            case "EFECTIVO": return "Efectivo";
            default: return metodoPago;
        }
    }

    private static boolean noVacio(String texto) {
        return texto != null && !texto.isBlank();
    }

    /** Escapa texto para insertarlo en HTML (nombres, direcciones y otros datos que escribe el cliente). */
    static String esc(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
