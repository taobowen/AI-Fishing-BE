package com.aifishing.auth;

import com.aifishing.common.exception.IdentityConflictException;
import com.aifishing.user.domain.User;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@Profile("prod")
public class CognitoJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String PROVIDER = "cognito";

    private final CognitoUserService users;

    public CognitoJwtAuthConverter(CognitoUserService users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email == null ? "Angler" : email;
        }
        try {
            User user = users.resolve(PROVIDER, sub, email, name);
            boolean admin = groups(jwt).stream().anyMatch("ADMIN"::equals);
            UserPrincipal principal = UserPrincipal.of(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    admin,
                    clientId(jwt)
            );
            return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
        } catch (IdentityConflictException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("identity_conflict", ex.getMessage(), null), ex);
        }
    }

    static String clientId(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        List<String> aud = jwt.getAudience();
        if (aud != null && aud.size() == 1) {
            return aud.get(0);
        }
        return null;
    }

    static List<String> groups(Jwt jwt) {
        Object claim = jwt.getClaims().get("cognito:groups");
        if (claim instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (claim instanceof String value && !value.isBlank()) {
            return List.of(value);
        }
        return List.of();
    }
}
