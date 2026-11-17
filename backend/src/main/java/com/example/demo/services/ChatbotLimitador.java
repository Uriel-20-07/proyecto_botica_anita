package com.example.demo.services;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Protege la cuota de Gemini limitando cuántos mensajes escritos puede enviar cada cliente.
 *
 * Solo se cuentan los mensajes que llegarían a Gemini. El flujo guiado por botones no pasa por aquí,
 * porque son consultas pequeñas a la base de datos y no gastan cuota.
 *
 * Se aplican tres límites, configurables en application.properties (todos opcionales):
 *   chatbot.limite.por-minuto       (5)   mensajes por minuto de un mismo usuario: frena ráfagas.
 *   chatbot.limite.por-dia-usuario  (30)  mensajes por día de un mismo usuario.
 *   chatbot.limite.global-dia       (400) mensajes por día entre todos los usuarios. Conviene dejarlo por
 *                                         debajo de la cuota diaria del modelo de respaldo, para no llegar
 *                                         nunca al error 429 de Google.
 *
 * Además permite pausar todas las llamadas a Gemini un rato (pausarGemini) cuando Google informa que la
 * cuota ya se agotó, para no seguir insistiendo.
 *
 * Todo vive en memoria: se reinicia al reiniciar el servicio y cada instancia cuenta por separado.
 * Con una sola instancia (Azure App Service básico) es suficiente; si algún día se escala a varias,
 * habría que mover estos contadores a una base de datos compartida.
 *
 * "Día" significa día calendario en la hora de Lima.
 */
@Component
public class ChatbotLimitador {

    public enum Resultado {
        PERMITIDO,
        DEMASIADO_RAPIDO,
        LIMITE_DIARIO_USUARIO,
        LIMITE_DIARIO_GLOBAL,
        GEMINI_PAUSADO
    }

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final long VENTANA_MS = 60_000L;
    private static final int PURGAR_CADA = 500;

    private final int porMinuto;
    private final int porDiaUsuario;
    private final int globalDia;
    private final Clock reloj;

    /** Consumo de un usuario. Solo se toca dentro de bloques synchronized del limitador. */
    private static final class Consumo {
        final ArrayDeque<Long> ultimoMinuto = new ArrayDeque<>();
        LocalDate dia;
        int delDia;
    }

    private final Map<String, Consumo> consumos = new HashMap<>();
    private LocalDate diaGlobal;
    private int usadosGlobal;
    private long geminiPausadoHasta;
    private int intentos;

    @Autowired
    public ChatbotLimitador(
            @Value("${chatbot.limite.por-minuto:5}") int porMinuto,
            @Value("${chatbot.limite.por-dia-usuario:30}") int porDiaUsuario,
            @Value("${chatbot.limite.global-dia:400}") int globalDia) {
        this(porMinuto, porDiaUsuario, globalDia, Clock.system(ZONA));
    }

    /** Constructor para pruebas: permite controlar el reloj. */
    ChatbotLimitador(int porMinuto, int porDiaUsuario, int globalDia, Clock reloj) {
        this.porMinuto = Math.max(1, porMinuto);
        this.porDiaUsuario = Math.max(1, porDiaUsuario);
        this.globalDia = Math.max(1, globalDia);
        this.reloj = reloj;
    }

    /**
     * Pide permiso para enviar un mensaje a Gemini en nombre de un usuario. Si lo concede, lo cuenta.
     * Un mensaje rechazado no gasta cuota de ningún límite.
     *
     * El orden de las comprobaciones evita mensajes engañosos: si ya no queda cuota global, nadie puede
     * escribir, así que no se le dice a un usuario que "va muy rápido".
     */
    public synchronized Resultado intentar(String usuario) {
        long ahora = reloj.millis();
        LocalDate hoy = LocalDate.now(reloj);

        if (ahora < geminiPausadoHasta) {
            return Resultado.GEMINI_PAUSADO;
        }

        if (++intentos % PURGAR_CADA == 0) {
            purgar(ahora, hoy);
        }

        if (!hoy.equals(diaGlobal)) {
            diaGlobal = hoy;
            usadosGlobal = 0;
        }

        Consumo c = consumos.computeIfAbsent(usuario, k -> new Consumo());
        if (!hoy.equals(c.dia)) {
            c.dia = hoy;
            c.delDia = 0;
        }
        while (!c.ultimoMinuto.isEmpty() && ahora - c.ultimoMinuto.peekFirst() >= VENTANA_MS) {
            c.ultimoMinuto.pollFirst();
        }

        if (usadosGlobal >= globalDia) {
            return Resultado.LIMITE_DIARIO_GLOBAL;
        }
        if (c.delDia >= porDiaUsuario) {
            return Resultado.LIMITE_DIARIO_USUARIO;
        }
        if (c.ultimoMinuto.size() >= porMinuto) {
            return Resultado.DEMASIADO_RAPIDO;
        }

        c.ultimoMinuto.addLast(ahora);
        c.delDia++;
        usadosGlobal++;
        return Resultado.PERMITIDO;
    }

    /** Detiene todas las llamadas a Gemini durante el tiempo indicado (por ejemplo, tras un 429 de Google). */
    public synchronized void pausarGemini(Duration duracion) {
        geminiPausadoHasta = reloj.millis() + duracion.toMillis();
    }

    /** Mensajes concedidos hoy entre todos los usuarios (útil para los registros). */
    public synchronized int usadosHoy() {
        return LocalDate.now(reloj).equals(diaGlobal) ? usadosGlobal : 0;
    }

    /** Elimina usuarios inactivos para que el mapa no crezca sin límite. */
    private void purgar(long ahora, LocalDate hoy) {
        consumos.values().removeIf(c -> !hoy.equals(c.dia)
                && (c.ultimoMinuto.isEmpty() || ahora - c.ultimoMinuto.peekLast() >= VENTANA_MS));
    }
}
