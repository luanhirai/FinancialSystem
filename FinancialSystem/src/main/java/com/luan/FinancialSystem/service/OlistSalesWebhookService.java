package com.luan.FinancialSystem.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.luan.FinancialSystem.entity.OlistSalesOrder;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.OlistSalesOrderRepository;
import com.luan.FinancialSystem.repository.UserRepository;
import com.luan.FinancialSystem.service.dto.OlistSalesOrderResponse;
import com.luan.FinancialSystem.service.dto.OlistSalesWebhookRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Service
public class OlistSalesWebhookService {
    private static final Set<String> ACCEPTED_EVENTS = Set.of("inclusao_pedido", "atualizacao_pedido");
    private static final DateTimeFormatter TINY_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final OlistSalesOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final ObjectMapper objectMapper;
    private final SalesOrderFinancialService financialService;

    public OlistSalesWebhookService(OlistSalesOrderRepository orderRepository, UserRepository userRepository,
                                    AuthenticatedUserService authenticatedUserService, ObjectMapper objectMapper, SalesOrderFinancialService financialService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.objectMapper = objectMapper;
        this.financialService = financialService;
    }

    @Transactional
    public OlistSalesOrderResponse receive(OlistSalesWebhookRequest request) {
        return receive(request, toJson(request));
    }

