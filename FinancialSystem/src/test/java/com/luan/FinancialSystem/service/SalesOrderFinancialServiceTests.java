package com.luan.FinancialSystem.service;
import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SalesOrderFinancialServiceTests {
    @Test void itemCostSnapshotsAndProfitReconcileForFractionalQuantities() {
        var products=mock(ProductRepository.class);var channels=mock(EcommerceRepository.class);var policies=mock(PolicySettingRepository.class);
        var user=new User();user.setId(1L);var order=new OlistSalesOrder();order.setUser(user);order.setEcommerceName("Loja");
        var channel=new Ecommerce();channel.setName("Loja");channel.setRate(0f);channel.setFixed_rate(0f);
        var policy=new PolicySetting();policy.setRate(0f);var product=new Product();product.setCost(0.01f);
        when(channels.findByUserId(1L)).thenReturn(List.of(channel));when(policies.findByUserId(1L)).thenReturn(List.of(policy));
        when(products.findByIdOlistAndUserId("3",1L)).thenReturn(Optional.of(product));
        var mapper=new ObjectMapper();
        new SalesOrderFinancialService(mock(OlistClient.class),products,channels,policies,mapper).applyDetails(order,mapper.readTree("""
          {"valorTotalPedido":1,"itens":[{"produto":{"id":3},"quantidade":0.5,"valorUnitario":1},{"produto":{"id":3},"quantidade":0.5,"valorUnitario":1}]}
          """));
        var financial=mapper.readTree(order.getFinancialDetails());
        var sum=financial.path("itens").get(0).path("cost").decimalValue().add(financial.path("itens").get(1).path("cost").decimalValue());
        assertThat(sum).isEqualByComparingTo(financial.path("custoProdutos").decimalValue());
        assertThat(order.getRealValue().add(sum)).isEqualByComparingTo("1.00");
    }
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
        assertThat(mapper.readTree(order.getFinancialDetails()).path("itens").get(0).path("cost").decimalValue()).isEqualByComparingTo("40.00");
        when(products.findByIdOlistAndUserId("3",1L)).thenReturn(Optional.empty());
        service.enrich(order);
        assertThat(order.getRealValue()).isNull();
        assertThat(order.getFinancialDetails()).contains("pendencia");
    }
}
