package com.luan.FinancialSystem.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
@Getter
@Setter
public class RevokedToken {
    @Id @Column(length = 64) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
}
