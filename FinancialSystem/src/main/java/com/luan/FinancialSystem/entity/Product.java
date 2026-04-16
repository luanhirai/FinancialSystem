package com.luan.FinancialSystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "product")
public class Product
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Float original_price;
    private String id_olist;
    private Float cost;
    private Integer quantity;

    @ManyToOne
    @JoinColumn(name = "ecommerce_id")
    private Ecommerce ecommerce;
}
