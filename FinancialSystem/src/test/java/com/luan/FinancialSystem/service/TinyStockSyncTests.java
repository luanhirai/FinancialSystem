package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import com.luan.FinancialSystem.service.dto.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TinyStockSyncTests {
    final OlistClient client = mock(OlistClient.class);
    final ProductRepository products = mock(ProductRepository.class);
    final EcommerceRepository stores = mock(EcommerceRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final AuthenticatedUserService auth = mock(AuthenticatedUserService.class);
    final OlistImportService service = new OlistImportService(client, products, stores, auth, users);

    User user(long id) {
        var user = new User(); user.setId(id); return user;
    }
    OlistProdutoResumo item(long id) {
        return new OlistProdutoResumo(id, "sku-" + id, "Produto " + id, "S",
                new OlistPrecos(50D, null, 20D, null), null);
    }
    OlistSaldoEstoque stock(Double available, Double physical) {
        return new OlistSaldoEstoque(null, null, null, null, physical, null, available, null);
    }
    OlistImportService.ImportStatus run() {
        var status = new OlistImportService.ImportStatus(); status.running = true;
        service.executarImportacaoTodosProdutos(1L, status); return status;
    }

    @Test void updatesExistingStockAndRegistersNewProductOnce() {
        var owner = user(1);
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(client.listarTodosProdutos(1L)).thenReturn(List.of(item(101), item(202), item(202)));
        when(products.findTinyIdsByUser(1L)).thenReturn(List.of("101"));
        when(client.obterEstoque(101L, 1L)).thenReturn(stock(4D, 9D));
        when(client.obterEstoque(202L, 1L)).thenReturn(stock(null, 7D));
        when(products.updateStockByTinyIdAndUserId("101", 1L, 4)).thenReturn(1);
        when(stores.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var status = run();
        assertThat(status.running).isFalse();
        assertThat(status.total).isEqualTo(2);
        assertThat(status.processed).isEqualTo(2);
        assertThat(status.updated).isEqualTo(1);
        assertThat(status.created).isEqualTo(1);
        assertThat(status.failed).isZero();
        var saved = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(products).save(saved.capture());
        assertThat(saved.getValue().getId_olist()).isEqualTo("202");
        assertThat(saved.getValue().getQuantity()).isEqualTo(7);
        assertThat(saved.getValue().getCost()).isEqualTo(20F);
        assertThat(saved.getValue().getEcommerce().getUser().getId()).isEqualTo(1L);
        verify(client, times(1)).obterEstoque(202L, 1L);
        verify(products).updateStockByTinyIdAndUserId("101", 1L, 4);
    }

    @Test void skipsUnavailableStockAndContinuesWithoutOverwritingOrCreatingBadRecords() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1)));
        when(client.listarTodosProdutos(1L)).thenReturn(List.of(item(101), item(202), item(303)));
        when(products.findTinyIdsByUser(1L)).thenReturn(List.of("101", "303"));
        when(client.obterEstoque(101L, 1L)).thenReturn(null);
        when(client.obterEstoque(202L, 1L)).thenThrow(new IllegalStateException("provider unavailable"));
        when(client.obterEstoque(303L, 1L)).thenReturn(stock(0D, 10D));
        when(products.updateStockByTinyIdAndUserId("303", 1L, 0)).thenReturn(1);
        var status = run();
        assertThat(status.processed).isEqualTo(3);
        assertThat(status.failed).isEqualTo(2);
        assertThat(status.updated).isEqualTo(1);
        assertThat(status.created).isZero();
        verify(products, never()).updateStockByTinyIdAndUserId(eq("101"), anyLong(), any());
        verify(products, never()).save(any());
        verifyNoInteractions(stores);
    }

    @Test void rejectsInvalidQuantitiesAndReportsCatalogErrorsWithoutProviderDetails() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1)));
        when(client.listarTodosProdutos(1L)).thenReturn(List.of(item(101), item(202)));
        when(client.obterEstoque(101L, 1L)).thenReturn(stock(Double.NaN, 1D));
        when(client.obterEstoque(202L, 1L)).thenReturn(stock(1e20, 1D));
        var invalid = run();
        assertThat(invalid.failed).isEqualTo(2);
        verify(products, never()).save(any());
        when(client.listarTodosProdutos(1L)).thenThrow(new IllegalStateException("sensitive provider data"));
        var failed = run();
        assertThat(failed.error).isNotBlank().doesNotContain("sensitive provider data");
        assertThat(failed.running).isFalse();
    }

    @Test void emptyCatalogDoesNotCreateAStore() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1)));
        when(client.listarTodosProdutos(1L)).thenReturn(List.of());
        var status = run();
        assertThat(status.total).isZero();
        assertThat(status.error).isNull();
        verifyNoInteractions(stores);
        verify(products, never()).save(any());
    }

    @Test void repeatedStartsShareTheSameJobAndStatusesStayIsolatedByUser() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstUser = user(1); var secondUser = user(2);
        when(auth.getLoggedUser()).thenReturn(firstUser);
        when(users.findById(1L)).thenReturn(Optional.of(firstUser));
        when(users.findById(2L)).thenReturn(Optional.of(secondUser));
        when(client.listarTodosProdutos(1L)).thenAnswer(invocation -> {
            entered.countDown(); release.await(5, TimeUnit.SECONDS); return List.of();
        });
        when(client.listarTodosProdutos(2L)).thenReturn(List.of());
        try {
            var first = service.iniciarImportacaoTodosProdutos();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.iniciarImportacaoTodosProdutos()).isSameAs(first);
            when(auth.getLoggedUser()).thenReturn(secondUser);
            var second = service.iniciarImportacaoTodosProdutos();
            assertThat(second).isNotSameAs(first);
            assertThat(service.obterStatusImportacao()).isSameAs(second);
            when(auth.getLoggedUser()).thenReturn(firstUser);
            assertThat(service.obterStatusImportacao()).isSameAs(first);
            release.countDown();
            for (int i = 0; i < 100 && (first.running || second.running); i++) Thread.sleep(20);
            assertThat(first.running).isFalse();
            assertThat(second.running).isFalse();
            verify(client, times(1)).listarTodosProdutos(1L);
            verify(client, times(1)).listarTodosProdutos(2L);
        } finally { release.countDown(); service.shutdownStockExecutor(); }
    }
}
