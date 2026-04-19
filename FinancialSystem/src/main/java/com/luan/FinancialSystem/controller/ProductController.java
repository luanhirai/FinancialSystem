package com.luan.FinancialSystem.controller;
import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.service.ProductService;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
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

    @PutMapping("/update")
    public ResponseEntity<Product> update(@RequestParam Long id,@Valid @RequestBody Product product) {
        try {
            product.setId(id);
            Product updated = service.update(product);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Product>Delete(@RequestParam Long id){
        try{
            return ResponseEntity.ok(service.delete(id));
        }catch(Exception e){
            return ResponseEntity.badRequest().build();
        }
    }

}
