package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import com.luan.FinancialSystem.repository.ProductRepository;
import com.luan.FinancialSystem.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EcommerceService {

    private final EcommerceRepository ecommerceRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public EcommerceService(EcommerceRepository ecommerceRepository,
                            ProductRepository productRepository,
                            UserRepository userRepository,
                            AuthenticatedUserService authenticatedUserService) { // ← adicionado
        this.ecommerceRepository = ecommerceRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.authenticatedUserService = authenticatedUserService; // ← adicionado
    }

    public Ecommerce create(Ecommerce ecommerce) {
        User user = authenticatedUserService.getLoggedUser(); // ← corrigido
        ecommerce.setUser(user);
        return ecommerceRepository.save(ecommerce);
    }

    public List<Ecommerce> list() {
        User user = authenticatedUserService.getLoggedUser(); // ← corrigido
        System.out.println("Usuário logado ID: " + user.getId());
        return ecommerceRepository.findByUserId(user.getId());
    }

    public Ecommerce update(Long id, Ecommerce updated) {
        Ecommerce ecommerce = ecommerceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ecommerce não encontrado"));

        ecommerce.setName(updated.getName());
        ecommerce.setRate(updated.getRate());
        ecommerce.setFixed_rate(updated.getFixed_rate());

        return ecommerceRepository.save(ecommerce);
    }

    public Ecommerce delete(Long id) {
        Ecommerce ecommerce = ecommerceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ecommerce não encontrado"));

        boolean hasProducts = !productRepository.findProductsByUser(id).isEmpty();
        if (hasProducts) {
            throw new RuntimeException(
                    "Não é possível deletar: existem produtos vinculados a este ecommerce."
            );
        }

        ecommerceRepository.delete(ecommerce);
        return ecommerce;
    }
}