package com.luan.FinancialSystem.service.dto;

public record OlistEstoque(
        Boolean controlar,
        Boolean sobEncomenda,
        Integer diasPreparacao,
        String localizacao,
        Double minimo,
        Double maximo,
        Double quantidade
) {
}
