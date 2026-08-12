package com.luan.FinancialSystem.service.dto;

public record OlistProdutoResumo(
        Long id,
        String sku,
        String descricao,
        String tipo,
        OlistPrecos precos,
        OlistEstoque estoque
) {
}
