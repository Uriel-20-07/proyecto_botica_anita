package com.example.demo.dto;

import lombok.Data;

@Data
public class PagoRequest {
    // Datos generales del pedido
    private String metodoPago; 
    private String direccionEnvio;
    private String distrito;
    private String codigoCupon;
    private boolean esUrgente;
    private Integer idReceta;

    private String numeroCelular;
    private String tokenYape;

    // --- CAMPO EXCLUSIVO PARA PLIN ---
    // Número de operación que muestra la app del banco tras transferir por Plin
    private String numeroOperacion;

    // --- CAMPOS EXCLUSIVOS PARA STRIPE ---
    // El monto debe ir en la unidad mínima (centavos). Ejemplo: 50.00 soles = 5000
    private Long monto; 
    private String moneda = "pen"; 
}
