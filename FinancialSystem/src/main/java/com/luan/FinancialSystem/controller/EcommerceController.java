package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.service.EcommerceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ecommerce")
public class EcommerceController
{
    private final EcommerceService service;

    public EcommerceController(EcommerceService service){
        this.service=service;
    }

    @PostMapping("/save")
    public ResponseEntity<Ecommerce> save(@Valid @RequestBody Ecommerce ecommerce) {
        return ResponseEntity.ok(service.save(ecommerce));
    }

    @GetMapping("/ecommerce")
    public List<Ecommerce> getAll(){
        return service.list();
    }

}
