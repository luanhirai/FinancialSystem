package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.Jwt.JwtService;
import com.luan.FinancialSystem.entity.RevokedToken;
import com.luan.FinancialSystem.repository.RevokedTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class TokenBlacklistService {
    private final RevokedTokenRepository tokens;
    private final JwtService jwt;
    public TokenBlacklistService(RevokedTokenRepository tokens, JwtService jwt) {
        this.tokens = tokens; this.jwt = jwt;
    }
    @Transactional
    public void invalidate(String token) {
        if (!jwt.isValid(token)) return;
        tokens.deleteByExpiresAtBefore(Instant.now());
        var revoked = new RevokedToken();
        revoked.setTokenHash(PasswordResetService.hash(token));
        revoked.setExpiresAt(jwt.expiresAt(token));
        tokens.save(revoked);
    }
    public boolean isBlacklisted(String token) {
        return tokens.existsByTokenHashAndExpiresAtAfter(PasswordResetService.hash(token), Instant.now());
    }
}
