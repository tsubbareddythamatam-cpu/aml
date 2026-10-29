package org.aml.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.aml.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Generates a signed JWT containing the user's identifier, role, and email subject.
     *
     * @param user user represented by the token
     * @return signed JWT
     */
    public String generateToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId()); // 🔍 Embeds the user primary key directly into the payload map
        extraClaims.put("role", user.getRole() != null ? user.getRole().name() : null);

        return Jwts.builder()
                .setClaims(extraClaims) // Injects the custom properties map
                .setSubject(user.getEmail()) // Sets the email address as the primary subject
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(
                        signingKey(),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extracts the user identifier claim from a JWT.
     *
     * @param token JWT or bearer-token value
     * @return user identifier, or {@code null} if the claim is not numeric
     */
    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);
        // Safely resolves the integer/long variant from the JSON object mapper context
        Object userIdClaim = claims.get("userId");
        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).longValue();
        }
        return null;
    }

    /**
     * Extracts the subject email address from a JWT.
     *
     * @param token JWT or bearer-token value
     * @return subject email address
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Parses and verifies a JWT, returning its claims.
     *
     * @param token JWT or bearer-token value
     * @return verified JWT claims
     */
    private Claims extractClaims(String token) {
        // Sanitizes standard bearer authorization strings if they retain the Bearer token scheme prefix
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Checks whether a JWT has expired.
     *
     * @param token JWT or bearer-token value
     * @return {@code true} when the token expiration precedes the current time
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Retrieves a JWT's expiration timestamp.
     *
     * @param token JWT or bearer-token value
     * @return expiration timestamp
     */
    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    /**
     * Builds the HMAC signing key from the configured secret.
     *
     * @return signing key with sufficient strength for HS256
     * @throws IllegalStateException if the configured secret is shorter than 256 bits
     */
    private javax.crypto.SecretKey signingKey() {
        byte[] keyBytes = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Validates the JWT subject against a user's username and checks token expiry.
     *
     * @param token JWT or bearer-token value
     * @param userDetails user details expected for the token
     * @return {@code true} if subject matches and token is not expired
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
