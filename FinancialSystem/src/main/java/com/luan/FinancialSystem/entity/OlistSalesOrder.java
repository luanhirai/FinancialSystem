package com.luan.FinancialSystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "olist_sales_orders", uniqueConstraints =
        @UniqueConstraint(name = "uk_olist_order_user", columnNames = {"user_id", "olist_order_id"}))
public class OlistSalesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "olist_order_id", nullable = false)
    private Long olistOrderId;

    @Column(nullable = false)
    private Long number;

    @Column(nullable = false, length = 20)
    private String accountDocument;

    @Column(nullable = false, length = 30)
    private String eventType;

    @Column(nullable = false, length = 20)
    private String webhookVersion;

    @Column(nullable = false)
    private LocalDate orderDate;

    private String ecommerceOrderId;
    private String statusCode;
    private String statusDescription;
    private Long contactId;
    private Long invoiceId;
    private String ecommerceName;
    private String shippingMethodId;
    private String shippingMethodDescription;
    private String customerName;
    private String customerDocument;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant lastWebhookAt;
}
