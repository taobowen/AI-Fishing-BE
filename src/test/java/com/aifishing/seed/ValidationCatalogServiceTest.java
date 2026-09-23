package com.aifishing.seed;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationCatalogServiceTest {

    @Test
    void catalogHasTwentyOntarioLakesWithStableIdsSlugsAndCentroids() {
        assertThat(ValidationCatalogService.VALIDATION_LAKES).hasSize(21);
        Set<UUID> ids = ValidationCatalogService.VALIDATION_LAKES.stream()
                .map(ValidationCatalogService.CatalogLake::id)
                .collect(Collectors.toSet());
        Set<String> slugs = ValidationCatalogService.VALIDATION_LAKES.stream()
                .map(ValidationCatalogService.CatalogLake::slug)
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(21);
        assertThat(slugs).hasSize(21);
        assertThat(ids).contains(
                DevSeedIds.LAKE_ID,
                DevSeedIds.BALSAM_LAKE_ID,
                DevSeedIds.HEART_LAKE_ID,
                DevSeedIds.CHRISTIE_LAKE_ID,
                DevSeedIds.STURGEON_LAKE_ID,
                DevSeedIds.BUCKHORN_LAKE_ID,
                DevSeedIds.LOVESICK_LAKE_ID
        );
        ValidationCatalogService.CatalogLake heart = ValidationCatalogService.VALIDATION_LAKES.stream()
                .filter(lake -> lake.id().equals(DevSeedIds.HEART_LAKE_ID))
                .findFirst()
                .orElseThrow();
        assertThat(heart.name()).isEqualTo("Heart Lake");
        assertThat(heart.slug()).isEqualTo("heart");
        assertThat(heart.lat()).isEqualTo(43.74);
        assertThat(heart.lng()).isEqualTo(-79.79);
        assertThat(heart.cardImagePath()).isEqualTo("lakes/heart.jpg");

        ValidationCatalogService.CatalogLake christie = ValidationCatalogService.VALIDATION_LAKES.stream()
                .filter(lake -> lake.id().equals(DevSeedIds.CHRISTIE_LAKE_ID))
                .findFirst()
                .orElseThrow();
        assertThat(christie.name()).isEqualTo("Christie Lake");
        assertThat(christie.lat()).isEqualTo(43.28);
        assertThat(christie.lng()).isEqualTo(-80.02);

        ValidationCatalogService.CatalogLake sturgeon = ValidationCatalogService.VALIDATION_LAKES.stream()
                .filter(lake -> lake.id().equals(DevSeedIds.STURGEON_LAKE_ID))
                .findFirst()
                .orElseThrow();
        assertThat(sturgeon.name()).isEqualTo("Sturgeon Lake");
        assertThat(sturgeon.lat()).isEqualTo(44.47);
        assertThat(sturgeon.lng()).isEqualTo(-78.73);

        ValidationCatalogService.CatalogLake lovesick = ValidationCatalogService.VALIDATION_LAKES.stream()
                .filter(lake -> lake.id().equals(DevSeedIds.LOVESICK_LAKE_ID))
                .findFirst()
                .orElseThrow();
        assertThat(lovesick.name()).isEqualTo("Lovesick Lake");
        assertThat(lovesick.slug()).isEqualTo("lovesick");
        assertThat(lovesick.lat()).isEqualTo(44.56);
        assertThat(lovesick.lng()).isEqualTo(-78.24);
        assertThat(lovesick.cardImagePath()).isEqualTo("lakes/lovesick.jpg");
    }
}
