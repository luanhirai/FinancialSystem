package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserService {

    public User getLoggedUser() {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        Object principal = auth.getPrincipal();

        if (principal instanceof User) {
            return (User) principal;
        }

        throw new RuntimeException("Usuário não autenticado");
    }
}