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

    @PostMapping
    public ResponseEntity<Ecommerce> create(@Valid @RequestBody Ecommerce ecommerce) {
        return ResponseEntity.ok(service.create(ecommerce));
    }

    @GetMapping
    public List<Ecommerce> getAll(){
        return service.list();
    }

    @PostMapping("/editEcommerce")
    public ResponseEntity<Ecommerce> editEcommerce(@Valid @RequestBody Ecommerce ecommerce, Long id){
        return ResponseEntity.ok(service.update(id, ecommerce));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEcommerce(@PathVariable Long id) {
        try {
            Ecommerce deleted = service.delete(id);
            return ResponseEntity.ok(deleted);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
