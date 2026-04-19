package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import com.luan.FinancialSystem.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EcommerceService {

    private final EcommerceRepository ecommerceRepository;
    private final ProductRepository productRepository;

    public EcommerceService(EcommerceRepository ecommerceRepository,
                            ProductRepository productRepository) {
        this.ecommerceRepository = ecommerceRepository;
        this.productRepository = productRepository;
    }

    public Ecommerce create(Ecommerce ecommerce) {
        return ecommerceRepository.save(ecommerce);
    }

    public List<Ecommerce> list() {
        return ecommerceRepository.findAll();
    }

    public Ecommerce update(Long id, Ecommerce updated) {
        Ecommerce ecommerce = ecommerceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ecommerce não encontrado"));
        return ecommerceRepository.save(ecommerce);
    }

    public Ecommerce delete(Long id) {
        Ecommerce ecommerce = ecommerceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ecommerce não encontrado"));

        boolean hasProducts = !productRepository.findProductsByEcommerce(id).isEmpty();
        if (hasProducts) {
            throw new RuntimeException(
                    "Não é possível deletar: existem produtos vinculados a este ecommerce."
            );
        }

        ecommerceRepository.delete(ecommerce);
        return ecommerce;
    }
}