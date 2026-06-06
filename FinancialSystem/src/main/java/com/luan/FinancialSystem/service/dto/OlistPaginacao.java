package com.luan.FinancialSystem.service.dto;

public record OlistPaginacao(
        Integer limit,
        Integer offset,
        Integer total
) {
}
