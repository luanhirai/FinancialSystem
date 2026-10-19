package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.controller.OlistSalesWebhookController;
import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import com.luan.FinancialSystem.service.dto.OlistSaldoEstoque;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlistWebhookSecurityTests {
    @Test void salesNotificationCannotInjectFieldsIntoStoredOrders() {
        var client = mock(OlistClient.class);
        var service = mock(OlistSalesWebhookService.class);
        var users = mock(UserRepository.class);
        var user = new User(); user.setId(1L);
        when(users.findByClientId("client")).thenReturn(Optional.of(user));
        var mapper = new tools.jackson.databind.ObjectMapper();
        var notification = mapper.readTree("{\"clientId\":\"client\",\"dados\":{\"id\":123,\"cliente\":{\"nome\":\"FORGED\"}}}");
        var verified = mapper.readTree("{\"id\":123,\"cliente\":{\"nome\":\"Verified customer\"}}");
        when(client.obterPedidoCompleto(123L, 1L)).thenReturn(verified);
        var controller = new OlistSalesWebhookController(service, "secret", client, users);
        assertThat(controller.receive("secret", notification).getStatusCode().value()).isEqualTo(200);
        verify(service).importFromApi(1L, verified);
        verify(service, never()).receive(any(), anyString());
        assertThat(controller.receive(null, notification).getStatusCode().value()).isEqualTo(401);
    }

    @Test void stockNotificationUsesProviderBalanceAndFailsClosedIfUnavailable() {
        var client = mock(OlistClient.class);
        var products = mock(ProductRepository.class);
        var stores = mock(EcommerceRepository.class);
        var auth = mock(AuthenticatedUserService.class);
        var users = mock(UserRepository.class);
        var user = new User(); user.setId(1L);
        var product = new Product(); product.setQuantity(10);
        when(users.findByClientId("client")).thenReturn(Optional.of(user));
        when(products.findByIdOlistAndUserId("123", 1L)).thenReturn(Optional.of(product));
        when(client.obterEstoque(123L, 1L)).thenReturn(new OlistSaldoEstoque(123L, null, null, null, 8D, 3D, 5D, null));
        var service = new OlistImportService(client, products, stores, auth, users);
        service.aplicarWebhookEstoque("client", 123L, 999999D, "D");
        assertThat(product.getQuantity()).isEqualTo(5);
        verify(products).save(product);
        when(client.obterEstoque(123L, 1L)).thenReturn(null);
        assertThatThrownBy(() -> service.aplicarWebhookEstoque("client", 123L, 999999D, "D"))
                .isInstanceOf(IllegalStateException.class);
        verify(products, times(1)).save(product);
    }
}
