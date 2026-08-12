package com.luan.FinancialSystem.service.dto;

import java.util.List;

public record OlistProdutosResponse(
        List<OlistProdutoResumo> itens,
        OlistPaginacao paginacao
) {
}
