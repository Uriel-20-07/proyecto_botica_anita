package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.demo.services.ChatbotLimitador.Resultado;

/**
 * Pruebas del limitador de mensajes del chatbot. No necesitan Spring ni base de datos:
 * un reloj controlable permite "avanzar" el tiempo sin esperar.
 */
class ChatbotLimitadorTest {

    /** Reloj que solo avanza cuando la prueba lo pide. Empieza a mediodía (hora de Lima). */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-15T17:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.ofHours(-5);
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static ChatbotLimitador nuevo(RelojManual reloj, int porMinuto, int porDia, int global) {
        return new ChatbotLimitador(porMinuto, porDia, global, reloj);
    }

    @Test
    void permiteHastaElLimiteDelMinutoYLuegoFrenaLasRafagas() {
        RelojManual reloj = new RelojManual();
        ChatbotLimitador limitador = nuevo(reloj, 3, 100, 100);

        for (int i = 0; i < 3; i++) {
            assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
        }
        assertEquals(Resultado.DEMASIADO_RAPIDO, limitador.intentar("ana@correo.com"));
    }

    @Test
    void vuelvePermitirDespuesDeUnMinuto() {
        RelojManual reloj = new RelojManual();
        ChatbotLimitador limitador = nuevo(reloj, 2, 100, 100);

        limitador.intentar("ana@correo.com");
        limitador.intentar("ana@correo.com");
        assertEquals(Resultado.DEMASIADO_RAPIDO, limitador.intentar("ana@correo.com"));

        reloj.avanzar(Duration.ofSeconds(61));
        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
    }

    @Test
    void cadaUsuarioTieneSuPropioLimite() {
        RelojManual reloj = new RelojManual();
        ChatbotLimitador limitador = nuevo(reloj, 1, 100, 100);

        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
        assertEquals(Resultado.DEMASIADO_RAPIDO, limitador.intentar("ana@correo.com"));
        assertEquals(Resultado.PERMITIDO, limitador.intentar("luis@correo.com"));
    }

    @Test
    void limiteDiarioPorUsuarioSeReiniciaAlDiaSiguiente() {
        RelojManual reloj = new RelojManual();
        ChatbotLimitador limitador = nuevo(reloj, 100, 3, 100);

        for (int i = 0; i < 3; i++) {
            assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
        }
        assertEquals(Resultado.LIMITE_DIARIO_USUARIO, limitador.intentar("ana@correo.com"));

        reloj.avanzar(Duration.ofHours(24));
        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
    }

    @Test
    void limiteGlobalSeComparteEntreUsuariosYTienePrioridad() {
        RelojManual reloj = new RelojManual();
        ChatbotLimitador limitador = nuevo(reloj, 1, 100, 2);

        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
        assertEquals(Resultado.PERMITIDO, limitador.intentar("luis@correo.com"));

        // Una tercera persona nueva choca con el límite global
        assertEquals(Resultado.LIMITE_DIARIO_GLOBAL, limitador.intentar("rosa@correo.com"));
        // Y a quien ya iba rápido no se le dice "vas muy rápido": ya no queda cuota para nadie
        assertEquals(Resultado.LIMITE_DIARIO_GLOBAL, limitador.intentar("ana@correo.com"));

        reloj.avanzar(Duration.ofHours(24));
        assertEquals(Resultado.PERMITIDO, limitador.intentar("rosa@correo.com"));
    }

    @Test
    void unMensajeRechazadoNoGastaCuota() {
        RelojManual reloj = new RelojManual();
        ChatbotLimitador limitador = nuevo(reloj, 1, 2, 100);

        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
        // Cinco intentos demasiado rápidos seguidos: ninguno debe contar contra el límite diario
        for (int i = 0; i < 5; i++) {
            assertEquals(Resultado.DEMASIADO_RAPIDO, limitador.intentar("ana@correo.com"));
        }
        assertEquals(1, limitador.usadosHoy());

        reloj.avanzar(Duration.ofSeconds(61));
        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
        assertEquals(2, limitador.usadosHoy());
    }

    @Test
    void pausarGeminiBloqueaATodosHastaQueTermineLaPausa() {
        RelojManual reloj = new RelojManual();
        ChatbotLimitador limitador = nuevo(reloj, 10, 100, 100);

        limitador.pausarGemini(Duration.ofMinutes(5));
        assertEquals(Resultado.GEMINI_PAUSADO, limitador.intentar("ana@correo.com"));
        assertEquals(Resultado.GEMINI_PAUSADO, limitador.intentar("luis@correo.com"));
        assertEquals(0, limitador.usadosHoy());

        reloj.avanzar(Duration.ofMinutes(5).plusSeconds(1));
        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
    }

    @Test
    void losValoresInvalidosDeConfiguracionNoDejanElChatbotBloqueado() {
        RelojManual reloj = new RelojManual();
        // 0 o negativos se tratan como 1: nunca deben bloquear a todos desde el primer mensaje
        ChatbotLimitador limitador = nuevo(reloj, 0, -5, 0);

        assertEquals(Resultado.PERMITIDO, limitador.intentar("ana@correo.com"));
        assertEquals(Resultado.LIMITE_DIARIO_GLOBAL, limitador.intentar("luis@correo.com"));
    }
}
