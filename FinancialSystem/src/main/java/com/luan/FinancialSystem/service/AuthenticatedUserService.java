package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserService {

    public User getLoggedUser() {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado");
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof User) {
            return (User) principal;
        }

        throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado");
    }
}
