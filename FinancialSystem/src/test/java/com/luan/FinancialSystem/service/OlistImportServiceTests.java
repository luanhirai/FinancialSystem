package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import com.luan.FinancialSystem.repository.ProductRepository;
import com.luan.FinancialSystem.repository.UserRepository;
import com.luan.FinancialSystem.service.dto.OlistEcommerceInfo;
import com.luan.FinancialSystem.service.dto.OlistPaginacao;
import com.luan.FinancialSystem.service.dto.OlistPedidoResumo;
import com.luan.FinancialSystem.service.dto.OlistPedidosResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OlistImportServiceTests {
    @Mock private OlistClient olistClient;
    @Mock private ProductRepository productRepository;
    @Mock private EcommerceRepository ecommerceRepository;
    @Mock private AuthenticatedUserService authenticatedUserService;
    @Mock private UserRepository userRepository;

    @Test
    void storesOnlyNewOrderEcommercesWithZeroRates() {
        User user = new User();
        user.setId(10L);

        Ecommerce existing = new Ecommerce();
        existing.setName("Mercado Livre");
        existing.setRate(12F);
        existing.setFixed_rate(5F);
        existing.setUser(user);

        OlistPedidosResponse response = new OlistPedidosResponse(List.of(
                order(1L, "Mercado Livre"),
                order(2L, " Shopee "),
                order(3L, "shopee")
        ), new OlistPaginacao(100, 0, 3));

        when(olistClient.listarPedidos(any(), any())).thenReturn(response);
        when(authenticatedUserService.getLoggedUser()).thenReturn(user);
        when(ecommerceRepository.findByUserId(10L)).thenReturn(List.of(existing));
        when(ecommerceRepository.save(any(Ecommerce.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OlistImportService service = new OlistImportService(olistClient, productRepository,
                ecommerceRepository, authenticatedUserService, userRepository);

        OlistPedidosResponse result = service.listarPedidos(LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(result).isSameAs(response);
        ArgumentCaptor<Ecommerce> captor = ArgumentCaptor.forClass(Ecommerce.class);
        verify(ecommerceRepository).save(captor.capture());
        Ecommerce saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Shopee");
        assertThat(saved.getRate()).isZero();
        assertThat(saved.getFixed_rate()).isZero();
        assertThat(saved.getUser()).isSameAs(user);
    }

    private OlistPedidoResumo order(Long id, String ecommerceName) {
        OlistEcommerceInfo ecommerce = new OlistEcommerceInfo(id, ecommerceName, null, null, null);
        return new OlistPedidoResumo(id, 1, id, ecommerce, null, null, "0");
    }
}
