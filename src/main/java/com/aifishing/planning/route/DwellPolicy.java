package com.aifishing.planning.route;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;

import java.util.ArrayList;
import java.util.List;

final class DwellPolicy {

    private DwellPolicy() {
    }

    static List<Integer> options(RankedCandidate candidate, int remainingMinutes, PlanningProperties.Schedule schedule) {
        int typeMin;
        int typeMax;
        FeatureType type = candidate.spot().getType();
        if (candidate.spot().getTargetKind() == com.aifishing.planning.spatial.TargetKind.ZONE) {
            typeMin = 45;
            typeMax = 90;
        } else if (candidate.spot().getTargetKind().isPathLike()) {
            typeMin = 20;
            typeMax = 60;
        } else if (type == FeatureType.POINT || type == FeatureType.ISLAND_EDGE) {
            typeMin = 20;
            typeMax = 45;
        } else if (type == FeatureType.FLAT) {
            typeMin = 45;
            typeMax = 90;
        } else if (type == FeatureType.BASIN) {
            typeMin = 30;
            typeMax = 60;
        } else {
            typeMin = 30;
            typeMax = 75;
        }
        double confidence = candidate.spot().getFeatureConfidence() == null ? 0.5 : candidate.spot().getFeatureConfidence();
        if (confidence < 0.45) {
            typeMax = Math.min(typeMax, 45);
        }
        int lo = Math.max(schedule.getMinSpotMinutes(), typeMin);
        int hi = Math.min(schedule.getMaxSpotMinutes(), typeMax);
        hi = Math.min(hi, Math.max(lo, remainingMinutes));
        List<Integer> chosen = new ArrayList<>();
        for (Integer option : schedule.getDwellOptionsMinutes()) {
            if (option != null && option >= lo && option <= hi) {
                chosen.add(option);
            }
        }
        if (chosen.isEmpty()) {
            chosen.add(Math.min(hi, Math.max(lo, schedule.getMinSpotMinutes())));
        }
        return chosen;
    }
}
