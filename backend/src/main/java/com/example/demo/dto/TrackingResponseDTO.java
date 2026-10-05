package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrackingResponseDTO {
    private Integer idPedido;
    private String estado;
    private String direccionEnvio;
    private String distrito;
    private boolean esUrgente;
    
    // Coordenadas de entrega
    private BigDecimal latitudEntrega;
    private BigDecimal longitudEntrega;
    
    // Coordenadas del repartidor
    private Integer repartidorId;
    private BigDecimal latitudRepartidor;
    private BigDecimal longitudRepartidor;
    private LocalDateTime ultimaActualizacionUbicacion;
}