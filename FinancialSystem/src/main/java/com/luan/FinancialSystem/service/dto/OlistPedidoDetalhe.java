package com.luan.FinancialSystem.service.dto;

import java.util.List;

public record OlistPedidoDetalhe(
        Long id,
        Long numeroPedido,
        OlistEcommerceInfo ecommerce,
        List<OlistPedidoItem> itens,
        String data,
        String dataEnvio,
        Integer situacao
) {
}
