package com.aifishing.planning.candidate;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class CandidateDeduper {

    public enum Mode {
        CURRENT,
        TRUE_DUPLICATE_ONLY
    }

    public record Result(List<CandidateSpot> kept, EnumMap<CandidateCompressionReason, Integer> reasons) {
        public Result {
            kept = kept == null ? List.of() : List.copyOf(kept);
            reasons = reasons == null ? new EnumMap<>(CandidateCompressionReason.class) : reasons;
        }

        public int dropped(CandidateCompressionReason reason) {
            return reasons.getOrDefault(reason, 0);
        }
    }

    public List<CandidateSpot> dedupe(List<CandidateSpot> candidates, double minSpacingM, int maxPerType, int maxTotal) {
        return diagnose(candidates, minSpacingM, maxPerType, maxTotal, Mode.CURRENT).kept();
    }

    public Result diagnose(
            List<CandidateSpot> candidates,
            double minSpacingM,
            int maxPerType,
            int maxTotal,
            Mode mode
    ) {
        Mode effective = mode == null ? Mode.CURRENT : mode;
        List<CandidateSpot> ranked = new ArrayList<>(candidates == null ? List.of() : candidates);
        ranked.sort(Comparator.comparingDouble((CandidateSpot spot) ->
                spot.getStrategyWeight() * (spot.getFeatureConfidence() == null ? 0.5 : spot.getFeatureConfidence())
        ).reversed());
        EnumMap<CandidateCompressionReason, Integer> reasons = new EnumMap<>(CandidateCompressionReason.class);
        if (effective == Mode.TRUE_DUPLICATE_ONLY) {
            return trueDuplicateOnly(ranked, reasons);
        }
        return currentSemantics(ranked, minSpacingM, maxPerType, maxTotal, reasons);
    }

    private static Result trueDuplicateOnly(
            List<CandidateSpot> ranked,
            EnumMap<CandidateCompressionReason, Integer> reasons
    ) {
        List<CandidateSpot> kept = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (CandidateSpot candidate : ranked) {
            UUID id = identity(candidate);
            if (id != null && !seen.add(id)) {
                increment(reasons, CandidateCompressionReason.TIME_VARIANT_MERGED);
                continue;
            }
            kept.add(candidate);
        }
        return new Result(kept, reasons);
    }

    private static Result currentSemantics(
            List<CandidateSpot> ranked,
            double minSpacingM,
            int maxPerType,
            int maxTotal,
            EnumMap<CandidateCompressionReason, Integer> reasons
    ) {
        List<CandidateSpot> kept = new ArrayList<>();
        Map<FeatureType, Integer> perType = new EnumMap<>(FeatureType.class);
        int cap = maxTotal <= 0 ? Integer.MAX_VALUE : maxTotal;
        int typeCap = maxPerType <= 0 ? Integer.MAX_VALUE : maxPerType;
        for (int i = 0; i < ranked.size(); i++) {
            CandidateSpot candidate = ranked.get(i);
            if (kept.size() >= cap) {
                increment(reasons, CandidateCompressionReason.REGIONAL_CANDIDATE_BUDGET, ranked.size() - i);
                break;
            }
            int typeCount = perType.getOrDefault(candidate.getType(), 0);
            if (typeCount >= typeCap) {
                increment(reasons, CandidateCompressionReason.FEATURE_TYPE_BUDGET);
                continue;
            }
            CandidateCompressionReason spatial = spatialReason(candidate, kept, minSpacingM);
            if (spatial != null) {
                increment(reasons, spatial);
                continue;
            }
            kept.add(candidate);
            perType.put(candidate.getType(), typeCount + 1);
        }
        return new Result(kept, reasons);
    }

    private static CandidateCompressionReason spatialReason(
            CandidateSpot candidate,
            List<CandidateSpot> kept,
            double minSpacingM
    ) {
        for (CandidateSpot other : kept) {
            boolean near = candidate.getLocation() != null && other.getLocation() != null
                    && GeoMetrics.distanceM(candidate.getLocation(), other.getLocation()) < minSpacingM;
            boolean overlap = substantialOverlap(candidate.getSourceGeometry(), other.getSourceGeometry());
            if (!near && !overlap) {
                continue;
            }
            if (sameIdentity(candidate, other)) {
                return CandidateCompressionReason.TIME_VARIANT_MERGED;
            }
            return near
                    ? CandidateCompressionReason.SPATIAL_NEAR_REDUNDANCY
                    : CandidateCompressionReason.GEOMETRY_OVERLAP_REDUNDANCY;
        }
        return null;
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

    private static UUID identity(CandidateSpot candidate) {
        if (candidate.getFishingTargetId() != null) {
            return candidate.getFishingTargetId();
        }
        return candidate.getFeatureId();
    }

    private static void increment(EnumMap<CandidateCompressionReason, Integer> reasons, CandidateCompressionReason reason) {
        increment(reasons, reason, 1);
    }

    private static void increment(
            EnumMap<CandidateCompressionReason, Integer> reasons,
            CandidateCompressionReason reason,
            int count
    ) {
        if (reason == null || count <= 0) {
            return;
        }
        reasons.merge(reason, count, Integer::sum);
    }

    static boolean sameIdentity(CandidateSpot a, CandidateSpot b) {
        return Objects.equals(identity(a), identity(b));
    }
}
