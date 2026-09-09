package com.aifishing.lake.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.Geometry;

public record ParsedFeature(
        String sourceRecordId,
        JsonNode properties,
        Geometry geometry
) {
}
