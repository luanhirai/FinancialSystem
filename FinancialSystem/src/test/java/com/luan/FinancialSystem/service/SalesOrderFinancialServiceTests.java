package com.luan.FinancialSystem.service;
import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SalesOrderFinancialServiceTests {
    @Test void calculatesDiscountedRevenueAndDoesNotInventMissingCosts() {
        var client=mock(OlistClient.class);
        var products=mock(ProductRepository.class);
        var channels=mock(EcommerceRepository.class);
        var policies=mock(PolicySettingRepository.class);
        var mapper=new ObjectMapper();
        var user=new User(); user.setId(1L);
        var order=new OlistSalesOrder(); order.setUser(user); order.setOlistOrderId(2L); order.setEcommerceName("Loja");
        var channel=new Ecommerce(); channel.setName("Loja"); channel.setRate(10f); channel.setFixed_rate(2f);
        var policy=new PolicySetting(); policy.setRate(5f);
        var product=new Product(); product.setCost(20f);
        when(client.obterPedidoCompleto(2L,1L)).thenReturn(mapper.readTree("""
{"valorTotalPedido":100,"valorFrete":10,"campoExtra":"preservado","itens":[{"produto":{"id":3},"quantidade":2}]}
"""));
        when(channels.findByUserId(1L)).thenReturn(List.of(channel));
        when(policies.findByUserId(1L)).thenReturn(List.of(policy));
        when(products.findByIdOlistAndUserId("3",1L)).thenReturn(Optional.of(product));
        var service=new SalesOrderFinancialService(client,products,channels,policies,mapper);
        service.enrich(order);
        assertThat(order.getRealValue()).isEqualByComparingTo("32.50");
        assertThat(order.getOrderDetails()).contains("campoExtra");
        when(products.findByIdOlistAndUserId("3",1L)).thenReturn(Optional.empty());
        service.enrich(order);
        assertThat(order.getRealValue()).isNull();
        assertThat(order.getFinancialDetails()).contains("pendencia");
    }
}
