package com.aifishing.guidance.replay;

import com.aifishing.common.geo.GeoJsonGeometryDto;

import java.util.List;
import java.util.Map;

public record GeoJsonFeature(String type, GeoJsonGeometryDto geometry, Map<String, Object> properties) {
    public static GeoJsonFeature point(double lng, double lat, Map<String, Object> properties) {
        return new GeoJsonFeature(
                "Feature",
                new GeoJsonGeometryDto("Point", List.of(lng, lat)),
                properties == null ? Map.of() : properties
        );
    }
}
