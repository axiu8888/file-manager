package com.kiftd.security;

import com.kiftd.config.KiftdProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expireHours;

    public JwtService(KiftdProperties props) {
        byte[] bytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes.length >= 32 ? bytes : pad(bytes));
        this.expireHours = props.jwt().expireHours();
    }

    private static byte[] pad(byte[] src) {
        byte[] out = new byte[32];
        System.arraycopy(src, 0, out, 0, Math.min(src.length, 32));
        return out;
    }

    public String generate(String accountName, String auth) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountName)
                .claim("auth", auth)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
