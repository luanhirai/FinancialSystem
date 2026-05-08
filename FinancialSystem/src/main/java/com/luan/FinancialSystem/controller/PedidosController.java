package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.service.TinyMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pedidos")
public class PedidosController {

    private final TinyMockService tinyMockService;

    public PedidosController(TinyMockService tinyMockService) {
        this.tinyMockService = tinyMockService;
    }

    @GetMapping
    public Map<String, Object> listarPedidos(
            @RequestParam(required = false) LocalDate dataInicial,
            @RequestParam(required = false) LocalDate dataFinal
    ) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = dataInicial != null ? dataInicial : hoje;
        LocalDate fim = dataFinal != null ? dataFinal : inicio;
        return tinyMockService.listarPedidos(inicio, fim);
    }

    @GetMapping("/confirmados")
    public Map<String, Object> listarPedidosConfirmados(@RequestParam(required = false) LocalDate data) {
        return tinyMockService.listarPedidosConfirmados(data != null ? data : LocalDate.now());
    }

    @GetMapping("/{idPedido}")
    public Map<String, Object> obterPedido(@PathVariable Long idPedido) {
        return tinyMockService.obterPedido(idPedido);
    }

    @GetMapping("/confirmados/produtos-lucro")
    public List<Map<String, Object>> calcularLucroProdutosConfirmados(@RequestParam(required = false) LocalDate data) {
        return tinyMockService.calcularLucroProdutosConfirmados(data != null ? data : LocalDate.now());
    }
}
