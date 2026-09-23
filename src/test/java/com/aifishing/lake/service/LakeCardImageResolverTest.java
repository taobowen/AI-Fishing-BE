package com.aifishing.lake.service;

import com.aifishing.common.asset.PublicAssetProperties;
import com.aifishing.lake.domain.Lake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LakeCardImageResolverTest {

    LakeCardImageResolver resolver;

    @BeforeEach
    void setUp() {
        PublicAssetProperties properties = new PublicAssetProperties();
        properties.setPublicAssetBaseUrl("https://assets.example.test");
        resolver = new LakeCardImageResolver(properties);
    }

    @Test
    void missingPathOrFileUsesPlaceholder() {
        assertThat(resolver.usablePath(null)).isEqualTo(LakeCardImageResolver.PLACEHOLDER_PATH);
        assertThat(resolver.usablePath("lakes/does-not-exist.jpg")).isEqualTo(LakeCardImageResolver.PLACEHOLDER_PATH);
        assertThat(resolver.urlFor((String) null))
                .isEqualTo("https://assets.example.test/lakes/_placeholder.jpg");
    }

    @Test
    void existingCatalogPathResolvesAgainstBaseUrl() {
        assertThat(resolver.usablePath("lakes/simcoe.jpg")).isEqualTo("lakes/simcoe.jpg");
        assertThat(resolver.urlFor("lakes/simcoe.jpg"))
                .isEqualTo("https://assets.example.test/lakes/simcoe.jpg");
    }

    @Test
    void emptyBaseUrlIsRootRelative() {
        PublicAssetProperties properties = new PublicAssetProperties();
        resolver = new LakeCardImageResolver(properties);
        assertThat(resolver.urlFor("lakes/head.jpg")).isEqualTo("/lakes/head.jpg");
    }

    @Test
    void batchResolveDoesNotRequirePerLakeLookups() {
        Lake head = new Lake();
        head.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        head.setCardImagePath("lakes/head.jpg");
        Lake missing = new Lake();
        missing.setId(UUID.fromString("44444444-4444-4444-4444-444444444499"));
        Map<UUID, String> urls = resolver.urlsFor(List.of(head, missing));
        assertThat(urls.get(head.getId())).isEqualTo("https://assets.example.test/lakes/head.jpg");
        assertThat(urls.get(missing.getId())).isEqualTo("https://assets.example.test/lakes/_placeholder.jpg");
    }
}
