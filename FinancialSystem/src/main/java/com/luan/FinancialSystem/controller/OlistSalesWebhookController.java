package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.service.OlistSalesWebhookService;
import com.luan.FinancialSystem.service.dto.OlistSalesOrderResponse;
import com.luan.FinancialSystem.service.dto.OlistSalesWebhookRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/olist/vendas")
public class OlistSalesWebhookController {
    private final OlistSalesWebhookService service;
    private final String webhookSecret;
    private final com.luan.FinancialSystem.service.OlistClient client;
    private final com.luan.FinancialSystem.repository.UserRepository users;

    public OlistSalesWebhookController(OlistSalesWebhookService service,
            @Value("${olist.webhook.secret:}") String webhookSecret,
            com.luan.FinancialSystem.service.OlistClient client,
            com.luan.FinancialSystem.repository.UserRepository users) {
        this.service = service;
        this.webhookSecret = webhookSecret;
        this.client = client; this.users = users;
    }

    @PostMapping("/webhook")
    public ResponseEntity<OlistSalesOrderResponse> receive(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String suppliedSecret,
            @RequestBody tools.jackson.databind.JsonNode payload) {
        if (!authorized(suppliedSecret)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String clientId = payload.path("clientId").asText("");
        long id = payload.path("dados").path("id").asLong(0);
        if (clientId.isBlank() || id <= 0) throw new IllegalArgumentException("Notificação inválida.");
        var user = users.findByClientId(clientId).orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));
        // Notifications are hints, never authoritative data: verify using this account's Olist credentials.
        return ResponseEntity.ok(service.importFromApi(user.getId(), client.obterPedidoCompleto(id, user.getId())));
    }

    @GetMapping("/pedidos")
    public List<OlistSalesOrderResponse> list(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate data,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dataInicial,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dataFinal,
            @RequestParam(defaultValue = "orderDate") String tipoData) {
        return service.listForLoggedUser(data != null ? data : dataInicial, data != null ? data : dataFinal, tipoData);
    }

    @PostMapping("/pedidos/recalcular")
    public java.util.Map<String, Integer> recalculateAll() {
        return service.recalculateAll();
    }

    @PostMapping("/pedidos/{id}/recalcular")
    public OlistSalesOrderResponse recalculate(@PathVariable Long id) {
        return service.recalculate(id);
    }

    private boolean authorized(String suppliedSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) return false;
        return suppliedSecret != null && MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> invalidWebhook(RuntimeException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
