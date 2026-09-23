package com.aifishing.planning.route;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.spatial.TargetKind;

import java.util.ArrayList;
import java.util.List;

final class DwellPolicy {

    private DwellPolicy() {
    }

    static List<Integer> options(RankedCandidate candidate, int remainingMinutes, PlanningProperties.Schedule schedule) {
        TargetKind kind = candidate.spot().getTargetKind();
        FeatureType type = candidate.spot().getType();
        List<Integer> table;
        int hiCap;
        int lo;
        if (kind == TargetKind.ZONE) {
            table = schedule.getZonePackageMinutes();
            hiCap = schedule.getMaxZoneVisitMinutes();
            lo = 45;
        } else if (kind.isPathLike()) {
            table = schedule.getPathDwellMinutes();
            hiCap = Math.min(schedule.getMaxSpotMinutes(), 90);
            lo = 30;
        } else if (type == FeatureType.POINT || type == FeatureType.ISLAND_EDGE) {
            table = schedule.getPointDwellMinutes();
            hiCap = Math.min(schedule.getMaxSpotMinutes(), 45);
            lo = 15;
        } else if (type == FeatureType.FLAT) {
            table = schedule.getDwellOptionsMinutes();
            hiCap = Math.min(schedule.getMaxSpotMinutes(), 90);
            lo = 45;
        } else if (type == FeatureType.BASIN) {
            table = schedule.getDwellOptionsMinutes();
            hiCap = Math.min(schedule.getMaxSpotMinutes(), 60);
            lo = 30;
        } else {
            table = schedule.getDwellOptionsMinutes();
            hiCap = schedule.getMaxSpotMinutes();
            lo = schedule.getMinSpotMinutes();
        }
        double confidence = candidate.spot().getFeatureConfidence() == null ? 0.5 : candidate.spot().getFeatureConfidence();
        if (confidence < 0.45 && kind != TargetKind.ZONE) {
            hiCap = Math.min(hiCap, 45);
        }
        int hi = Math.min(hiCap, Math.max(0, remainingMinutes));
        List<Integer> chosen = new ArrayList<>();
        for (Integer option : table) {
            if (option != null && option >= lo && option <= hi) {
                chosen.add(option);
            }
        }
        if (chosen.isEmpty() && hi >= 8) {
            chosen.add(Math.min(hi, Math.max(lo, 8)));
        }
        return chosen;
    }
}
