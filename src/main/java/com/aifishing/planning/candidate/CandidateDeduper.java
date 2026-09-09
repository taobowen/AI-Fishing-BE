package com.aifishing.planning.candidate;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CandidateDeduper {

    public List<CandidateSpot> dedupe(List<CandidateSpot> candidates, double minSpacingM, int maxPerType, int maxTotal) {
        List<CandidateSpot> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((CandidateSpot spot) ->
                spot.getStrategyWeight() * (spot.getFeatureConfidence() == null ? 0.5 : spot.getFeatureConfidence())
        ).reversed());
        List<CandidateSpot> kept = new ArrayList<>();
        Map<FeatureType, Integer> perType = new EnumMap<>(FeatureType.class);
        for (CandidateSpot candidate : ranked) {
            if (kept.size() >= maxTotal) {
                break;
            }
            int typeCount = perType.getOrDefault(candidate.getType(), 0);
            if (typeCount >= maxPerType) {
                continue;
            }
            if (tooCloseOrOverlap(candidate, kept, minSpacingM)) {
                continue;
            }
            kept.add(candidate);
            perType.put(candidate.getType(), typeCount + 1);
        }
        return kept;
    }

    private boolean tooCloseOrOverlap(CandidateSpot candidate, List<CandidateSpot> kept, double minSpacingM) {
        for (CandidateSpot other : kept) {
            if (candidate.getLocation() != null && other.getLocation() != null
                    && GeoMetrics.distanceM(candidate.getLocation(), other.getLocation()) < minSpacingM) {
                return true;
            }
            if (substantialOverlap(candidate.getSourceGeometry(), other.getSourceGeometry())) {
                return true;
            }
        }
        return false;
    }

    static boolean substantialOverlap(Geometry a, Geometry b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.getDimension() < 2 || b.getDimension() < 2) {
            return false;
        }
        Geometry intersection = a.intersection(b);
        double interArea = GeoMetrics.areaM2(intersection);
        double minArea = Math.min(GeoMetrics.areaM2(a), GeoMetrics.areaM2(b));
        return minArea > 0 && interArea / minArea > 0.6;
    }
}
