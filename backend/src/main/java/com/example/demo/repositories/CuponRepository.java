package com.example.demo.repositories;

import com.example.demo.models.Cupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CuponRepository extends JpaRepository<Cupon, Integer> {
    Optional<Cupon> findByCodigo(String codigo);
}
