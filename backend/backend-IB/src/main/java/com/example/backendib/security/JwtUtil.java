package com.example.backendib.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final Key key;

    private final long accessExpirationMs;
    @Getter
    private final long refreshExpirationMs;

    private final long totpExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs,
            @Value("${jwt.totp-expiration-ms}") long totpExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.totpExpirationMs = totpExpirationMs;
    }

    public String generateAccessToken(String subject, String role) {
        return generateToken(subject, role, "access", accessExpirationMs);
    }

    public String generateRefreshToken(String subject, String role) {
        return generateToken(subject, role, "refresh", refreshExpirationMs);
    }

    public String generateTotpPendingToken(String subject) {
        return generateToken(subject, null, "totp_pending", totpExpirationMs);
    }

    public String generateToken(String subject, String role, String type, long ttlMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .claim("role", role)
                .claim("typ", type)
                .claim("jti", UUID.randomUUID().toString())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public boolean isRefreshToken(String token) {
        Claims c = getClaims(token);
        return "refresh".equals(c.get("typ", String.class));
    }

    public boolean isAccessToken(String token) {
        Claims c = getClaims(token);
        return "access".equals(c.get("typ", String.class));
    }

    public boolean isTotpPendingToken(String token) {
        Claims c = getClaims(token);
        return "totp_pending".equals(c.get("typ", String.class));
    }

    public String getSubjectIfValidAndRefresh(String refreshToken) {
        if (!validateToken(refreshToken)) return null;
        if (!isRefreshToken(refreshToken)) return null;
        return getClaims(refreshToken).getSubject();
    }

    public String getSubjectIfValidTotpPending(String pendingToken) {
        if (!validateToken(pendingToken)) return null;
        if (!isTotpPendingToken(pendingToken)) return null;
        return getClaims(pendingToken).getSubject();
    }

    public String getRoleIfValid(String token) {
        if (!validateToken(token)) return null;
        return getClaims(token).get("role", String.class);
    }

    public long getExpirationMs() {
        return accessExpirationMs;
    }
}
