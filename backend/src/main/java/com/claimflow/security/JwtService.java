package com.claimflow.security;

import com.claimflow.config.AppProperties;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(AppProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlMinutes = props.jwt().ttlMinutes();
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .issuer("claimflow")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer("claimflow").build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(Long.valueOf(c.getSubject()), c.get("email", String.class),
                    c.get("name", String.class), Role.valueOf(c.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
