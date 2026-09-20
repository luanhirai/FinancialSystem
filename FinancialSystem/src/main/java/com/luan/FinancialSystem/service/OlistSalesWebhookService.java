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

    public OlistSalesWebhookService(OlistSalesOrderRepository orderRepository, UserRepository userRepository,
                                    AuthenticatedUserService authenticatedUserService, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OlistSalesOrderResponse receive(OlistSalesWebhookRequest request) {
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
        return OlistSalesOrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OlistSalesOrderResponse> listForLoggedUser() {
        Long userId = authenticatedUserService.getLoggedUser().getId();
        return orderRepository.findByUserIdOrderByOrderDateDescNumberDesc(userId).stream()
                .map(OlistSalesOrderResponse::from).toList();
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
            return LocalDate.parse(value, TINY_DATE);
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
