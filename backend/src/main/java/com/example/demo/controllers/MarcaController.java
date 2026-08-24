package com.example.demo.controllers;

import com.example.demo.models.Marca;
import com.example.demo.repositories.MarcaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marcas")
public class MarcaController {

    @Autowired
    private MarcaRepository marcaRepository;

    @GetMapping
    public List<Marca> getMarcas() {
        return marcaRepository.findAll();
    }
}
