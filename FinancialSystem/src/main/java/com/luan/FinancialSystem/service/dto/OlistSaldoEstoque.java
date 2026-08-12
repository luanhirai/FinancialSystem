package com.luan.FinancialSystem.service.dto;

public record OlistSaldoEstoque(
        Long id,
        String nome,
        String codigo,
        String unidade,
        Double saldo,
        Double reservado,
        Double disponivel,
        String localizacao
) {
}
