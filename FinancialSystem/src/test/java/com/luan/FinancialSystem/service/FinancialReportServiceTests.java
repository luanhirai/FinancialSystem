package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialReportServiceTests {
    final ObjectMapper mapper=new ObjectMapper();
    final OlistSalesOrderRepository orders=mock(OlistSalesOrderRepository.class);
    final AuthenticatedUserService auth=mock(AuthenticatedUserService.class);
    final LocalDate date=LocalDate.of(2026,10,10);
    final FinancialReportService service=new FinancialReportService(orders,auth,mapper);
    @BeforeEach void setup(){var user=new User();user.setId(7L);when(auth.getLoggedUser()).thenReturn(user);}
    OlistSalesOrder order() {
        var order=new OlistSalesOrder();order.setId(1L);order.setNumber(123L);order.setOrderDate(date);order.setEcommerceName("Loja");order.setStatusCode("aprovado");
        order.setOrderDetails("""
          {"valorTotalPedido":100,"valorFrete":10,"itens":[
          {"produto":{"id":11,"descricao":"Produto A","sku":"A"},"quantidade":1,"valorUnitario":60},
          {"produto":{"id":22,"descricao":"Produto B","sku":"B"},"quantidade":2,"valorUnitario":20}]}
          """);
        order.setFinancialDetails("""
          {"total":100,"custoProdutos":30,"taxaPercentual":9,"taxaFixa":6,"impostoVenda":4.5,
          "itens":[{"productId":"11","cost":10},{"productId":"22","cost":20}]}
          """);order.setRealValue(new BigDecimal("40.50"));return order;
    }
    void data(OlistSalesOrder... data){when(orders.findByUserIdAndOrderDateBetweenOrderByOrderDateDescNumberDesc(7L,date,date)).thenReturn(List.of(data));}
    @Test void consolidatesDiscountFreightCostsFeesAndTaxesWithoutDoubleCountingOrders(){
        data(order());var report=service.report(date,date,null,null);
        assertThat(report.totals().orders()).isEqualTo(1);
        assertThat(report.totals().quantity()).isEqualByComparingTo("3");
        assertThat(report.totals().revenue()).isEqualByComparingTo("90");
        assertThat(report.totals().profit()).isEqualByComparingTo("40.50");
        assertThat(report.lines().stream().map(FinancialReportService.Line::profit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(report.totals().profit());
        assertThat(report.channels()).hasSize(1);assertThat(report.products()).hasSize(2);
        verify(orders).findByUserIdAndOrderDateBetweenOrderByOrderDateDescNumberDesc(7L,date,date);
    }
    @Test void filtersProductAndChannelBeforeSummarizing(){
        data(order());var report=service.report(date,date,"11","Loja");
        assertThat(report.lines()).hasSize(1);assertThat(report.totals().revenue()).isEqualByComparingTo("54");
        assertThat(report.totals().fees()).isEqualByComparingTo("7.40");
        assertThat(report.totals().profit()).isEqualByComparingTo("33.90");
        assertThat(service.report(date,date,"11","Outra conta").lines()).isEmpty();
        assertThat(service.report(date,date,"999",null).totals().orders()).isZero();
    }
    @Test void excludesCancelledAndMarksLegacyProductCostsPendingWithoutInventingCosts(){
        var legacy=order();legacy.setFinancialDetails("{\"total\":100,\"custoProdutos\":30,\"taxaPercentual\":9,\"taxaFixa\":6,\"impostoVenda\":4.5}");
        var cancelled=order();cancelled.setId(2L);cancelled.setStatusCode("2");data(legacy,cancelled);
        var report=service.report(date,date,null,null);
        assertThat(report.cancelled()).isEqualTo(1);assertThat(report.totals().profit()).isEqualByComparingTo("40.50");
        assertThat(report.products()).allSatisfy(g->assertThat(g.totals().pending()).isEqualTo(1));
        assertThat(report.lines()).allSatisfy(l->{assertThat(l.cost()).isNull();assertThat(l.profit()).isNull();});
        assertThat(service.report(date,date,"11",null).totals().pending()).isEqualTo(1);
    }
    @Test void preservesCentsWithFractionalQuantitiesAndZeroWeights(){
        var split=FinancialReportService.allocate(new BigDecimal("0.01"),List.of(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE));
        assertThat(split.stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0.01");
        assertThat(FinancialReportService.allocate(BigDecimal.ZERO,List.of(BigDecimal.ZERO))).containsExactly(BigDecimal.ZERO);
        assertThat(FinancialReportService.allocate(BigDecimal.ONE,List.of(BigDecimal.ZERO))).containsExactly((BigDecimal)null);
    }
    @Test void handlesMissingOrCorruptDetailsAsPending(){
        var order=order();order.setOrderDetails("null");order.setFinancialDetails("broken");order.setRealValue(null);data(order);
        var report=service.report(date,date,null,null);assertThat(report.lines()).hasSize(1);
        assertThat(report.totals().pending()).isEqualTo(1);assertThat(report.lines().getFirst().pending()).isNotBlank();
    }
    @Test void rejectsInvalidPeriodBeforeReadingOrders(){
        assertThatThrownBy(()->service.report(date,date.minusDays(1),null,null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orders);
    }
}