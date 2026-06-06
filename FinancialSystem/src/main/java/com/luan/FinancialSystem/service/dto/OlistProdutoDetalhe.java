package com.luan.FinancialSystem.service.dto;

public record OlistProdutoDetalhe(
        Long id,
        String sku,
        String descricao,
        OlistPrecos precos,
        OlistEstoque estoque
) {
}
