package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.service.OrderProfitReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/olist/relatorios/pedidos")
public class OrderProfitReportController {
    private final OrderProfitReportService service;

    public OrderProfitReportController(OrderProfitReportService service) {
        this.service = service;
    }

    @PostMapping
    public OrderProfitReportService.ReportStatus start(@RequestParam LocalDate dataInicial,
                                                       @RequestParam LocalDate dataFinal,
                                                       @RequestParam(required = false) Long ecommerceId,
                                                       @RequestParam Long productEcommerceId) {
        return service.start(dataInicial, dataFinal, ecommerceId, productEcommerceId);
    }

    @GetMapping("/status")
    public OrderProfitReportService.ReportStatus status() {
        return service.status();
    }
}
