package com.luan.FinancialSystem.repository;
import com.luan.FinancialSystem.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    boolean existsByTokenHashAndExpiresAtAfter(String hash, Instant now);
    void deleteByExpiresAtBefore(Instant now);
}
