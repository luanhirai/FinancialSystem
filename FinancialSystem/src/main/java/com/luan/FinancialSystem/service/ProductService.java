package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final AuthenticatedUserService authenticatedUserService; // ← adicionado

    public ProductService(ProductRepository repository,
                          AuthenticatedUserService authenticatedUserService) { // ← adicionado
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
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
        product.setQuantity(updatedProduct.getQuantity());
        product.setId_olist(updatedProduct.getId_olist());
        product.setEcommerce(updatedProduct.getEcommerce());

        return repository.save(product);
    }

    public List<Product> list() {
        User user = authenticatedUserService.getLoggedUser();
        return repository.findProductsByUser(user.getId());
    }

    public Product findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
    }

    public Product delete(Long id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
        repository.delete(product);
        return product;
    }
}
