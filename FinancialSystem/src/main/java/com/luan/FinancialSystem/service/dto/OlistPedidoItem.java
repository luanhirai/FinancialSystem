package com.luan.FinancialSystem.service.dto;

public record OlistPedidoItem(
        OlistProdutoResumo produto,
        Integer quantidade,
        Double valorUnitario,
        String infoAdicional
) {
}
