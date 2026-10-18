package com.smartpark.security;

import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/** Short-lived JWT access tokens (sub=email, uid, roles). Refresh uses opaque tokens. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-ms}") long accessTtlMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtlMs = accessTtlMs;
    }

    public String generateAccess(AuthUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId().toString())
                .claim("name", user.getName())
                .claim("roles", user.getRoles())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtlMs))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            return new AuthUser(
                    java.util.UUID.fromString(claims.get("uid", String.class)),
                    claims.getSubject(),
                    claims.get("name", String.class),
                    roles == null ? List.of() : roles);
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized(ErrorCode.INVALID_TOKEN, "Session expired. Please sign in again.");
        }
    }
}
