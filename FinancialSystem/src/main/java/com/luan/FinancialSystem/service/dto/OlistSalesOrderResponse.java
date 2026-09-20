package com.luan.FinancialSystem.service.dto;

import com.luan.FinancialSystem.entity.OlistSalesOrder;

import java.time.Instant;
import java.time.LocalDate;

public record OlistSalesOrderResponse(
        Long id, Long olistOrderId, Long number, LocalDate orderDate,
        String eventType, String statusCode, String statusDescription,
        String ecommerceOrderId, String ecommerceName, String customerName,
        String shippingMethodDescription, Instant createdAt, Instant updatedAt,
        Instant lastWebhookAt
) {
    public static OlistSalesOrderResponse from(OlistSalesOrder order) {
        return new OlistSalesOrderResponse(order.getId(), order.getOlistOrderId(), order.getNumber(),
                order.getOrderDate(), order.getEventType(), order.getStatusCode(),
                order.getStatusDescription(), order.getEcommerceOrderId(), order.getEcommerceName(),
                order.getCustomerName(), order.getShippingMethodDescription(), order.getCreatedAt(),
                order.getUpdatedAt(), order.getLastWebhookAt());
    }
}
