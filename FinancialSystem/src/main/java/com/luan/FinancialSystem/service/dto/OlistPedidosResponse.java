package com.luan.FinancialSystem.service.dto;

import java.util.List;

public record OlistPedidosResponse(
        List<OlistPedidoResumo> itens,
        OlistPaginacao paginacao
) {
}
