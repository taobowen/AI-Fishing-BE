package com.aifishing.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    @Test
    void tokenUseMustBeAccess() {
        assertThat(SecurityConfig.tokenUseAccess().validate(jwt(Map.of("token_use", "access"))).hasErrors()).isFalse();
        assertThat(SecurityConfig.tokenUseAccess().validate(jwt(Map.of("token_use", "id"))).hasErrors()).isTrue();
    }

    @Test
    void clientIdMustMatchClaimOrAudience() {
        var validator = SecurityConfig.clientIdMatches(List.of("app-client", "web-client"));
        assertThat(validator.validate(jwt(Map.of("client_id", "app-client"))).hasErrors()).isFalse();
        assertThat(validator.validate(jwt(Map.of("client_id", "web-client"))).hasErrors()).isFalse();
        assertThat(validator.validate(jwt(Map.of("client_id", "other"))).hasErrors()).isTrue();
        Jwt audOnly = Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuer("https://example.com")
                .subject("sub")
                .audience(List.of("app-client"))
                .issuedAt(Instant.parse("2026-09-02T12:00:00Z"))
                .expiresAt(Instant.parse("2026-09-02T13:00:00Z"))
                .build();
        assertThat(validator.validate(audOnly).hasErrors()).isFalse();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuer("https://example.com")
                .subject("sub")
                .issuedAt(Instant.parse("2026-09-02T12:00:00Z"))
                .expiresAt(Instant.parse("2026-09-02T13:00:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
