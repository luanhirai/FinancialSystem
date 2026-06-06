package com.luan.FinancialSystem.service.dto;

public record OlistEcommerceInfo(
        Long id,
        String nome,
        String numeroPedidoEcommerce,
        String numeroPedidoCanalVenda,
        String canalVenda
) {
}
