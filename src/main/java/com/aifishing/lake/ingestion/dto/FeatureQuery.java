package com.aifishing.lake.ingestion.dto;

public record FeatureQuery(
        String layerUrl,
        String where,
        Double minLng,
        Double minLat,
        Double maxLng,
        Double maxLat,
        Integer pageSize
) {
}
