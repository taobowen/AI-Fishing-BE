package com.aifishing.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String issuer = "";
    private String clientId = "";
    private String webClientId = "";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer == null ? "" : issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null ? "" : clientId;
    }

    public String getWebClientId() {
        return webClientId;
    }

    public void setWebClientId(String webClientId) {
        this.webClientId = webClientId == null ? "" : webClientId;
    }

    public List<String> allowedClientIds() {
        List<String> ids = new ArrayList<>();
        if (!clientId.isBlank()) {
            ids.add(clientId);
        }
        if (!webClientId.isBlank()) {
            ids.add(webClientId);
        }
        return ids;
    }

    public boolean isWebClient(String tokenClientId) {
        return tokenClientId != null && !webClientId.isBlank() && webClientId.equals(tokenClientId);
    }
}
