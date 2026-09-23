package com.aifishing.lake.service;

import com.aifishing.common.asset.PublicAssetProperties;
import com.aifishing.lake.domain.Lake;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LakeCardImageResolver {

    public static final String PLACEHOLDER_PATH = "lakes/_placeholder.jpg";

    private final PublicAssetProperties properties;

    public LakeCardImageResolver(PublicAssetProperties properties) {
        this.properties = properties;
    }

    public String urlFor(Lake lake) {
        return urlFor(lake == null ? null : lake.getCardImagePath());
    }

    public String urlFor(String cardImagePath) {
        return join(usablePath(cardImagePath));
    }

    public Map<UUID, String> urlsFor(Collection<Lake> lakes) {
        Map<UUID, String> urls = new LinkedHashMap<>();
        if (lakes == null) {
            return urls;
        }
        for (Lake lake : lakes) {
            if (lake != null && lake.getId() != null) {
                urls.put(lake.getId(), urlFor(lake));
            }
        }
        return urls;
    }

    String usablePath(String cardImagePath) {
        if (cardImagePath == null || cardImagePath.isBlank()) {
            return PLACEHOLDER_PATH;
        }
        String normalized = cardImagePath.startsWith("/") ? cardImagePath.substring(1) : cardImagePath;
        return classpathExists("static/" + normalized) ? normalized : PLACEHOLDER_PATH;
    }

    private String join(String path) {
        String base = properties.getPublicAssetBaseUrl();
        if (base == null || base.isBlank()) {
            return "/" + path;
        }
        return base.replaceAll("/+$", "") + "/" + path;
    }

    private static boolean classpathExists(String location) {
        return new ClassPathResource(location).exists();
    }
}
