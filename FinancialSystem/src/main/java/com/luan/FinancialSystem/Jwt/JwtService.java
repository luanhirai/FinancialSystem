package com.luan.FinancialSystem.Jwt;

import com.luan.FinancialSystem.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private final String secret;

    public JwtService(@Value("${jwt.secret:troque-esta-chave-em-producao-jwt-32-chars}") String secret) {
        this.secret = secret;
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .setSubject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("pwd", passwordFingerprint(user))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 dia
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Long extractUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();

        return Long.valueOf(subject);
    }

    public boolean isValid(String token) {
        try { extractUserId(token); return true; }
        catch (Exception e) { return false; }
    }

    public boolean isValidForUser(String token, User user) {
        try {
            var claims = Jwts.parser().verifyWith((javax.crypto.SecretKey) getKey()).build().parseSignedClaims(token).getPayload();
            return String.valueOf(user.getId()).equals(claims.getSubject())
                    && passwordFingerprint(user).equals(claims.get("pwd", String.class));
        } catch (Exception exception) { return false; }
    }

    private String passwordFingerprint(User user) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(user.getPassword().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public java.time.Instant expiresAt(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) getKey()).build()
                .parseSignedClaims(token).getPayload().getExpiration().toInstant();
    }

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
