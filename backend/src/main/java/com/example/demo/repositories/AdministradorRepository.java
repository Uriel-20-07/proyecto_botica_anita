package com.example.demo.repositories;

import com.example.demo.models.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AdministradorRepository extends JpaRepository<Administrador, Integer> {
    Optional<Administrador> findByCorreoCorp(String correoCorp);
    boolean existsByCorreoCorp(String correoCorp);
}
