package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.service.TinyMockService;
import com.luan.FinancialSystem.service.TinyProductImportService;
import com.luan.FinancialSystem.entity.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/produtos")
public class ProdutosMockController {

    private final TinyMockService tinyMockService;
    private final TinyProductImportService tinyProductImportService;

    public ProdutosMockController(TinyMockService tinyMockService,
                                  TinyProductImportService tinyProductImportService) {
        this.tinyMockService = tinyMockService;
        this.tinyProductImportService = tinyProductImportService;
    }

    @PostMapping("/importar-tiny")
    public List<Product> importarProdutosTiny() {
        return tinyProductImportService.importarProdutosMockadosDoTiny();
    }

    @GetMapping("/{idProduto}")
    public Map<String, Object> obterProduto(@PathVariable Long idProduto) {
        return tinyMockService.obterProduto(idProduto);
    }

    @GetMapping("/{idProduto}/custos")
    public Map<String, Object> listarCustosProduto(@PathVariable Long idProduto) {
        return tinyMockService.listarCustosProduto(idProduto);
    }
}
