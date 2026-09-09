package com.aifishing.auth;

import com.aifishing.common.api.ErrorResponse;
import com.aifishing.user.domain.User;
import com.aifishing.user.repo.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile({"dev", "test"})
public class DevAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String AUTH_CLIENT_HEADER = "X-Auth-Client";
    public static final String DEV_WEB_CLIENT_ID = "web";
    public static final String DEV_MOBILE_CLIENT_ID = "mobile";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final UUID defaultUserId;
    private final boolean adminEnabled;

    public DevAuthenticationFilter(
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Value("${app.dev.default-user-id}") UUID defaultUserId,
            @Value("${app.admin.enabled:false}") boolean adminEnabled
    ) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.defaultUserId = defaultUserId;
        this.adminEnabled = adminEnabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawUserId = Optional.ofNullable(request.getHeader(USER_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElse(defaultUserId.toString());

        UUID userId;
        try {
            userId = UUID.fromString(rawUserId);
        } catch (IllegalArgumentException ex) {
            writeUnauthorized(request, response, "Invalid user id");
            return;
        }

        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            writeUnauthorized(request, response, "Unknown user");
            return;
        }

        String clientId = webClient(request) ? DEV_WEB_CLIENT_ID : DEV_MOBILE_CLIENT_ID;
        UserPrincipal principal = UserPrincipal.of(
                user.get().getId(),
                user.get().getEmail(),
                user.get().getDisplayName(),
                adminEnabled,
                clientId
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private static boolean webClient(HttpServletRequest request) {
        String raw = request.getHeader(AUTH_CLIENT_HEADER);
        return raw != null && "WEB".equalsIgnoreCase(raw.trim());
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                "UNAUTHORIZED",
                message,
                List.of(),
                Instant.now(),
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
