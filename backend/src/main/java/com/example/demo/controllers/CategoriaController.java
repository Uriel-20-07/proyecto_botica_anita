package com.example.demo.controllers;

import com.example.demo.models.Categoria;
import com.example.demo.services.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {
    
    @Autowired
    private CategoriaService categoriaService;

    @GetMapping
    public List<Categoria> obtenerCategorias() {
        return categoriaService.listarTodas();
    }
}
