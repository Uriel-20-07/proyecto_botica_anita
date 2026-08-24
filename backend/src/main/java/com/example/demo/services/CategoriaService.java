package com.example.demo.services;

import com.example.demo.models.Categoria;
import com.example.demo.repositories.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoriaService {
    
    @Autowired
    private CategoriaRepository categoriaRepository;

    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }
}
