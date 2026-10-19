package com.luan.FinancialSystem.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Runs before authentication; never trusts client-supplied forwarded addresses by default. */
public class RequestSecurityFilter extends OncePerRequestFilter {
    private static final Set<String> WEBHOOKS = Set.of("/auth/olist-token/callback", "/olist/estoque/webhook", "/olist/vendas/webhook");
    private final Set<String> origins;
    private final String webhookSecret;
    private final boolean trustProxy;
    private final Map<String, Window> requests = new HashMap<>();
    private record Window(long startedAt, int count) {}
    public RequestSecurityFilter(String origins, String webhookSecret, boolean trustProxy) {
        this.origins = new HashSet<>(Arrays.asList(origins.split(",")));
        this.webhookSecret = webhookSecret;
        this.trustProxy = trustProxy;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (WEBHOOKS.contains(path) && unsafe) {
            String supplied = request.getHeader("X-Webhook-Secret");
            if (webhookSecret.isBlank() || supplied == null || !MessageDigest.isEqual(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(401); return;
            }
        } else if (unsafe) {
            String origin = request.getHeader("Origin");
            if ((origin != null && !origins.contains(origin))
                    || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
                response.setStatus(403); return;
            }
        }
        if (unsafe && (WEBHOOKS.contains(path) || Set.of("/auth/login", "/auth/register", "/auth/forgot-password", "/auth/reset-password").contains(path))) {
            String address = request.getRemoteAddr();
            if (trustProxy && request.getHeader("X-Real-IP") != null) address = request.getHeader("X-Real-IP");
            int limit = WEBHOOKS.contains(path) ? 600 : path.equals("/auth/register") ? 10 : path.equals("/auth/forgot-password") ? 20 : 60;
            if (!allow(address + ":" + path, limit)) {
                response.setStatus(429);
                response.setHeader("Retry-After", "900");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Muitas tentativas. Aguarde 15 minutos e tente novamente.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    private synchronized boolean allow(String key, int limit) {
        long now = System.nanoTime();
        long duration = 900_000_000_000L;
        requests.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= duration);
        Window window = requests.get(key);
        if (window == null) {
            if (requests.size() >= 10_000) return false;
            requests.put(key, new Window(now, 1)); return true;
        }
        if (window.count() >= limit) return false;
        requests.put(key, new Window(window.startedAt(), window.count() + 1)); return true;
    }
}
