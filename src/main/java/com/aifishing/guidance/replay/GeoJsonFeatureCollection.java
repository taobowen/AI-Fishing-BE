package com.aifishing.guidance.replay;

import java.util.List;

public record GeoJsonFeatureCollection(String type, List<GeoJsonFeature> features) {
    public static GeoJsonFeatureCollection of(List<GeoJsonFeature> features) {
        return new GeoJsonFeatureCollection("FeatureCollection", features == null ? List.of() : List.copyOf(features));
    }
}
