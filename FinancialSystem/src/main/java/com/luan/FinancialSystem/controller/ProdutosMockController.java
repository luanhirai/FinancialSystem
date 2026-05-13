package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.service.TinyProductImportService;
import com.luan.FinancialSystem.entity.Product;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/produtos")
public class ProdutosMockController {

    private final TinyProductImportService tinyProductImportService;

    public ProdutosMockController(TinyProductImportService tinyProductImportService) {
        this.tinyProductImportService = tinyProductImportService;
    }

    @PostMapping("/importar-tiny")
    public List<Product> ImportacaoTiny() {
        return tinyProductImportService.ImportacaoTiny();
    }
}
