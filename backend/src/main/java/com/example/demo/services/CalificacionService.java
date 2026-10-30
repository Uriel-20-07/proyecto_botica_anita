package com.example.demo.services;

import com.example.demo.models.Calificacion;
import com.example.demo.models.Pedido;
import com.example.demo.models.User;
import com.example.demo.repositories.CalificacionRepository;
import com.example.demo.repositories.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lógica de la satisfacción del cliente: decide qué pedido puede calificarse,
 * registra la calificación y arma el resumen que se muestra en el dashboard.
 */
@Service
public class CalificacionService {

    /** Estados en los que el cliente puede calificar su pedido. */
    private static final Set<String> ESTADOS_CALIFICABLES = Set.of("COMPLETADO", "LISTO_RECOJO");
    /** Solo se pregunta por pedidos recientes, para no insistir con compras antiguas. */
    private static final long DIAS_VIGENCIA = 30;
    private static final int MAX_COMENTARIO = 500;
    private static final int SEMANAS_TENDENCIA = 8;
    private static final int MAX_COMENTARIOS_RECIENTES = 8;

    @Autowired
    private CalificacionRepository calificacionRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    /**
     * Devuelve el pedido más reciente del cliente que ya puede calificarse
     * (listo para recojo o completado) y que todavía no tiene calificación.
     */
    @Transactional(readOnly = true)
    public Optional<Pedido> buscarPedidoPendiente(User usuario) {
        Set<Integer> calificados = calificacionRepository.findByUsuario(usuario).stream()
                .map(c -> c.getPedido().getIdPedido())
                .collect(Collectors.toSet());
        LocalDateTime limite = LocalDateTime.now(ZoneOffset.UTC).minusDays(DIAS_VIGENCIA);

        return pedidoRepository.findByUsuarioOrderByFechaDesc(usuario).stream()
                .filter(p -> ESTADOS_CALIFICABLES.contains(p.getEstado()))
                .filter(p -> p.getFecha() == null || p.getFecha().isAfter(limite))
                .filter(p -> !calificados.contains(p.getIdPedido()))
                .findFirst();
    }

    /** Registra la calificación de un pedido del cliente. */
    @Transactional
    public Calificacion registrar(User usuario, Integer idPedido, Integer estrellas, String comentario) {
        if (idPedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el pedido a calificar");
        }
        if (estrellas == null || estrellas < 1 || estrellas > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La calificación debe ser de 1 a 5 estrellas");
        }
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (pedido.getUsuario() == null || !Objects.equals(pedido.getUsuario().getId(), usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este pedido no te pertenece");
        }
        if (!ESTADOS_CALIFICABLES.contains(pedido.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo puedes calificar pedidos listos para recojo o completados");
        }
        if (calificacionRepository.existsByPedido_IdPedido(idPedido)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido ya fue calificado");
        }

        String texto = comentario == null ? null : comentario.trim();
        if (texto != null && texto.isEmpty()) {
            texto = null;
        }
        if (texto != null && texto.length() > MAX_COMENTARIO) {
            texto = texto.substring(0, MAX_COMENTARIO);
        }

        Calificacion calificacion = new Calificacion();
        calificacion.setPedido(pedido);
        calificacion.setUsuario(usuario);
        calificacion.setEstrellas(estrellas);
        calificacion.setComentario(texto);
        calificacion.setFechaCreacion(LocalDateTime.now());
        return calificacionRepository.save(calificacion);
    }

    /**
     * Resumen para el dashboard: promedio, distribución 1–5, tendencia semanal
     * (últimas 8 semanas) y comentarios recientes.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        List<Calificacion> todas = calificacionRepository.findAllByOrderByFechaCreacionDesc();

        int[] conteo = new int[5];
        double suma = 0;
        for (Calificacion c : todas) {
            int e = c.getEstrellas();
            if (e >= 1 && e <= 5) {
                conteo[e - 1]++;
                suma += e;
            }
        }
        int total = Arrays.stream(conteo).sum();

        // Distribución de 5 a 1 estrellas
        List<Map<String, Object>> distribucion = new ArrayList<>();
        for (int e = 5; e >= 1; e--) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("estrellas", e);
            fila.put("cantidad", conteo[e - 1]);
            fila.put("porcentaje", total == 0 ? 0.0 : redondear(conteo[e - 1] * 100.0 / total, 1));
            distribucion.add(fila);
        }

        // Tendencia: promedio por semana (lunes a domingo) de las últimas semanas
        LocalDate lunesActual = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Map<String, Object>> tendencia = new ArrayList<>();
        for (int k = SEMANAS_TENDENCIA - 1; k >= 0; k--) {
            LocalDate inicio = lunesActual.minusWeeks(k);
            LocalDate fin = inicio.plusDays(7);
            List<Integer> deLaSemana = todas.stream()
                    .filter(c -> {
                        LocalDate d = c.getFechaCreacion().toLocalDate();
                        return !d.isBefore(inicio) && d.isBefore(fin);
                    })
                    .map(Calificacion::getEstrellas)
                    .collect(Collectors.toList());
            Map<String, Object> semana = new LinkedHashMap<>();
            semana.put("semana", inicio.toString());
            semana.put("cantidad", deLaSemana.size());
            semana.put("promedio", deLaSemana.isEmpty() ? null
                    : redondear(deLaSemana.stream().mapToInt(Integer::intValue).average().orElse(0), 2));
            tendencia.add(semana);
        }

        // Comentarios recientes (solo las calificaciones que dejaron texto)
        List<Map<String, Object>> comentarios = todas.stream()
                .filter(c -> c.getComentario() != null && !c.getComentario().isBlank())
                .limit(MAX_COMENTARIOS_RECIENTES)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("estrellas", c.getEstrellas());
                    m.put("comentario", c.getComentario());
                    m.put("fecha", c.getFechaCreacion().toString());
                    m.put("cliente", nombreCorto(c.getUsuario()));
                    m.put("idPedido", c.getPedido().getIdPedido());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("total", total);
        resultado.put("promedio", total == 0 ? 0.0 : redondear(suma / total, 2));
        resultado.put("satisfechosPorcentaje",
                total == 0 ? 0.0 : redondear((conteo[3] + conteo[4]) * 100.0 / total, 1));
        resultado.put("distribucion", distribucion);
        resultado.put("tendencia", tendencia);
        resultado.put("comentarios", comentarios);
        return resultado;
    }

    /** "Aldo Javier Pérez" → "Aldo P." (en el dashboard no se muestra el nombre completo). */
    private String nombreCorto(User u) {
        if (u == null) {
            return "Cliente";
        }
        String nombre = u.getNombre() == null ? "" : u.getNombre().trim();
        String primero = nombre.isEmpty() ? "Cliente" : nombre.split("\\s+")[0];
        String apellido = u.getApellido() == null ? "" : u.getApellido().trim();
        return apellido.isEmpty() ? primero : primero + " " + apellido.charAt(0) + ".";
    }

    private double redondear(double valor, int decimales) {
        double factor = Math.pow(10, decimales);
        return Math.round(valor * factor) / factor;
    }
}
