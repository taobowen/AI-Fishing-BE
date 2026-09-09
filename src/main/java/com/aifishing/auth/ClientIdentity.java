package com.aifishing.auth;

import com.aifishing.common.enums.ClientChannel;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ClientIdentity {

    private final AuthProperties authProperties;

    public ClientIdentity(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public ClientChannel channel() {
        return authProperties.isWebClient(clientId()) ? ClientChannel.WEB : ClientChannel.MOBILE;
    }

    public boolean web() {
        return channel() == ClientChannel.WEB;
    }

    public String clientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.clientId();
        }
        return null;
    }
}
