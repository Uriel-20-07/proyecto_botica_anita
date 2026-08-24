package com.example.demo.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.models.EstadoReceta;
import com.example.demo.models.Receta;

@Repository
public interface RecetaRepository extends JpaRepository<Receta, Integer> {
    List<Receta> findByEstado(EstadoReceta estado);

    List<Receta> findByPacienteId(Integer pacienteId);
}
