package com.luan.FinancialSystem.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Ecommerce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @NonNull
    private Float rate;

    @NonNull
    private Float fixed_rate;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
