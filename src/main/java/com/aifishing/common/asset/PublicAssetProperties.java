package com.aifishing.common.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class PublicAssetProperties {

    /**
     * Public origin for static files (no trailing slash). Empty yields root-relative URLs.
     */
    private String publicAssetBaseUrl = "";

    public String getPublicAssetBaseUrl() {
        return publicAssetBaseUrl;
    }

    public void setPublicAssetBaseUrl(String publicAssetBaseUrl) {
        this.publicAssetBaseUrl = publicAssetBaseUrl == null ? "" : publicAssetBaseUrl.trim();
    }
}
