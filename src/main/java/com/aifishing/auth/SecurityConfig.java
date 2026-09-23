package com.aifishing.auth;

import com.aifishing.common.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;

@Configuration
@Profile("!worker")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    static final String[] PUBLIC_LAKE_GETTERS = {
            "/api/v1/lakes",
            "/api/v1/lakes/{id}",
            "/api/v1/lakes/{id}/planning-capabilities",
            "/api/v1/lakes/{id}/boat-launches",
            "/api/v1/lakes/{id}/contours"
    };

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Configuration
    @Profile({"dev", "test"})
    static class DevSecurityConfig {

        private final DevAuthenticationFilter devAuthenticationFilter;

        DevSecurityConfig(DevAuthenticationFilter devAuthenticationFilter) {
            this.devAuthenticationFilter = devAuthenticationFilter;
        }

        @Bean
        FilterRegistrationBean<DevAuthenticationFilter> disableDevAuthRegistration(DevAuthenticationFilter filter) {
            FilterRegistrationBean<DevAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(Customizer.withDefaults())
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(HttpMethod.GET, PUBLIC_LAKE_GETTERS).permitAll()
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(devAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Configuration
    @Profile("prod")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ProdSecurityConfig {

        private final AuthProperties authProperties;
        private final CognitoJwtAuthConverter cognitoJwtAuthConverter;

        ProdSecurityConfig(AuthProperties authProperties, CognitoJwtAuthConverter cognitoJwtAuthConverter) {
            this.authProperties = authProperties;
            this.cognitoJwtAuthConverter = cognitoJwtAuthConverter;
        }

        @Bean
        @ConditionalOnMissingBean(JwtDecoder.class)
        JwtDecoder jwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(authProperties.getIssuer()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(authProperties.getIssuer()),
                    tokenUseAccess(),
                    clientIdMatches(authProperties.allowedClientIds())
            ));
            return decoder;
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                JwtDecoder jwtDecoder,
                ObjectMapper objectMapper
        ) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(Customizer.withDefaults())
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/health").permitAll()
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(HttpMethod.GET, PUBLIC_LAKE_GETTERS).permitAll()
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth -> oauth
                            .authenticationEntryPoint(bearerEntryPoint(objectMapper))
                            .jwt(jwt -> jwt
                                    .decoder(jwtDecoder)
                                    .jwtAuthenticationConverter(cognitoJwtAuthConverter)))
                    .build();
        }
    }

    static AuthenticationEntryPoint bearerEntryPoint(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException exception) -> {
            if (isIdentityConflict(exception)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                        "IDENTITY_EMAIL_CONFLICT",
                        exception.getMessage() == null
                                ? "This email is already associated with a different sign-in identity."
                                : exception.getMessage(),
                        List.of(),
                        Instant.now(),
                        request.getRequestURI()
                ));
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                    "UNAUTHORIZED",
                    "Unauthorized",
                    List.of(),
                    Instant.now(),
                    request.getRequestURI()
            ));
        };
    }

    private static boolean isIdentityConflict(org.springframework.security.core.AuthenticationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof com.aifishing.common.exception.IdentityConflictException) {
                return true;
            }
            if (current instanceof OAuth2AuthenticationException oauth
                    && "identity_conflict".equals(oauth.getError().getErrorCode())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static OAuth2TokenValidator<Jwt> tokenUseAccess() {
        return jwt -> {
            if ("access".equals(jwt.getClaimAsString("token_use"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "token_use must be access",
                    null
            ));
        };
    }

    static OAuth2TokenValidator<Jwt> clientIdMatches(String expected) {
        return clientIdMatches(expected == null || expected.isBlank() ? List.of() : List.of(expected));
    }

    static OAuth2TokenValidator<Jwt> clientIdMatches(List<String> allowed) {
        List<String> ids = allowed == null ? List.of() : allowed.stream().filter(id -> id != null && !id.isBlank()).toList();
        return jwt -> {
            String clientId = jwt.getClaimAsString("client_id");
            if (clientId == null) {
                List<String> aud = jwt.getAudience();
                if (aud != null && aud.size() == 1) {
                    clientId = aud.get(0);
                }
            }
            if (clientId != null && ids.contains(clientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "client_id does not match",
                    null
            ));
        };
    }
}
