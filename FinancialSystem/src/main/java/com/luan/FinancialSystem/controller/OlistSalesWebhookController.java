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

    public OlistSalesWebhookController(OlistSalesWebhookService service,
            @Value("${olist.webhook.secret:}") String webhookSecret) {
        this.service = service;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<OlistSalesOrderResponse> receive(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String suppliedSecret,
            @RequestBody OlistSalesWebhookRequest request) {
        if (!authorized(suppliedSecret)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.receive(request));
    }

    @GetMapping("/pedidos")
    public List<OlistSalesOrderResponse> list() {
        return service.listForLoggedUser();
    }

    private boolean authorized(String suppliedSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) return true;
        return suppliedSecret != null && MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> invalidWebhook(RuntimeException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
