package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository Repository) {
        this.repository = Repository;
    }

    public Product create(Product product) {
        return repository.save(product);
    }

    public Product update(Product updatedProduct) {
        Product product = repository.findById(updatedProduct.getId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        product.setName(updatedProduct.getName());
        product.setOriginal_price(updatedProduct.getOriginal_price());
        product.setCost(updatedProduct.getCost());

        return repository.save(product);
    }

    public List<Product> list() {
        return repository.findAll();
    }

    public Product findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Produto não encontrado"));
    }

    public Product delete(Long id) {
        Product product = repository.findById(id).orElseThrow(() -> new RuntimeException("Produto não encontrado"));
        repository.delete(product);
        return product;
    }
}
