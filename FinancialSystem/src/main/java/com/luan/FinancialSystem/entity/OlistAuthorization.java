package com.luan.FinancialSystem.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name = "olist_authorizations") @Getter @Setter
public class OlistAuthorization {
    @Id private Long userId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "user_id")
    private User user;
    @Column(unique = true, length = 64) private String stateHash;
    private Instant expiresAt;
}
