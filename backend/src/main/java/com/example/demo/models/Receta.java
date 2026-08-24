package com.example.demo.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA que representa una receta médica subida por un usuario.
 */
@Entity
@Table(name = "recetas")
public class Receta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idReceta;

    @Column(name = "paciente_id", nullable = false)
    private Integer pacienteId;

    @Column(name = "medico_id", nullable = false, length = 100)
    private String medicoId;

    @Column(name = "medicamentos", columnDefinition = "TEXT")
    private String medicamentos;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoReceta estado = EstadoReceta.EN_ESPERA;

    @Column(name = "documento_url", length = 500)
    private String documentoUrl;

    @Column(name = "revisado_por")
    private Integer revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "comentario_revision", columnDefinition = "TEXT")
    private String comentarioRevision;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Receta() {
    }

    public Integer getIdReceta() {
        return idReceta;
    }

    public void setIdReceta(Integer idReceta) {
        this.idReceta = idReceta;
    }

    public Integer getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Integer pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getMedicoId() {
        return medicoId;
    }

    public void setMedicoId(String medicoId) {
        this.medicoId = medicoId;
    }

    public String getMedicamentos() {
        return medicamentos;
    }

    public void setMedicamentos(String medicamentos) {
        this.medicamentos = medicamentos;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public EstadoReceta getEstado() {
        return estado;
    }

    public void setEstado(EstadoReceta estado) {
        this.estado = estado;
    }

    public String getDocumentoUrl() {
        return documentoUrl;
    }

    public void setDocumentoUrl(String documentoUrl) {
        this.documentoUrl = documentoUrl;
    }

    public Integer getRevisadoPor() {
        return revisadoPor;
    }

    public void setRevisadoPor(Integer revisadoPor) {
        this.revisadoPor = revisadoPor;
    }

    public LocalDateTime getFechaRevision() {
        return fechaRevision;
    }

    public void setFechaRevision(LocalDateTime fechaRevision) {
        this.fechaRevision = fechaRevision;
    }

    public String getComentarioRevision() {
        return comentarioRevision;
    }

    public void setComentarioRevision(String comentarioRevision) {
        this.comentarioRevision = comentarioRevision;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}