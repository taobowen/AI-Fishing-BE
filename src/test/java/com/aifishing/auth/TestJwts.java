package com.aifishing.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class TestJwts {

    public static final String ISSUER = "https://cognito-idp.ca-central-1.amazonaws.com/ca-central-1_test";
    public static final String CLIENT_ID = "test-client";
    public static final String WEB_CLIENT_ID = "test-web-client";

    static final KeyPair KEY_PAIR = generate();
    static final RSAPublicKey PUBLIC_KEY = (RSAPublicKey) KEY_PAIR.getPublic();
    static final RSAPrivateKey PRIVATE_KEY = (RSAPrivateKey) KEY_PAIR.getPrivate();

    private TestJwts() {
    }

    public static String accessToken(String subject, String email, String clientId, String tokenUse, List<String> groups) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(subject)
                    .issueTime(Date.from(Instant.parse("2026-09-02T12:00:00Z")))
                    .expirationTime(Date.from(Instant.parse("2099-01-01T00:00:00Z")))
                    .claim("token_use", tokenUse)
                    .claim("email", email)
                    .claim("name", email);
            if (clientId != null) {
                claims.claim("client_id", clientId);
            }
            if (groups != null && !groups.isEmpty()) {
                claims.claim("cognito:groups", groups);
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("test").build(),
                    claims.build()
            );
            jwt.sign(new RSASSASigner(PRIVATE_KEY));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    public static class DecoderConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(PUBLIC_KEY).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    SecurityConfig.tokenUseAccess(),
                    SecurityConfig.clientIdMatches(List.of(CLIENT_ID, WEB_CLIENT_ID))
            ));
            return decoder;
        }
    }
}
