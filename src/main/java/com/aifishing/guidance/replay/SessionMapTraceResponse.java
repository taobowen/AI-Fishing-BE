package com.aifishing.guidance.replay;

import java.util.List;
import java.util.UUID;

public record SessionMapTraceResponse(
        UUID sessionId,
        GeoJsonFeatureCollection originalPlan,
        GeoJsonFeatureCollection gpsTrace,
        GeoJsonFeatureCollection anchors
) {
    public SessionMapTraceResponse {
        originalPlan = originalPlan == null ? GeoJsonFeatureCollection.of(List.of()) : originalPlan;
        gpsTrace = gpsTrace == null ? GeoJsonFeatureCollection.of(List.of()) : gpsTrace;
        anchors = anchors == null ? GeoJsonFeatureCollection.of(List.of()) : anchors;
    }
}
