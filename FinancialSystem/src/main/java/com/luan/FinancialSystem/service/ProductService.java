package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final EcommerceRepository ecommerces;
    private final AuthenticatedUserService authenticatedUserService;
    public ProductService(ProductRepository repository, EcommerceRepository ecommerces, AuthenticatedUserService authenticatedUserService) {
        this.repository = repository;
        this.ecommerces = ecommerces;
        this.authenticatedUserService = authenticatedUserService;
    }
    private Ecommerce ownedEcommerce(Ecommerce value) {
        if (value == null || value.getId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a loja.");
        return ecommerces.findByIdAndUserId(value.getId(), authenticatedUserService.getLoggedUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loja não encontrada."));
    }
    public Product create(Product product) {
        product.setId(null);
        product.setEcommerce(ownedEcommerce(product.getEcommerce()));
        return repository.save(product);
    }
    public Product update(Product updated) {
        Product product = findById(updated.getId());
        product.setEcommerce(ownedEcommerce(updated.getEcommerce()));
        product.setName(updated.getName());
        product.setOriginal_price(updated.getOriginal_price());
        product.setCost(updated.getCost());
        product.setQuantity(updated.getQuantity());
        product.setId_olist(updated.getId_olist());
        return repository.save(product);
    }
    public List<Product> list() {
        return repository.findProductsByUser(authenticatedUserService.getLoggedUser().getId());
    }
    public Product findById(Long id) {
        return repository.findByIdAndEcommerceUserId(id, authenticatedUserService.getLoggedUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }
    public Product delete(Long id) {
        Product product = findById(id);
        repository.delete(product);
        return product;
    }
}
