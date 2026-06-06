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
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.olistAuthService = olistAuthService;
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
    public ResponseEntity<?> login(@RequestBody User req, HttpServletResponse response) {

        User user = userService.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Credenciais inválidas");
        }

        String token = jwtService.generateToken(user);

        ResponseCookie cookie = ResponseCookie.from("token", token)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of(
                        "message", "Login realizado com sucesso",
                        "token", token,
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
        System.out.println("============================================================");
        System.out.println("[BACKEND PERFIL/OLIST] PUT /auth/me recebido.");
        System.out.println("[BACKEND PERFIL/OLIST] Etapa 1: frontend pediu para salvar perfil/credenciais antes de conectar Olist.");
        System.out.println("[BACKEND PERFIL/OLIST] Nome informado? " + (request.name() != null && !request.name().isBlank()));
        System.out.println("[BACKEND PERFIL/OLIST] Email informado? " + (request.email() != null && !request.email().isBlank()));
        System.out.println("[BACKEND PERFIL/OLIST] Telefone informado? " + (request.phone() != null && !request.phone().isBlank()));
        System.out.println("[BACKEND PERFIL/OLIST] Client ID informado? " + (request.clientId() != null && !request.clientId().isBlank()));
        System.out.println("[BACKEND PERFIL/OLIST] Client Secret novo informado? " + (request.clientSecret() != null && !request.clientSecret().isBlank()));
        try {
            System.out.println("[BACKEND PERFIL/OLIST] Etapa 2: chamando UserService.updateProfile...");
            User user = userService.updateProfile(
                    request.name(),
                    request.email(),
                    request.phone(),
                    request.clientId(),
                    request.clientSecret()
            );
            System.out.println("[BACKEND PERFIL/OLIST] SUCESSO: perfil/credenciais salvos no banco.");
            System.out.println("[BACKEND PERFIL/OLIST] Usuario ID: " + user.getId());
            System.out.println("[BACKEND PERFIL/OLIST] Client ID salvo: " + maskClientId(user.getClient_id()));
            System.out.println("[BACKEND PERFIL/OLIST] Possui client_secret salvo? " + (user.getClient_secret() != null && !user.getClient_secret().isBlank()));
            System.out.println("============================================================");
            return ResponseEntity.ok(profileResponse(user));
        } catch (IllegalArgumentException exception) {
            System.out.println("[BACKEND PERFIL/OLIST] ERRO: nao foi possivel salvar perfil/credenciais.");
            System.out.println("[BACKEND PERFIL/OLIST] Motivo: " + exception.getMessage());
            System.out.println("============================================================");
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
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
        System.out.println("============================================================");
        System.out.println("[BACKEND OLIST CALLBACK] POST /auth/olist-token/callback recebido.");
        System.out.println("[BACKEND OLIST CALLBACK] Etapa 1: webhook chamou o backend para trocar CODE por TOKEN.");
        System.out.println("[BACKEND OLIST CALLBACK] Client ID recebido: " + maskClientId(request.clientId()));
        System.out.println("[BACKEND OLIST CALLBACK] Code recebido? " + (request.code() != null && !request.code().isBlank()));
        System.out.println("[BACKEND OLIST CALLBACK] Prefixo do code: " + maskSecret(request.code()));
        try {
            logger.info("[Olist/Tiny] Webhook chamou backend para trocar code. clientId={}, hasCode={}",
                    maskClientId(request.clientId()),
                    request.code() != null && !request.code().isBlank());
            System.out.println("[BACKEND OLIST CALLBACK] Etapa 2: chamando OlistAuthService.generateTokenFromWebhook...");
            ResponseEntity<?> response = ResponseEntity.ok(olistAuthService.generateTokenFromWebhook(request.code(), request.clientId()));
            logger.info("[Olist/Tiny] Backend finalizou troca do code chamada pelo webhook. clientId={}",
                    maskClientId(request.clientId()));
            System.out.println("[BACKEND OLIST CALLBACK] SUCESSO: service terminou sem erro.");
            System.out.println("[BACKEND OLIST CALLBACK] SUCESSO: token/refresh_token foram processados e salvos pelo service.");
            System.out.println("[BACKEND OLIST CALLBACK] Retornando HTTP 200 para o webhook.");
            System.out.println("============================================================");
            return response;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            logger.warn("[Olist/Tiny] Falha ao trocar code chamado pelo webhook: {}", exception.getMessage());
            System.out.println("[BACKEND OLIST CALLBACK] ERRO: falha ao trocar code por token.");
            System.out.println("[BACKEND OLIST CALLBACK] Motivo: " + exception.getMessage());
            System.out.println("[BACKEND OLIST CALLBACK] Retornando HTTP 400 para o webhook.");
            System.out.println("============================================================");
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (RestClientResponseException exception) {
            logger.warn("[Olist/Tiny] Tiny recusou a troca do code. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString());
            System.out.println("[BACKEND OLIST CALLBACK] ERRO: Tiny recusou a troca do code.");
            System.out.println("[BACKEND OLIST CALLBACK] HTTP Tiny: " + exception.getStatusCode());
            System.out.println("[BACKEND OLIST CALLBACK] Body Tiny: " + exception.getResponseBodyAsString());
            System.out.println("[BACKEND OLIST CALLBACK] Retornando HTTP 400 para o webhook.");
            System.out.println("============================================================");
            return ResponseEntity.badRequest().body("Tiny recusou a troca do code: " + exception.getResponseBodyAsString());
        } catch (RuntimeException exception) {
            logger.error("[Olist/Tiny] Erro inesperado ao trocar code chamado pelo webhook.", exception);
            System.out.println("[BACKEND OLIST CALLBACK] ERRO INESPERADO ao trocar code por token.");
            System.out.println("[BACKEND OLIST CALLBACK] Classe: " + exception.getClass().getName());
            System.out.println("[BACKEND OLIST CALLBACK] Mensagem: " + exception.getMessage());
            System.out.println("[BACKEND OLIST CALLBACK] Retornando HTTP 500 para o webhook.");
            System.out.println("============================================================");
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
