package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dto.ItemPedidoEmailDTO;
import com.example.demo.dto.PedidoEmailDTO;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

/**
 * Arma los correos con un servidor SMTP simulado (no se envía nada) y revisa su contenido.
 * Además deja una copia en target/correos/ para poder abrirlos en el navegador y revisar cómo se ven.
 */
class EmailServiceTest {

    /** "Servidor" de correo que solo guarda los mensajes. */
    private static final class CorreoFalso extends JavaMailSenderImpl {
        final List<MimeMessage> enviados = new ArrayList<>();

        @Override
        public void send(MimeMessage... mensajes) {
            enviados.addAll(List.of(mensajes));
        }
    }

    private final CorreoFalso correo = new CorreoFalso();
    private final EmailService servicio = crearServicio();

    private EmailService crearServicio() {
        EmailService s = new EmailService();
        ReflectionTestUtils.setField(s, "mailSender", correo);
        return s;
    }

    private static PedidoEmailDTO pedidoDeEjemplo() {
        return PedidoEmailDTO.builder()
                .destinatario("cliente@ejemplo.com")
                .nombreCliente("Uriel Alejos")
                .numeroPedido("000004")
                .fecha(LocalDateTime.of(2026, 11, 28, 7, 23, 35, 720_147_400)) // UTC → 02:23 en Lima
                .direccionEnvio("Av. Maracuyá 123, Independencia, Independencia")
                .esUrgente(true)
                .distrito("Independencia")
                .total(new BigDecimal("124.96"))
                .codigoCupon("bienvenida30")
                .items(List.of(
                        item("Paracetamol 500 mg tabletas", null, 1, "4.40"),
                        item("Panadol Antigripal cápsulas", "Blíster x10", 1, "5.36"),
                        item("Panadol Antigripal cápsulas", "Caja x100", 1, "46.40"),
                        item("Pañales Pampers Pants XG x 44", null, 1, "58.00"),
                        item("Pantoprazol 40 mg x 14 tabletas", null, 2, "6.4")))
                .build();
    }

    private static ItemPedidoEmailDTO item(String nombre, String presentacion, int cantidad, String precio) {
        return ItemPedidoEmailDTO.builder().nombre(nombre).presentacion(presentacion).cantidad(cantidad)
                .precioUnitario(new BigDecimal(precio)).build();
    }

    /** HTML del último correo enviado; también lo guarda en target/correos/<archivo>.html. */
    private String ultimoHtml(String archivo) throws Exception {
        assertEquals(1, correo.enviados.size(), "debía enviarse exactamente un correo");
        // Se serializa y se vuelve a leer, igual que lo recibiría un cliente de correo
        ByteArrayOutputStream crudo = new ByteArrayOutputStream();
        correo.enviados.get(0).writeTo(crudo);
        MimeMessage m = new MimeMessage(null, new java.io.ByteArrayInputStream(crudo.toByteArray()));
        String html = buscarHtml(m);
        Path destino = Path.of("target", "correos", archivo + ".html");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, html, StandardCharsets.UTF_8);
        correo.enviados.clear();
        return html;
    }

    private static String buscarHtml(Part parte) throws Exception {
        if (parte.isMimeType("text/html")) {
            return (String) parte.getContent();
        }
        if (parte.getContent() instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                String html = buscarHtml(mp.getBodyPart(i));
                if (html != null) {
                    return html;
                }
            }
        }
        return null;
    }

    @Test
    void confirmacionDeCompraLegibleEnElCelular() throws Exception {
        servicio.sendOrderConfirmationEmail(pedidoDeEjemplo());
        MimeMessage m = correo.enviados.get(0);
        assertEquals("Confirmación de tu compra #000004 - BoticaAnita", m.getSubject());

        String html = ultimoHtml("confirmacion");
        assertTrue(html.contains("name='viewport'"));
        assertTrue(html.contains("28 de noviembre de 2026, 02:23"), "fecha en hora de Lima y en español");
        assertFalse(html.contains("T07:23"), "sin fecha ISO");
        assertTrue(html.contains("Blíster x10") && html.contains("Caja x100"), "se ve la presentación");
        assertTrue(html.contains("2 × S/ 6.40"), "precios con 2 decimales");
        assertTrue(html.contains("1 unidad"), "con 1 unidad no se repite el precio");
        assertTrue(html.contains("Dirección de envío"));
        // 4.40 + 5.36 + 46.40 + 58.00 + 12.80 = 126.96; + 5.00 de envío − 124.96 pagado = 7.00 de descuento
        assertTrue(html.contains("S/ 126.96") && html.contains("− S/ 7.00") && html.contains("S/ 124.96"));
        assertTrue(html.contains("BIENVENIDA30"));
        assertFalse(html.contains("<th"), "sin tabla de 4 columnas");
    }

    @Test
    void escapaLosDatosDelCliente() throws Exception {
        PedidoEmailDTO dto = pedidoDeEjemplo();
        dto.setNombreCliente("<script>x</script>");
        servicio.sendOrderConfirmationEmail(dto);
        String html = ultimoHtml("escape");
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void generaLosDemasCorreos() throws Exception {
        PedidoEmailDTO dto = pedidoDeEjemplo();

        servicio.sendEnCaminoEmail(dto);
        assertTrue(ultimoHtml("en-camino").contains("está en camino"));

        dto.setMetodoPago("YAPE");
        servicio.sendBoletaPresencialEmail(dto);
        assertTrue(ultimoHtml("boleta-presencial").contains("Yape"));

        servicio.sendEntregadoEmail(dto);
        assertTrue(ultimoHtml("entregado").contains("S/ 124.96"));

        servicio.sendPedidoSuspendidoEmail(dto);
        assertTrue(ultimoHtml("suspendido").contains("Cliente ausente"));

        servicio.sendPedidoCanceladoEmail(dto);
        assertTrue(ultimoHtml("cancelado").contains("Receta médica incorrecta"));

        assertTrue(servicio.sendVerificationCodeEmail("a@b.com", "482913", 10));
        assertTrue(ultimoHtml("codigo-verificacion").contains("482913"));

        servicio.sendPasswordResetEmail("a@b.com", "ABC123");
        assertTrue(ultimoHtml("recuperar-contrasena").contains("contraseña"));

        servicio.sendWelcomeCouponEmail("a@b.com", "Uriel", "BIENV-7X2K");
        assertTrue(ultimoHtml("cupon-bienvenida").contains("BIENV-7X2K"));

        servicio.sendCampaignCouponEmail("a@b.com", "Uriel", "NAVIDAD15", 15, "Campaña de Navidad");
        assertTrue(ultimoHtml("cupon-campana").contains("15% de descuento"));
    }

    @Test
    void elAsuntoLlevaTildesEnUtf8() throws Exception {
        servicio.sendEnCaminoEmail(pedidoDeEjemplo());
        ByteArrayOutputStream crudo = new ByteArrayOutputStream();
        correo.enviados.get(0).writeTo(crudo);
        assertEquals("Tu pedido #000004 está en camino - BoticaAnita", correo.enviados.get(0).getSubject());
        assertTrue(crudo.toString(StandardCharsets.US_ASCII).contains("UTF-8"));
    }
}
