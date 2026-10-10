package com.luan.FinancialSystem.controller;
import com.luan.FinancialSystem.service.OlistSalesSyncService;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;

@RestController
@RequestMapping("/olist/vendas/sincronizacao")
public class OlistSalesSyncController {
    private final OlistSalesSyncService service;
    public OlistSalesSyncController(OlistSalesSyncService service) { this.service=service; }
    @GetMapping public OlistSalesSyncService.Status status() { return service.status(); }
    @PostMapping public OlistSalesSyncService.Status start(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return service.start(dataInicial,dataFinal);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException ex) { return ResponseEntity.badRequest().body(ex.getMessage()); }
}
