package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticatedUserService authenticatedUserService;
    private final CryptoService cryptoService;

    public UserService(UserRepository repository,
                       PasswordEncoder passwordEncoder,
                       AuthenticatedUserService authenticatedUserService,
                       CryptoService cryptoService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authenticatedUserService = authenticatedUserService;
        this.cryptoService = cryptoService;
    }

    public User register(User user) {
        if (repository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email já cadastrado.");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setClient_id(null);
        user.setClient_secret(null);
        user.setOlist_access_token(null);
        user.setOlist_refresh_token(null);
        user.setOlist_token_expires_at(null);
        return repository.save(user);
    }

    @Transactional
    public User setClientOlist(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Informe o client_id da Olist/Tiny.");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Informe o client_secret da Olist/Tiny.");
        }

        User user = authenticatedUserService.getLoggedUser();
        user.setClient_id(clientId.trim());
        user.setClient_secret(cryptoService.encrypt(clientSecret.trim()));
        user.setOlist_access_token(null);
        user.setOlist_refresh_token(null);
        user.setOlist_token_expires_at(null);
        return repository.save(user);
    }

    @Transactional
    public User updateProfile(String name, String email, String phone, String clientId, String clientSecret) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome e obrigatorio.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email e obrigatorio.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Telefone e obrigatorio.");
        }

        User user = authenticatedUserService.getLoggedUser();
        String normalizedEmail = email.trim();

        repository.findByEmail(normalizedEmail)
                .filter(existingUser -> !existingUser.getId().equals(user.getId()))
                .ifPresent(existingUser -> {
                    throw new IllegalArgumentException("Email ja cadastrado.");
                });

        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPhone(phone.trim());

        boolean credentialsChanged = false;

        if (clientId != null) {
            String normalizedClientId = clientId.trim();
            String currentClientId = user.getClient_id() == null ? "" : user.getClient_id();

            if (normalizedClientId.isBlank()) {
                if (!currentClientId.isBlank() || user.getClient_secret() != null) {
                    user.setClient_id(null);
                    user.setClient_secret(null);
                    credentialsChanged = true;
                }
            } else if (!normalizedClientId.equals(currentClientId)) {
                user.setClient_id(normalizedClientId);
                credentialsChanged = true;
            }
        }

        if (clientSecret != null && !clientSecret.isBlank()) {
            user.setClient_secret(cryptoService.encrypt(clientSecret.trim()));
            credentialsChanged = true;
        }

        if (credentialsChanged) {
            user.setOlist_access_token(null);
            user.setOlist_refresh_token(null);
            user.setOlist_token_expires_at(null);
        }

        return repository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public String getDecryptedClientSecret(User user) {
        return cryptoService.decrypt(user.getClient_secret());
    }
}
