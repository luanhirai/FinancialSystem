package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.service.OlistImportService;
import com.luan.FinancialSystem.service.dto.OlistPedidoDetalhe;
import com.luan.FinancialSystem.service.dto.OlistPedidosResponse;
import com.luan.FinancialSystem.service.dto.OlistProdutoDetalhe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/olist")
public class OlistController {
    private final OlistImportService olistImportService;

    public OlistController(OlistImportService olistImportService) {
        this.olistImportService = olistImportService;
    }

    @GetMapping("/pedidos")
    public OlistPedidosResponse listarPedidosPorPeriodo(@RequestParam LocalDate dataInicial,
                                                        @RequestParam LocalDate dataFinal) {
        return olistImportService.listarPedidos(dataInicial, dataFinal);
    }

    @GetMapping("/pedidos/{idPedido}")
    public OlistPedidoDetalhe obterPedido(@PathVariable Long idPedido) {
        return olistImportService.obterPedido(idPedido);
    }

    @GetMapping("/pedidos/{idPedido}/produtos")
    public List<OlistProdutoDetalhe> listarProdutosDoPedido(@PathVariable Long idPedido) {
        return olistImportService.listarProdutosDoPedido(idPedido);
    }

    @GetMapping("/produtos/{idProduto}")
    public OlistProdutoDetalhe obterProduto(@PathVariable Long idProduto) {
        return olistImportService.obterProduto(idProduto);
    }

    @GetMapping("/produtos")
    public List<com.luan.FinancialSystem.service.dto.OlistProdutoResumo> listarProdutos() {
        return olistImportService.listarCatalogoProdutos();
    }

    @PostMapping("/produtos/importar")
    public OlistImportService.ImportStatus importarTodosProdutos() {
        return olistImportService.iniciarImportacaoTodosProdutos();
    }

    @GetMapping("/produtos/importar/status")
    public OlistImportService.ImportStatus statusImportacaoTodosProdutos() {
        return olistImportService.obterStatusImportacao();
    }

    @PostMapping("/estoque/webhook")
    public ResponseEntity<Void> receberWebhookEstoque(@org.springframework.web.bind.annotation.RequestBody EstoqueWebhookRequest request) {
        olistImportService.aplicarWebhookEstoque(
                request.clientId(), request.idProduto(), request.saldo(), request.tipoEstoque());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pedidos/{idPedido}/produtos/importar")
    public List<Product> importarProdutosDoPedido(@PathVariable Long idPedido) {
        return olistImportService.importarProdutosDoPedido(idPedido);
    }

    @PostMapping("/pedidos/produtos/importar")
    public List<Product> importarProdutosPorPeriodo(@RequestParam LocalDate dataInicial,
                                                    @RequestParam LocalDate dataFinal,
                                                    @RequestParam(required = false) Long ecommerceId) {
        return olistImportService.importarProdutosPorPeriodo(dataInicial, dataFinal, ecommerceId);
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<String> handleTooManyRequests() {
        return ResponseEntity
                .status(429)
                .body("Limite de requisicoes da API Tiny/Olist atingido. Aguarde alguns minutos e tente novamente com um periodo menor.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    public record EstoqueWebhookRequest(String clientId, Long idProduto, Double saldo, String tipoEstoque) {}
}
