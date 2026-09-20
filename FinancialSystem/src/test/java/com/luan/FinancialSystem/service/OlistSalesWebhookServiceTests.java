package com.luan.FinancialSystem.service;

import tools.jackson.databind.ObjectMapper;
import com.luan.FinancialSystem.entity.OlistSalesOrder;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.OlistSalesOrderRepository;
import com.luan.FinancialSystem.repository.UserRepository;
import com.luan.FinancialSystem.service.dto.OlistSalesWebhookRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OlistSalesWebhookServiceTests {
    @Mock OlistSalesOrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock AuthenticatedUserService authenticatedUserService;

    @Test
    void createsAndMapsSalesOrder() {
        User user = new User(); user.setId(7L);
        var request = request("inclusao_pedido", "aberto");
        when(userRepository.findByClientId("client-1")).thenReturn(Optional.of(user));
        when(orderRepository.findByUserIdAndOlistOrderId(7L, 123L)).thenReturn(Optional.empty());
        when(orderRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        new OlistSalesWebhookService(orderRepository, userRepository, authenticatedUserService, new ObjectMapper())
                .receive(request);

        ArgumentCaptor<OlistSalesOrder> captor = ArgumentCaptor.forClass(OlistSalesOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getOlistOrderId()).isEqualTo(123L);
        assertThat(captor.getValue().getCustomerDocument()).isEqualTo("12345678900");
        assertThat(captor.getValue().getOrderDate()).hasToString("2026-09-06");
    }

    @Test
    void updatesExistingOrderInsteadOfCreatingDuplicate() {
        User user = new User(); user.setId(7L);
        OlistSalesOrder existing = new OlistSalesOrder(); existing.setId(9L); existing.setUser(user);
        when(userRepository.findByClientId("client-1")).thenReturn(Optional.of(user));
        when(orderRepository.findByUserIdAndOlistOrderId(7L, 123L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        new OlistSalesWebhookService(orderRepository, userRepository, authenticatedUserService, new ObjectMapper())
                .receive(request("atualizacao_pedido", "faturado"));

        assertThat(existing.getId()).isEqualTo(9L);
        assertThat(existing.getEventType()).isEqualTo("atualizacao_pedido");
        assertThat(existing.getStatusCode()).isEqualTo("faturado");
    }

    @Test
    void createsServiceWithSpringBootJacksonConfiguration() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class))
                .withBean(OlistSalesOrderRepository.class, () -> orderRepository)
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(AuthenticatedUserService.class, () -> authenticatedUserService)
                .withBean(OlistSalesWebhookService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OlistSalesWebhookService.class);
                });
    }
    private OlistSalesWebhookRequest request(String type, String status) {
        return new OlistSalesWebhookRequest("client-1", "1.0.0", "48.404.755/0001-88", type,
                new OlistSalesWebhookRequest.Dados(123L, 456L, "06/09/2026", "X123", status,
                        "Em aberto", 22L, null, "Loja", new OlistSalesWebhookRequest.FormaEnvio("1", "Correios"),
                        new OlistSalesWebhookRequest.Cliente("Cliente", "123.456.789-00")));
    }
}