    @Transactional
    public OlistSalesOrderResponse receive(OlistSalesWebhookRequest request, String rawPayload) {
        validate(request);
        User user = userRepository.findByClientId(request.clientId().trim())
                .orElseThrow(() -> new IllegalStateException("Usuario do webhook de vendas nao encontrado."));
        OlistSalesOrder order = orderRepository
                .findByUserIdAndOlistOrderId(user.getId(), request.dados().id())
                .orElseGet(OlistSalesOrder::new);
        Instant now = Instant.now();
        if (order.getId() == null) {
            order.setUser(user);
            order.setOlistOrderId(request.dados().id());
            order.setCreatedAt(now);
        }
        map(order, request, now);
        order.setRawPayload(rawPayload);
        financialService.enrich(order);
        return OlistSalesOrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OlistSalesOrderResponse> listForLoggedUser(LocalDate start, LocalDate end, String dateType) {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        var zone = java.time.ZoneId.of("America/Sao_Paulo");
        LocalDate today = LocalDate.now(zone);
        if (start == null) start = end == null ? today : end;
        if (end == null) end = start;
        if (start.isAfter(end)) throw new IllegalArgumentException("Periodo invalido.");
        if (!"orderDate".equals(dateType) && !"createdAt".equals(dateType))
            throw new IllegalArgumentException("Tipo de data invalido.");
        List<OlistSalesOrder> orders = "createdAt".equals(dateType)
                ? orderRepository.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByOrderDateDescNumberDesc(
                        userId, start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant())
                : orderRepository.findByUserIdAndOrderDateBetweenOrderByOrderDateDescNumberDesc(userId, start, end);
        return orders.stream().map(OlistSalesOrderResponse::from).toList();
    }

    @Transactional
    public OlistSalesOrderResponse recalculate(Long id) {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        OlistSalesOrder order = orderRepository.findById(id)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Pedido nao encontrado."));
        financialService.enrich(order);
        order.setUpdatedAt(Instant.now());
        return OlistSalesOrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OlistSalesOrderResponse importFromApi(Long userId, tools.jackson.databind.JsonNode data) {
        if (data == null || !data.isObject() || !data.path("id").isNumber())
            throw new IllegalArgumentException("Pedido da API invalido.");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado."));
        Long orderId = data.path("id").asLong();
        OlistSalesOrder order = orderRepository.findByUserIdAndOlistOrderId(userId, orderId)
                .orElseGet(OlistSalesOrder::new);
        Instant now = Instant.now();
        if (order.getId() == null) {
            order.setUser(user); order.setOlistOrderId(orderId); order.setCreatedAt(now);
            order.setAccountDocument(""); order.setEventType("sincronizacao_api");
            order.setWebhookVersion("api-v3"); order.setRawPayload("{}"); order.setLastWebhookAt(now);
        }
        order.setNumber(data.path("numeroPedido").asLong(orderId));
        String date = data.path("data").asText("");
        order.setOrderDate(date.length() >= 10 ? parseDate(date.substring(0, 10)) :
                LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")));
        order.setEcommerceName(text(data.path("ecommerce"), "nome"));
        order.setEcommerceOrderId(text(data.path("ecommerce"), "numeroPedidoEcommerce"));
        order.setCustomerName(text(data.path("cliente"), "nome"));
        order.setCustomerDocument(onlyDigits(text(data.path("cliente"), "cpfCnpj")));
        order.setContactId(nullableLong(data.path("cliente"), "id"));
        order.setInvoiceId(nullableLong(data, "idNotaFiscal"));
        order.setStatusCode(data.path("situacao").asText(""));
        order.setStatusDescription(switch (data.path("situacao").asInt(-1)) {
            case 0 -> "Em aberto"; case 1 -> "Faturado"; case 2 -> "Cancelado";
            case 3 -> "Aprovado"; case 4 -> "Preparando envio"; case 5 -> "Enviado";
            case 6 -> "Entregue"; case 7 -> "Pronto para envio"; case 8 -> "Dados incompletos";
            case 9 -> "Nao entregue"; default -> "Situacao nao informada";
        });
        var shipping = data.path("transportador").path("formaEnvio");
        order.setShippingMethodId(text(shipping, "id"));
        order.setShippingMethodDescription(text(shipping, "nome"));
        order.setUpdatedAt(now);
        financialService.applyDetails(order, data);
        return OlistSalesOrderResponse.from(orderRepository.save(order));
    }
    private String text(tools.jackson.databind.JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }
    private Long nullableLong(tools.jackson.databind.JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asLong() : null;
    }

    private void map(OlistSalesOrder order, OlistSalesWebhookRequest request, Instant now) {
        var data = request.dados();
        order.setNumber(data.numero());
        order.setAccountDocument(onlyDigits(request.cnpj()));
        order.setEventType(request.tipo());
        order.setWebhookVersion(request.versao());
        order.setOrderDate(parseDate(data.data()));
        order.setEcommerceOrderId(data.idPedidoEcommerce());
        order.setStatusCode(data.codigoSituacao());
        order.setStatusDescription(data.descricaoSituacao());
        order.setContactId(data.idContato());
        order.setInvoiceId(data.idNotaFiscal());
        order.setEcommerceName(data.nomeEcommerce());
        order.setShippingMethodId(data.formaEnvio() == null ? null : data.formaEnvio().id());
        order.setShippingMethodDescription(data.formaEnvio() == null ? null : data.formaEnvio().descricao());
        order.setCustomerName(data.cliente().nome());
        order.setCustomerDocument(onlyDigits(data.cliente().cpfCnpj()));
        order.setRawPayload(toJson(request));
        order.setUpdatedAt(now);
        order.setLastWebhookAt(now);
    }

    private void validate(OlistSalesWebhookRequest request) {
        if (request == null || isBlank(request.clientId()) || isBlank(request.versao()) || isBlank(request.cnpj())
                || !ACCEPTED_EVENTS.contains(request.tipo()) || request.dados() == null
                || request.dados().id() == null || request.dados().numero() == null
                || isBlank(request.dados().data()) || isBlank(request.dados().codigoSituacao())
                || isBlank(request.dados().descricaoSituacao()) || request.dados().idContato() == null
                || request.dados().cliente() == null || isBlank(request.dados().cliente().nome())) {
            throw new IllegalArgumentException("Payload do webhook de vendas invalido ou incompleto.");
        }
        parseDate(request.dados().data());
    }

    private LocalDate parseDate(String value) {
        try {
            return value.contains("-") ? LocalDate.parse(value) : LocalDate.parse(value, TINY_DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Data do pedido invalida; use dd/MM/AAAA.");
        }
    }

    private String toJson(OlistSalesWebhookRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Nao foi possivel serializar o webhook de vendas.", exception);
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String onlyDigits(String value) { return value == null ? null : value.replaceAll("\\D", ""); }
}
