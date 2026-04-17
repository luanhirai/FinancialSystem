package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.repository.ProductRepository;

import java.util.List;

public class ProductService
{
    private final ProductRepository repository;

    public ProductService(ProductRepository Repository){
        this.repository=Repository;
    }

    public Product create(Product product){
        return repository.save(product);
    }

    public Product edit(Product updatedProduct, Long id){
        Product product=repository.findById(id).orElseThrow(()->new RuntimeException("Produto não encontrado"));

        product.setName(updatedProduct.getName());
        product.setOriginal_price(updatedProduct.getOriginal_price());
        product.setCost(updatedProduct.getCost());

        return product;
    }

    public List<Product> list(){
        return repository.findAll();
    }
}
