package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.Jwt.JwtService;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.service.TokenBlacklistService;
import com.luan.FinancialSystem.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(UserService userService,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          TokenBlacklistService tokenBlacklistService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            return ResponseEntity.badRequest().body("Nome é obrigatório.");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("Email é obrigatório.");
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            return ResponseEntity.badRequest().body("Telefone é obrigatório.");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Senha é obrigatória.");
        }

        userService.register(user);
        return ResponseEntity.ok("Usuário registrado com sucesso.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User req) {
        User user = userService.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Credenciais inválidas");
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", user.getName(),
                "email", user.getEmail()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Token não informado.");
        }

        tokenBlacklistService.invalidate(header.substring(7));
        return ResponseEntity.ok("Logout realizado com sucesso.");
    }
}