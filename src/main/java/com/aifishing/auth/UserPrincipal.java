package com.aifishing.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record UserPrincipal(
        UUID id,
        String email,
        String displayName,
        String clientId,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    public static UserPrincipal of(UUID id, String email, String displayName, boolean admin) {
        return of(id, email, displayName, admin, null);
    }

    public static UserPrincipal of(UUID id, String email, String displayName, boolean admin, String clientId) {
        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (admin) {
            roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new UserPrincipal(id, email, displayName, clientId, List.copyOf(roles));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
