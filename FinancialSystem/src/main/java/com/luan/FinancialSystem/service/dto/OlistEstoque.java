package com.luan.FinancialSystem.service.dto;

public record OlistEstoque(
        Boolean controlar,
        Boolean sobEncomenda,
        Integer diasPreparacao,
        String localizacao,
        Integer minimo,
        Integer maximo,
        Integer quantidade
) {
}
