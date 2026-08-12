package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import com.luan.FinancialSystem.repository.ProductRepository;
import com.luan.FinancialSystem.repository.UserRepository;
import com.luan.FinancialSystem.service.dto.OlistEcommerceInfo;
import com.luan.FinancialSystem.service.dto.OlistPedidoDetalhe;
import com.luan.FinancialSystem.service.dto.OlistPedidoItem;
import com.luan.FinancialSystem.service.dto.OlistPedidosResponse;
import com.luan.FinancialSystem.service.dto.OlistProdutoDetalhe;
import com.luan.FinancialSystem.service.dto.OlistProdutoResumo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OlistImportService {
    private final OlistClient olistClient;
    private final ProductRepository productRepository;
    private final EcommerceRepository ecommerceRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;
    private final Map<Long, ImportStatus> importJobs = new ConcurrentHashMap<>();

    public OlistImportService(OlistClient olistClient,
                              ProductRepository productRepository,
                              EcommerceRepository ecommerceRepository,
                              AuthenticatedUserService authenticatedUserService,
                              UserRepository userRepository) {
        this.olistClient = olistClient;
        this.productRepository = productRepository;
        this.ecommerceRepository = ecommerceRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.userRepository = userRepository;
    }

    public OlistPedidosResponse listarPedidos(LocalDate dataInicial, LocalDate dataFinal) {
        return olistClient.listarPedidos(dataInicial, dataFinal);
    }

    public OlistPedidoDetalhe obterPedido(Long idPedido) {
        return olistClient.obterPedido(idPedido);
    }

    public OlistProdutoDetalhe obterProduto(Long idProduto) {
        return olistClient.obterProduto(idProduto);
    }

    public List<OlistProdutoResumo> listarCatalogoProdutos() {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        return olistClient.listarTodosProdutos(userId);
    }

    public ImportStatus iniciarImportacaoTodosProdutos() {
        User user = authenticatedUserService.getLoggedUser();
        ImportStatus current = importJobs.get(user.getId());
        if (current != null && current.running) return current;

        ImportStatus status = new ImportStatus();
        status.running = true;
        status.message = "Preparando importacao dos produtos...";
        importJobs.put(user.getId(), status);
        CompletableFuture.runAsync(() -> executarImportacaoTodosProdutos(user.getId(), status));
        return status;
    }

    public ImportStatus obterStatusImportacao() {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        return importJobs.getOrDefault(userId, ImportStatus.idle());
    }

    private void executarImportacaoTodosProdutos(Long userId, ImportStatus status) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Usuario da importacao nao encontrado."));
            Ecommerce ecommerce = buscarOuCriarEcommerce(null, user);
            List<OlistProdutoResumo> produtos = olistClient.listarTodosProdutos(userId);
            status.total = produtos.size();
            status.message = "Importando cadastro e estoque dos produtos...";

            for (OlistProdutoResumo produtoOlist : produtos) {
                if (produtoOlist == null || produtoOlist.id() == null) continue;
                Product product = salvarProdutoResumo(produtoOlist, ecommerce, user);

                var estoque = olistClient.obterEstoque(produtoOlist.id(), userId);
                Double quantidade = estoque == null ? null
                        : estoque.disponivel() != null ? estoque.disponivel() : estoque.saldo();
                product.setQuantity(toInteger(quantidade));
                productRepository.save(product);
                status.processed++;
                status.message = "Produto " + status.processed + " de " + status.total;
            }

            status.message = status.processed + " produtos importados e sincronizados.";
        } catch (Exception exception) {
            status.error = exception.getMessage() != null ? exception.getMessage() : "Erro inesperado na importacao.";
        } finally {
            status.running = false;
        }
    }

    @Transactional
    public void aplicarWebhookEstoque(String clientId, Long idProduto, Double saldo, String tipoEstoque) {
        if (clientId == null || clientId.isBlank() || idProduto == null || saldo == null) {
            throw new IllegalArgumentException("Webhook de estoque sem clientId, idProduto ou saldo.");
        }
        User user = userRepository.findByClientId(clientId.trim())
                .orElseThrow(() -> new IllegalStateException("Usuario do webhook de estoque nao encontrado."));
        Product product = productRepository.findByIdOlistAndUserId(String.valueOf(idProduto), user.getId())
                .orElseThrow(() -> new IllegalStateException("Produto do webhook ainda nao foi importado."));

        // Algumas contas nao enviam tipoEstoque. Nesses casos, consulta a API
        // para nao confundir saldo fisico com o total realmente disponivel.
        Double quantidadeDisponivel = saldo;
        if (!"D".equalsIgnoreCase(tipoEstoque)) {
            var estoqueAtual = olistClient.obterEstoque(idProduto, user.getId());
            if (estoqueAtual != null) {
                quantidadeDisponivel = estoqueAtual.disponivel() != null
                        ? estoqueAtual.disponivel()
                        : estoqueAtual.saldo();
            }
        }

        product.setQuantity(toInteger(quantidadeDisponivel));
        productRepository.save(product);
    }

    private Product salvarProdutoResumo(OlistProdutoResumo produtoOlist, Ecommerce ecommerce, User user) {
        String idOlist = String.valueOf(produtoOlist.id());
        Product product = productRepository.findByIdOlistAndUserId(idOlist, user.getId())
                .orElseGet(Product::new);
        product.setId_olist(idOlist);
        product.setName(produtoOlist.descricao());
        product.setOriginal_price(toFloat(produtoOlist.precos() != null ? produtoOlist.precos().preco() : null));
        product.setCost(toFloat(produtoOlist.precos() != null ? produtoOlist.precos().precoCusto() : null));
        product.setEcommerce(ecommerce);
        return productRepository.save(product);
    }

    private Integer toInteger(Double value) {
        return value != null ? (int) Math.round(value) : null;
    }

    public static class ImportStatus {
        public volatile boolean running;
        public volatile int total;
        public volatile int processed;
        public volatile String message;
        public volatile String error;

        static ImportStatus idle() {
            ImportStatus status = new ImportStatus();
            status.message = "Nenhuma importacao iniciada.";
            return status;
        }
    }

    public List<OlistProdutoDetalhe> listarProdutosDoPedido(Long idPedido) {
        OlistPedidoDetalhe pedido = olistClient.obterPedido(idPedido);

        if (pedido == null || pedido.itens() == null) {
            return List.of();
        }

        return pedido.itens().stream()
                .filter(Objects::nonNull)
                .map(OlistPedidoItem::produto)
                .filter(Objects::nonNull)
                .map(OlistProdutoResumo::id)
                .filter(Objects::nonNull)
                .distinct()
                .map(olistClient::obterProduto)
                .toList();
    }

    @Transactional
    public List<Product> importarProdutosDoPedido(Long idPedido) {
        OlistPedidoDetalhe pedido = olistClient.obterPedido(idPedido);
        User user = authenticatedUserService.getLoggedUser();
        return importarProdutosDoPedido(pedido, user, new LinkedHashMap<>());
    }

    @Transactional
    public List<Product> importarProdutosPorPeriodo(LocalDate dataInicial, LocalDate dataFinal, Long ecommerceId) {
        OlistPedidosResponse pedidos = olistClient.listarPedidos(dataInicial, dataFinal);
        Map<String, Product> produtosImportados = new LinkedHashMap<>();
        Map<Long, OlistProdutoDetalhe> produtosTinyCache = new LinkedHashMap<>();

        if (pedidos == null || pedidos.itens() == null) {
            return List.of();
        }

        User user = authenticatedUserService.getLoggedUser();

        pedidos.itens().stream()
                .filter(Objects::nonNull)
                .filter(pedido -> ecommerceId == null
                        || pedido.ecommerce() != null && ecommerceId.equals(pedido.ecommerce().id()))
                .map(pedido -> pedido.id())
                .filter(Objects::nonNull)
                .map(olistClient::obterPedido)
                .map(pedido -> importarProdutosDoPedido(pedido, user, produtosTinyCache))
                .flatMap(List::stream)
                .forEach(product -> produtosImportados.put(product.getId_olist(), product));

        return new ArrayList<>(produtosImportados.values());
    }

    private List<Product> importarProdutosDoPedido(
            OlistPedidoDetalhe pedido,
            User user,
            Map<Long, OlistProdutoDetalhe> produtosTinyCache
    ) {
        if (pedido == null || pedido.itens() == null) {
            return List.of();
        }

        Ecommerce ecommerce = buscarOuCriarEcommerce(pedido.ecommerce(), user);
        Map<Long, Product> produtosImportados = new LinkedHashMap<>();

        pedido.itens().stream()
                .filter(Objects::nonNull)
                .map(OlistPedidoItem::produto)
                .filter(Objects::nonNull)
                .map(produto -> produto.id())
                .filter(Objects::nonNull)
                .distinct()
                .forEach(idProduto -> {
                    OlistProdutoDetalhe produtoOlist = produtosTinyCache.computeIfAbsent(
                            idProduto,
                            olistClient::obterProduto
                    );
                    Product product = salvarProduto(produtoOlist, ecommerce, user);
                    produtosImportados.put(idProduto, product);
                });

        return new ArrayList<>(produtosImportados.values());
    }

    private Product salvarProduto(OlistProdutoDetalhe produtoOlist, Ecommerce ecommerce, User user) {
        if (produtoOlist == null || produtoOlist.id() == null) {
            throw new IllegalArgumentException("Produto retornado pela Olist/Tiny sem identificador");
        }

        String idOlist = String.valueOf(produtoOlist.id());
        Product product = productRepository.findByIdOlistAndUserId(idOlist, user.getId())
                .orElseGet(Product::new);

        product.setId_olist(idOlist);
        product.setName(produtoOlist.descricao());
        product.setOriginal_price(toFloat(produtoOlist.precos() != null ? produtoOlist.precos().preco() : null));
        product.setCost(toFloat(produtoOlist.precos() != null ? produtoOlist.precos().precoCusto() : null));
        product.setQuantity(toInteger(produtoOlist.estoque() != null ? produtoOlist.estoque().quantidade() : null));
        product.setEcommerce(ecommerce);

        return productRepository.save(product);
    }

    private Ecommerce buscarOuCriarEcommerce(OlistEcommerceInfo ecommerceOlist, User user) {
        String nome = ecommerceOlist != null && ecommerceOlist.nome() != null && !ecommerceOlist.nome().isBlank()
                ? ecommerceOlist.nome()
                : "Olist";

        return ecommerceRepository.findByUserId(user.getId()).stream()
                .filter(ecommerce -> ecommerce.getName().equalsIgnoreCase(nome))
                .findFirst()
                .orElseGet(() -> {
                    Ecommerce ecommerce = new Ecommerce();
                    ecommerce.setName(nome);
                    ecommerce.setRate(0F);
                    ecommerce.setFixed_rate(0F);
                    ecommerce.setUser(user);
                    return ecommerceRepository.save(ecommerce);
                });
    }

    private Float toFloat(Double value) {
        return value != null ? value.floatValue() : null;
    }
}
