package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.entity.PolicySetting;
import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import com.luan.FinancialSystem.repository.PolicySettingRepository;
import com.luan.FinancialSystem.repository.ProductRepository;
import com.luan.FinancialSystem.repository.UserRepository;
import com.luan.FinancialSystem.service.dto.OlistPedidoDetalhe;
import com.luan.FinancialSystem.service.dto.OlistPedidoItem;
import com.luan.FinancialSystem.service.dto.OlistPedidoResumo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderProfitReportService {
    private static final BigDecimal DEFAULT_COST_PERCENTAGE = new BigDecimal("55.00");

    private final OlistClient olistClient;
    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EcommerceRepository ecommerceRepository;
    private final PolicySettingRepository policySettingRepository;
    private final Map<Long, ReportStatus> jobs = new ConcurrentHashMap<>();

    public OrderProfitReportService(OlistClient olistClient,
                                    AuthenticatedUserService authenticatedUserService,
                                    UserRepository userRepository,
                                    ProductRepository productRepository,
                                    EcommerceRepository ecommerceRepository,
                                    PolicySettingRepository policySettingRepository) {
        this.olistClient = olistClient;
        this.authenticatedUserService = authenticatedUserService;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.ecommerceRepository = ecommerceRepository;
        this.policySettingRepository = policySettingRepository;
    }

    public ReportStatus start(LocalDate startDate, LocalDate endDate, Long productEcommerceId) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Informe um periodo valido.");
        }
        if (productEcommerceId == null) {
            throw new IllegalArgumentException("Selecione o ecommerce ao qual os produtos pertencem.");
        }
        User authenticated = authenticatedUserService.getLoggedUser();
        Ecommerce productEcommerce = ecommerceRepository.findById(productEcommerceId)
                .filter(item -> item.getUser().getId().equals(authenticated.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Ecommerce dos produtos nao encontrado."));
        if (productEcommerce.getRate() == null || productEcommerce.getFixed_rate() == null) {
            throw new IllegalArgumentException("Cadastre as taxas percentual e fixa do ecommerce antes de gerar o relatorio.");
        }
        ReportStatus current = jobs.get(authenticated.getId());
        if (current != null && current.running) return current;

        ReportStatus status = new ReportStatus();
        status.running = true;
        status.message = "Listando pedidos do periodo...";
        status.startDate = startDate.toString();
        status.endDate = endDate.toString();
        jobs.put(authenticated.getId(), status);
        CompletableFuture.runAsync(() -> generate(authenticated.getId(), startDate, endDate,
                productEcommerce.getId(), status));
        return status;
    }

    public ReportStatus status() {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        return jobs.getOrDefault(userId, ReportStatus.idle());
    }

    private void generate(Long userId, LocalDate startDate, LocalDate endDate,
                          Long productEcommerceId, ReportStatus status) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Usuario do relatorio nao encontrado."));
            Ecommerce productEcommerce = ecommerceRepository.findById(productEcommerceId)
                    .filter(item -> item.getUser().getId().equals(userId))
                    .orElseThrow(() -> new IllegalStateException("Ecommerce dos produtos nao encontrado."));
            BigDecimal taxRate = getTaxRate(user);
            List<OlistPedidoResumo> summaries = olistClient.listarPedidos(userId, startDate, endDate).itens();
            String selectedEcommerceName = normalizeName(productEcommerce.getName());
            List<OlistPedidoResumo> selected = summaries == null ? List.of() : summaries.stream()
                    .filter(order -> order.ecommerce() != null
                            && normalizeName(order.ecommerce().nome()).equals(selectedEcommerceName))
                    .toList();
            status.total = selected.size();
            status.message = "Calculando ganho real pedido a pedido...";
            List<OrderProfitRow> rows = new ArrayList<>();

            for (OlistPedidoResumo summary : selected) {
                OlistPedidoDetalhe order = olistClient.obterPedido(summary.id(), userId);
                rows.add(calculateOrder(order, productEcommerce, user, taxRate));
                status.processed++;
                status.message = "Pedido " + status.processed + " de " + status.total;
            }

            status.rows = rows;
            status.summary = summarize(rows);
            status.message = rows.size() + " pedidos calculados.";
        } catch (Exception exception) {
            status.error = exception.getMessage() != null ? exception.getMessage() : "Erro inesperado no relatorio.";
        } finally {
            status.running = false;
        }
    }

    private OrderProfitRow calculateOrder(OlistPedidoDetalhe order, Ecommerce ecommerce, User user, BigDecimal taxRate) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal productCosts = BigDecimal.ZERO;
        int units = 0;
        List<String> productNames = new ArrayList<>();

        for (OlistPedidoItem item : order.itens() == null ? List.<OlistPedidoItem>of() : order.itens()) {
            if (item == null || item.produto() == null || item.produto().id() == null) continue;
            int quantity = item.quantidade() != null ? item.quantidade() : 0;
            BigDecimal unitPrice = decimal(item.valorUnitario());
            BigDecimal lineGross = unitPrice.multiply(BigDecimal.valueOf(quantity));
            Product product = getOrCreateProduct(item, ecommerce, user, unitPrice);
            gross = gross.add(lineGross);
            productCosts = productCosts.add(decimal(product.getCost()).multiply(BigDecimal.valueOf(quantity)));
            units += quantity;
            productNames.add(item.produto().descricao());
        }

        BigDecimal discount = decimal(order.valorDesconto());
        BigDecimal marketplaceRate = gross.multiply(decimal(ecommerce.getRate())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal marketplaceFixed = decimal(ecommerce.getFixed_rate()).multiply(BigDecimal.valueOf(units));
        BigDecimal tax = gross.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(discount).subtract(productCosts).subtract(marketplaceRate).subtract(marketplaceFixed).subtract(tax);

        return new OrderProfitRow(order.id(), order.numeroPedido(), order.data(), ecommerce.getName(), units,
                String.join(" | ", productNames), money(gross), money(discount), money(productCosts),
                money(marketplaceRate), money(marketplaceFixed), money(tax), money(net));
    }

    private Product getOrCreateProduct(OlistPedidoItem item, Ecommerce ecommerce, User user, BigDecimal unitPrice) {
        String idOlist = String.valueOf(item.produto().id());
        Product product = productRepository.findByIdOlistAndUserId(idOlist, user.getId()).orElseGet(Product::new);
        product.setId_olist(idOlist);
        product.setName(item.produto().descricao());
        product.setOriginal_price(unitPrice.floatValue());
        if (product.getCost() == null || product.getCost() <= 0) {
            product.setCost(unitPrice.multiply(DEFAULT_COST_PERCENTAGE)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).floatValue());
        }
        product.setEcommerce(ecommerce);
        return productRepository.save(product);
    }

    private BigDecimal getTaxRate(User user) {
        PolicySetting setting = policySettingRepository.findByUserId(user.getId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cadastre o imposto em Configuracao de Regras antes de gerar o relatorio."));
        if (setting.getRate() == null) {
            throw new IllegalStateException("A taxa de imposto cadastrada e invalida.");
        }
        return decimal(setting.getRate());
    }

    private ReportSummary summarize(List<OrderProfitRow> rows) {
        return new ReportSummary(rows.size(),
                money(rows.stream().map(OrderProfitRow::grossRevenue).reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(rows.stream().map(OrderProfitRow::productCost).reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(rows.stream()
                        .map(row -> row.marketplacePercentageFee().add(row.marketplaceFixedFee()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(rows.stream().map(OrderProfitRow::tax).reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(rows.stream().map(OrderProfitRow::netProfit).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private BigDecimal decimal(Number value) { return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value.doubleValue()); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String normalizeName(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }

    public static class ReportStatus {
        public volatile boolean running;
        public volatile int total;
        public volatile int processed;
        public volatile String message;
        public volatile String error;
        public volatile String startDate;
        public volatile String endDate;
        public volatile List<OrderProfitRow> rows = List.of();
        public volatile ReportSummary summary;
        static ReportStatus idle() { ReportStatus value = new ReportStatus(); value.message = "Nenhum relatorio iniciado."; return value; }
    }

    public record OrderProfitRow(Long orderId, Long orderNumber, String date, String ecommerce, int units,
                                 String products, BigDecimal grossRevenue, BigDecimal discount,
                                 BigDecimal productCost, BigDecimal marketplacePercentageFee,
                                 BigDecimal marketplaceFixedFee, BigDecimal tax, BigDecimal netProfit) {}
    public record ReportSummary(int orders, BigDecimal grossRevenue, BigDecimal productCost,
                                BigDecimal marketplaceFee, BigDecimal tax, BigDecimal netProfit) {}
}
