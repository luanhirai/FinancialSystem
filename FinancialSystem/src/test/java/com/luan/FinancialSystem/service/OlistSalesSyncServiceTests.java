package com.luan.FinancialSystem.service;
import com.luan.FinancialSystem.repository.*;
import com.luan.FinancialSystem.service.dto.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class OlistSalesSyncServiceTests {
    @Test void recoversOnlyMissingOrdersAndContinuesAfterFailure() {
        var client=mock(OlistClient.class);
        var sales=mock(OlistSalesWebhookService.class);
        var users=mock(UserRepository.class);
        var orders=mock(OlistSalesOrderRepository.class);
        var auth=mock(AuthenticatedUserService.class);
        var date=LocalDate.of(2026,10,10);
        when(client.listarPedidos(1L,date,date)).thenReturn(new OlistPedidosResponse(List.of(
            new OlistPedidoResumo(11L,0,11L,null,null,null,null),
            new OlistPedidoResumo(12L,0,12L,null,null,null,null),
            new OlistPedidoResumo(13L,0,13L,null,null,null,null)),new OlistPaginacao(100,0,3)));
        when(orders.existsByUserIdAndOlistOrderId(1L,11L)).thenReturn(true);
        when(client.obterPedidoCompleto(12L,1L)).thenThrow(new IllegalStateException("falha temporaria"));
        var data=new ObjectMapper().readTree("{}");
        when(client.obterPedidoCompleto(13L,1L)).thenReturn(data);
        var service=new OlistSalesSyncService(client,sales,users,orders,auth);
        var status=new OlistSalesSyncService.Status(); status.running=true;
        service.synchronize(1L,date,date,status);
        verify(client,never()).obterPedidoCompleto(11L,1L);
        verify(sales).importFromApi(1L,data);
        assertThat(status.imported).isEqualTo(1);
        assertThat(status.failed).isEqualTo(1);
        assertThat(status.running).isFalse();
        assertThat(status.lastAttemptAt).isNotNull();
    }
}
