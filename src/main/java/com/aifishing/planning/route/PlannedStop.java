package com.aifishing.planning.route;

import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.ZoneSubPlan;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlannedStop(
        RankedCandidate candidate,
        Instant arrivalAt,
        Instant departureAt,
        int stayMinutes,
        TravelEstimate fromPrevious,
        ScoreBreakdown timeScore,
        List<String> whyThisTime,
        Map<String, Object> environment,
        int precedingWaitMinutes,
        String precedingWaitLocation,
        FishingVisitOption visitOption,
        ZoneSubPlan zoneSubPlan,
        int plannedFishingMinutes,
        int plannedInternalTransitMinutes,
        int plannedWaitMinutes
) {
    public PlannedStop(
            RankedCandidate candidate,
            Instant arrivalAt,
            Instant departureAt,
            int stayMinutes,
            TravelEstimate fromPrevious,
            ScoreBreakdown timeScore,
            List<String> whyThisTime,
            Map<String, Object> environment,
            int precedingWaitMinutes,
            String precedingWaitLocation
    ) {
        this(
                candidate,
                arrivalAt,
                departureAt,
                stayMinutes,
                fromPrevious,
                timeScore,
                whyThisTime,
                environment,
                precedingWaitMinutes,
                precedingWaitLocation,
                null,
                null,
                stayMinutes,
                0,
                0
        );
    }

    public UUID visitId() {
        return candidate == null || candidate.spot() == null ? null : candidate.spot().planningIdentity();
    }

    public Instant arrival() {
        return arrivalAt;
    }

    public Instant departure() {
        return departureAt;
    }

    public Point exitPoint() {
        if (visitOption != null && visitOption.exitPoint() != null) {
            return visitOption.exitPoint();
        }
        return candidate.spot().getExitPoint();
    }

    public Point entryPoint() {
        if (visitOption != null && visitOption.entryPoint() != null) {
            return visitOption.entryPoint();
        }
        return candidate.spot().getEntryPoint();
    }

    public TargetKind targetKind() {
        return candidate.spot().getTargetKind();
    }

    public Geometry targetGeometry() {
        return candidate.spot().getTargetGeometry();
    }
}
