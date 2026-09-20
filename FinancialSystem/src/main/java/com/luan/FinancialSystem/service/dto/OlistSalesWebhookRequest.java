package com.luan.FinancialSystem.service.dto;

public record OlistSalesWebhookRequest(
        String clientId,
        String versao,
        String cnpj,
        String tipo,
        Dados dados
) {
    public record Dados(
            Long id,
            Long numero,
            String data,
            String idPedidoEcommerce,
            String codigoSituacao,
            String descricaoSituacao,
            Long idContato,
            Long idNotaFiscal,
            String nomeEcommerce,
            FormaEnvio formaEnvio,
            Cliente cliente
    ) {}

    public record FormaEnvio(String id, String descricao) {}
    public record Cliente(String nome, String cpfCnpj) {}
}
