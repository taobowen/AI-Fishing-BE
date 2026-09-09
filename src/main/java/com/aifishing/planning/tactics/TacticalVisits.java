package com.aifishing.planning.tactics;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.ZoneSubPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TacticalVisits {

    private TacticalVisits() {
    }

    public static List<FishableVisit> extract(List<PlannedStop> stops) {
        List<FishableVisit> visits = new ArrayList<>();
        if (stops == null) {
            return visits;
        }
        for (PlannedStop stop : stops) {
            if (stop == null || stop.candidate() == null || stop.candidate().spot() == null) {
                continue;
            }
            CandidateSpot spot = stop.candidate().spot();
            if (spot.getTargetKind() == TargetKind.ZONE && stop.zoneSubPlan() != null
                    && stop.zoneSubPlan().stops() != null
                    && !stop.zoneSubPlan().stops().isEmpty()) {
                for (ZoneSubPlan.MicroStop micro : stop.zoneSubPlan().stops()) {
                    FishableVisit visit = fromMicro(micro);
                    if (visit != null) {
                        visits.add(visit);
                    }
                }
                continue;
            }
            if (!isFishable(spot.getTargetKind())) {
                continue;
            }
            UUID visitId = stop.visitId();
            if (visitId == null) {
                continue;
            }
            visits.add(new FishableVisit(
                    visitId,
                    spot.getTargetKind(),
                    spot.getType(),
                    spot.getRepresentativeDepthM(),
                    spot.getMinDepthM(),
                    spot.getMaxDepthM(),
                    spot.techniqueTypes(),
                    stop.arrivalAt(),
                    stop.departureAt(),
                    stop.plannedFishingMinutes(),
                    spot.getType() == null ? null : spot.getType().name(),
                    spot.getTargetKind() != null && spot.getTargetKind().isPathLike()
            ));
        }
        return visits;
    }

    private static FishableVisit fromMicro(ZoneSubPlan.MicroStop micro) {
        if (micro == null || micro.spot() == null) {
            return null;
        }
        CandidateSpot spot = micro.spot();
        UUID visitId = spot.planningIdentity();
        if (visitId == null) {
            return null;
        }
        return new FishableVisit(
                visitId,
                spot.getTargetKind() == null ? TargetKind.POINT : spot.getTargetKind(),
                spot.getType(),
                spot.getRepresentativeDepthM(),
                spot.getMinDepthM(),
                spot.getMaxDepthM(),
                spot.techniqueTypes(),
                micro.arrivalAt(),
                micro.departureAt(),
                micro.fishingMinutes(),
                spot.getType() == null ? null : spot.getType().name(),
                spot.getTargetKind() != null && spot.getTargetKind().isPathLike()
        );
    }

    private static boolean isFishable(TargetKind kind) {
        return kind == TargetKind.POINT
                || kind == TargetKind.PATH
                || kind == TargetKind.SEGMENT
                || kind == TargetKind.AREA;
    }
}
