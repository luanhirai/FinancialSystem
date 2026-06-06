package com.luan.FinancialSystem.service.dto;

public record OlistPedidoResumo(
        Long id,
        Integer situacao,
        Long numeroPedido,
        OlistEcommerceInfo ecommerce,
        String dataCriacao,
        String dataPrevista,
        String valor
) {
}
