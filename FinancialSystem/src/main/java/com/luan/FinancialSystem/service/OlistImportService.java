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
import java.util.Locale;
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
    private final java.util.concurrent.ThreadPoolExecutor stockExecutor = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16),
            runnable -> {
                Thread thread = new Thread(runnable, "tiny-stock-sync");
                thread.setDaemon(true);
                return thread;
            });

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

    @Transactional
    public OlistPedidosResponse listarPedidos(LocalDate dataInicial, LocalDate dataFinal) {
        OlistPedidosResponse response = olistClient.listarPedidos(dataInicial, dataFinal);
        armazenarEcommercesDosPedidos(response);
        return response;
    }

    private void armazenarEcommercesDosPedidos(OlistPedidosResponse response) {
        if (response == null || response.itens() == null || response.itens().isEmpty()) {
            return;
        }

        User user = authenticatedUserService.getLoggedUser();
        Map<String, Ecommerce> cadastradosPorNome = new LinkedHashMap<>();
        ecommerceRepository.findByUserId(user.getId()).forEach(ecommerce ->
                cadastradosPorNome.put(normalizarNome(ecommerce.getName()), ecommerce));

        response.itens().stream()
                .filter(Objects::nonNull)
                .map(pedido -> pedido.ecommerce())
                .filter(Objects::nonNull)
                .map(OlistEcommerceInfo::nome)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(nome -> !nome.isBlank())
                .forEach(nome -> {
                    String nomeNormalizado = normalizarNome(nome);
                    if (cadastradosPorNome.containsKey(nomeNormalizado)) {
                        return;
                    }

                    Ecommerce ecommerce = new Ecommerce();
                    ecommerce.setName(nome);
                    ecommerce.setRate(0F);
                    ecommerce.setFixed_rate(0F);
                    ecommerce.setUser(user);
                    cadastradosPorNome.put(nomeNormalizado, ecommerceRepository.save(ecommerce));
                });
    }

    private String normalizarNome(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase(Locale.ROOT);
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

    public synchronized ImportStatus iniciarImportacaoTodosProdutos() {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        ImportStatus current = importJobs.get(userId);
        if (current != null && current.running) return current;
        ImportStatus status = new ImportStatus();
        status.running = true;
        status.message = "Consultando o catálogo atual do Tiny...";
        importJobs.put(userId, status);
        try {
            stockExecutor.execute(() -> executarImportacaoTodosProdutos(userId, status));
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            importJobs.remove(userId, status);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Há outras sincronizações em andamento. Tente novamente em alguns minutos.");
        }
        return status;
    }

    public ImportStatus obterStatusImportacao() {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        return importJobs.getOrDefault(userId, ImportStatus.idle());
    }

    @jakarta.annotation.PreDestroy
    void shutdownStockExecutor() {
        stockExecutor.shutdownNow();
    }

    void executarImportacaoTodosProdutos(Long userId, ImportStatus status) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Usuário não encontrado."));
            Map<Long, OlistProdutoResumo> catalog = new LinkedHashMap<>();
            for (OlistProdutoResumo item : olistClient.listarTodosProdutos(userId)) {
                if (item != null && item.id() != null && item.id() > 0) catalog.putIfAbsent(item.id(), item);
            }
            status.total = catalog.size();
            java.util.Set<String> existingIds = new java.util.HashSet<>(productRepository.findTinyIdsByUser(userId));
            Ecommerce defaultStore = null;
            for (OlistProdutoResumo item : catalog.values()) {
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Sincronização interrompida.");
                try {
                    var stock = olistClient.obterEstoque(item.id(), userId);
                    Double quantity = stock == null ? null : stock.disponivel() != null ? stock.disponivel() : stock.saldo();
                    if (quantity == null || !Double.isFinite(quantity)
                            || quantity < Integer.MIN_VALUE || quantity > Integer.MAX_VALUE) {
                        throw new IllegalStateException("Estoque indisponível para o produto.");
                    }
                    if (existingIds.contains(String.valueOf(item.id()))) {
                        // A stock-only SQL update also preserves edits made while the sync is running.
                        int affected = productRepository.updateStockByTinyIdAndUserId(String.valueOf(item.id()), userId, toInteger(quantity));
                        if (affected == 0) throw new IllegalStateException("Produto removido durante a sincronização.");
                        status.updated++;
                    } else {
                        if (defaultStore == null) defaultStore = buscarOuCriarEcommerce(null, user);
                        Product product = new Product();
                        product.setId_olist(String.valueOf(item.id()));
                        product.setName(item.descricao());
                        product.setOriginal_price(toFloat(item.precos() != null ? item.precos().preco() : null));
                        product.setCost(toFloat(item.precos() != null ? item.precos().precoCusto() : null));
                        product.setQuantity(toInteger(quantity));
                        product.setEcommerce(defaultStore);
                        productRepository.save(product);
                        existingIds.add(String.valueOf(item.id()));
                        status.created++;
                    }
                } catch (Exception exception) {
                    status.failed++;
                    // Provider responses and credentials must not be exposed in the job status.
                }
                status.processed++;
                status.message = "Processados " + status.processed + " de " + status.total + " produtos.";
            }
            status.message = "Sincronização concluída: " + status.updated + " atualizados, "
                    + status.created + " cadastrados e " + status.failed + " com falha.";
        } catch (Exception exception) {
            status.error = "Não foi possível concluir a sincronização. Verifique a conexão com o Tiny e tente novamente.";
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
        var estoqueAtual = olistClient.obterEstoque(idProduto, user.getId());
        if (estoqueAtual == null || (estoqueAtual.disponivel() == null && estoqueAtual.saldo() == null)) {
            throw new IllegalStateException("Não foi possível confirmar o estoque na Olist.");
        }
        Double quantidadeDisponivel = estoqueAtual.disponivel() != null
                ? estoqueAtual.disponivel() : estoqueAtual.saldo();

        product.setQuantity(toInteger(quantidadeDisponivel));
        productRepository.save(product);
    }

    private Integer toInteger(Double value) {
        return value != null ? (int) Math.round(value) : null;
    }

    public static class ImportStatus {
        public volatile int updated;
        public volatile int created;
        public volatile int failed;
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
