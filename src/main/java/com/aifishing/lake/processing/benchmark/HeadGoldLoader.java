package com.aifishing.lake.processing.benchmark;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.BenchmarkProperties;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class HeadGoldLoader {

    private final ResourceLoader resourceLoader;
    private final BenchmarkProperties properties;
    private final ObjectMapper objectMapper;

    public HeadGoldLoader(ResourceLoader resourceLoader, BenchmarkProperties properties, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public GoldSet load() {
        return load(properties.getGoldResource());
    }

    public GoldSet load(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return new GoldSet(null, List.of());
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            String lakeName = root.path("properties").path("lakeName").asText(null);
            List<GoldFeature> features = new ArrayList<>();
            JsonNode array = root.path("features");
            if (array.isArray()) {
                for (JsonNode feature : array) {
                    FeatureType type = GeoJsonGeometryReader.featureType(feature.path("properties"));
                    Geometry geometry = GeoJsonGeometryReader.read(feature.get("geometry"));
                    if (type == null || geometry == null || geometry.isEmpty()) {
                        continue;
                    }
                    features.add(new GoldFeature(
                            type,
                            geometry,
                            feature.path("properties").path("name").asText(null),
                            feature.path("properties").path("notes").asText(null)
                    ));
                }
            }
            return new GoldSet(lakeName, List.copyOf(features));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Head Lake gold overlay from " + location, ex);
        }
    }

    public boolean appliesTo(Lake lake, GoldSet gold) {
        if (gold == null || gold.features().isEmpty()) {
            return false;
        }
        if (gold.lakeName() == null || gold.lakeName().isBlank()) {
            return true;
        }
        return gold.lakeName().equalsIgnoreCase(lake.getName());
    }

    public record GoldSet(String lakeName, List<GoldFeature> features) {
    }
}
