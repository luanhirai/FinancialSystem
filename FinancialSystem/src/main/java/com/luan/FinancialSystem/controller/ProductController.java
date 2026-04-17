package com.luan.FinancialSystem.controller;
import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController
{
    private final ProductService service;

    public ProductController(ProductService Service){
        this.service=Service;
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody Product product){
        return ResponseEntity.ok(service.create(product));
    }

    @GetMapping
    public List<Product> list(){
        return service.list();
    }
}
