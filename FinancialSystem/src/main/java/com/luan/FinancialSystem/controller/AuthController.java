package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.Jwt.JwtService;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.service.OlistAuthService;
import com.luan.FinancialSystem.service.TokenBlacklistService;
import com.luan.FinancialSystem.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @org.springframework.beans.factory.annotation.Value("${app.auth.secure-cookie:false}")
    private boolean secureCookie;
    private final String dummyPasswordHash;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final OlistAuthService olistAuthService;

    public AuthController(UserService userService,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          TokenBlacklistService tokenBlacklistService,
                          OlistAuthService olistAuthService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("invalid-account-placeholder");
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.olistAuthService = olistAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@jakarta.validation.Valid @RequestBody User user) {
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
    public ResponseEntity<?> login(@RequestBody User req, HttpServletResponse response) {

        if (req.getPassword() == null || req.getPassword().isBlank()
                || req.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.status(401).body("Credenciais inválidas");
        }
        User user = userService.findByEmail(req.getEmail()).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(req.getPassword(), user == null ? dummyPasswordHash : user.getPassword());
        if (user == null || !passwordMatches) {
            return ResponseEntity.status(401).body("Credenciais inválidas");
        }

        String token = jwtService.generateToken(user);

        ResponseCookie cookie = ResponseCookie.from("token", token)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Lax")
                .secure(secureCookie)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of(
                        "message", "Login realizado com sucesso",
                        "cookieName", "token"
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {

        String token = null;

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }

        if (token == null) {
            return ResponseEntity.badRequest().body("Token não encontrado.");
        }
        tokenBlacklistService.invalidate(token);

        ResponseCookie cookie = ResponseCookie.from("token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .secure(secureCookie)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("Logout realizado com sucesso.");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body("Não autenticado");
        }

        User user = (User) auth.getPrincipal();

        return ResponseEntity.ok(profileResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody ProfileRequest request) {
        try {
            User user = userService.updateProfile(
                    request.name(),
                    request.email(),
                    request.phone(),
                    request.clientId(),
                    request.clientSecret()
            );
            return ResponseEntity.ok(profileResponse(user));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PostMapping("/olist-authorize")
    public ResponseEntity<?> olistAuthorize() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(olistAuthService.authorizationUrl());
    }

    @GetMapping("/olist-client")
    public ResponseEntity<?> getOlistClient() {
        return ResponseEntity.ok(olistAuthService.getLoggedUserTokenStatus());
    }

    @PostMapping("/olist-client")
    public ResponseEntity<?> saveOlistClient(@RequestBody OlistClientRequest request) {
        try {
            olistAuthService.saveClientCredentials(request.clientId(), request.clientSecret());
            return ResponseEntity.ok(olistAuthService.getLoggedUserTokenStatus());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PostMapping("/olist-token")
    public ResponseEntity<?> generateOlistToken(@RequestBody OlistTokenRequest request) {
        try {
            return ResponseEntity.ok(olistAuthService.generateToken(request.code(), request.redirectUri()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PostMapping("/olist-token/callback")
    public ResponseEntity<?> generateOlistTokenFromCallback(@RequestBody OlistCallbackTokenRequest request) {
        try {
            logger.info("[Olist/Tiny] Webhook chamou backend para trocar code. clientId={}, hasCode={}",
                    maskClientId(request.clientId()),
                    request.code() != null && !request.code().isBlank());
            ResponseEntity<?> response = ResponseEntity.ok(olistAuthService.generateTokenFromWebhook(request.code(), request.clientId()));
            logger.info("[Olist/Tiny] Backend finalizou troca do code chamada pelo webhook. clientId={}",
                    maskClientId(request.clientId()));
            return response;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            logger.warn("[Olist/Tiny] Falha ao trocar code chamado pelo webhook: {}", exception.getMessage());
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (RestClientResponseException exception) {
            logger.warn("[Olist/Tiny] Tiny recusou a troca do code. status={}, body={}",
                    exception.getStatusCode(),
                    "[provider response redacted]");
            return ResponseEntity.badRequest().body("Tiny recusou a troca do code: " + "[provider response redacted]");
        } catch (RuntimeException exception) {
            logger.error("[Olist/Tiny] Erro inesperado ao trocar code chamado pelo webhook.");
            return ResponseEntity.internalServerError().body("Erro inesperado ao trocar code por token: " + exception.getMessage());
        }
    }

    @PostMapping("/olist-token/refresh")
    public ResponseEntity<?> refreshOlistToken() {
        try {
            return ResponseEntity.ok(olistAuthService.refreshLoggedUserToken());
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    public record OlistClientRequest(String clientId, String clientSecret) {
    }

    public record OlistTokenRequest(String code, String redirectUri) {
    }

    public record OlistCallbackTokenRequest(String code, String clientId) {
    }

    public record ProfileRequest(String name, String email, String phone, String clientId, String clientSecret) {
    }

    private Map<String, Object> profileResponse(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("clientId", user.getClient_id());
        profile.put("hasClientSecret", user.getClient_secret() != null && !user.getClient_secret().isBlank());
        return profile;
    }

    private String maskClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "";
        }
        String trimmed = clientId.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12) + "...";
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 8 ? "[informado]" : trimmed.substring(0, 8) + "...";
    }
}
