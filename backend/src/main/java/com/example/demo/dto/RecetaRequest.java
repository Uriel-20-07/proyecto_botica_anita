package com.example.demo.dto;

import java.time.LocalDate;
import java.util.List;

public class RecetaRequest {
    private String medicoId;
    private List<MedicamentoDTO> medicamentos;
    private LocalDate fechaEmision;
    private String documentoUrl;

    public static class MedicamentoDTO {
        private String nombre;
        private String dosis;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDosis() { return dosis; }
        public void setDosis(String dosis) { this.dosis = dosis; }
    }

    public String getMedicoId() { return medicoId; }
    public void setMedicoId(String medicoId) { this.medicoId = medicoId; }
    public List<MedicamentoDTO> getMedicamentos() { return medicamentos; }
    public void setMedicamentos(List<MedicamentoDTO> medicamentos) { this.medicamentos = medicamentos; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }
    public String getDocumentoUrl() { return documentoUrl; }
    public void setDocumentoUrl(String documentoUrl) { this.documentoUrl = documentoUrl; }
}