package com.aifishing.fishingsession.dto;

import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.fishingsession.domain.LocationQuality;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionTrackResponse(
        UUID sessionId,
        List<TrackPoint> points
) {
    public record TrackPoint(
            Instant recordedAt,
            GeoPointDto location,
            LocationQuality quality,
            Double accuracyM
    ) {
    }
}
