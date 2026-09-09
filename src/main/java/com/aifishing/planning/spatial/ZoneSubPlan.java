package com.aifishing.planning.spatial;

import com.aifishing.planning.candidate.CandidateSpot;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ZoneSubPlan(
        List<MicroStop> stops,
        int fishingMinutes,
        int internalTransitMinutes,
        int waitMinutes,
        int visitMinutes,
        double utility
) {
    public int visitMinutesOrFallback() {
        return visitMinutes > 0 ? visitMinutes : fishingMinutes + internalTransitMinutes + waitMinutes;
    }

    public record MicroStop(
            CandidateSpot spot,
            Instant arrivalAt,
            Instant departureAt,
            Point entry,
            Point exit,
            Geometry geometry,
            int fishingMinutes,
            int transitMinutes,
            double utility,
            String reason
    ) {
        public UUID fishingTargetId() {
            return spot == null ? null : spot.getFishingTargetId();
        }
    }
}
