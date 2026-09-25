package com.aifishing.planning.candidate;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.spatial.TargetKind;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Collapses overlapping USER and AI opportunities before beam.
 * Precedence: {@link CandidateSource#REQUIRED} &gt; {@link CandidateSource#TEMPLATE} &gt; {@link CandidateSource#AI}.
 * Evidence metadata from the losing spot is absorbed onto the winner.
 */
@Component
public class PlanningOpportunityDeduper {

    static final double OVERLAP_DISTANCE_M = 75.0;

    public List<CandidateSpot> dedupe(List<CandidateSpot> spots) {
        if (spots == null || spots.isEmpty()) {
            return List.of();
        }
        List<CandidateSpot> ranked = new ArrayList<>(spots.size());
        for (CandidateSpot spot : spots) {
            ranked.add(spot.copy());
        }
        ranked.sort(Comparator
                .comparingInt((CandidateSpot spot) -> -precedence(spot.getCandidateSource()))
                .thenComparingDouble((CandidateSpot spot) -> -spot.getStrategyWeight())
                .thenComparing(spot -> String.valueOf(spot.planningIdentity())));
        List<CandidateSpot> kept = new ArrayList<>();
        for (CandidateSpot candidate : ranked) {
            CandidateSpot match = null;
            for (CandidateSpot existing : kept) {
                if (overlaps(candidate, existing)) {
                    match = existing;
                    break;
                }
            }
            if (match == null) {
                kept.add(candidate);
                continue;
            }
            if (precedence(candidate.getCandidateSource()) > precedence(match.getCandidateSource())) {
                absorb(candidate, match);
                kept.set(kept.indexOf(match), candidate);
            } else {
                absorb(match, candidate);
            }
        }
        return List.copyOf(kept);
    }

    static int precedence(CandidateSource source) {
        return switch (CandidateSource.orAi(source)) {
            case REQUIRED -> 3;
            case TEMPLATE -> 2;
            case AI -> 1;
        };
    }

    static boolean overlaps(CandidateSpot left, CandidateSpot right) {
        if (left == null || right == null) {
            return false;
        }
        UUID leftId = left.planningIdentity();
        UUID rightId = right.planningIdentity();
        if (leftId != null && leftId.equals(rightId)) {
            return true;
        }
        if (left.getFishingTargetId() != null
                && left.getFishingTargetId().equals(right.getFishingTargetId())) {
            return true;
        }
        if (left.getLocation() != null && right.getLocation() != null
                && GeoMetrics.distanceM(left.getLocation(), right.getLocation()) < OVERLAP_DISTANCE_M) {
            return true;
        }
        return pathOrPolygonOverlap(left, right);
    }

    private static boolean pathOrPolygonOverlap(CandidateSpot left, CandidateSpot right) {
        Geometry a = geometry(left);
        Geometry b = geometry(right);
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.getDimension() >= 1 && b.getDimension() >= 1) {
            try {
                if (!a.intersects(b)) {
                    return false;
                }
                if (a.getDimension() >= 2 && b.getDimension() >= 2) {
                    return CandidateDeduper.substantialOverlap(a, b);
                }
                return GeoMetrics.distanceM(a, b) < OVERLAP_DISTANCE_M
                        || a.intersection(b).getLength() > 0;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Geometry geometry(CandidateSpot spot) {
        if (spot.getTargetGeometry() != null && !spot.getTargetGeometry().isEmpty()) {
            return spot.getTargetGeometry();
        }
        return spot.getSourceGeometry();
    }

    static void absorb(CandidateSpot kept, CandidateSpot other) {
        if (kept == null || other == null) {
            return;
        }
        kept.addUnderlyingSource(other.getCandidateSource());
        kept.addUnderlyingSources(other.getUnderlyingSources());
        kept.setSourceFeatureIds(mergeIds(kept.getSourceFeatureIds(), other.getSourceFeatureIds()));
        kept.setCoverageIds(mergeIds(kept.getCoverageIds(), other.getCoverageIds()));
        kept.setEvidenceTypes(mergeTypes(kept.getEvidenceTypes(), other.getEvidenceTypes()));
        if (kept.getFeatureId() == null) {
            kept.setFeatureId(other.getFeatureId());
        }
        if ((kept.getEvidenceTypes() == null || kept.getEvidenceTypes().isEmpty())
                && other.getType() != null) {
            kept.setEvidenceTypes(List.of(other.getType()));
        }
        if (kept.getFeatureConfidence() == null) {
            kept.setFeatureConfidence(other.getFeatureConfidence());
        }
        if (kept.getTargetKind() == null) {
            kept.setTargetKind(other.getTargetKind() == null ? TargetKind.POINT : other.getTargetKind());
        }
        if (kept.getStrategyRationale() == null || kept.getStrategyRationale().isBlank()) {
            kept.setStrategyRationale(other.getStrategyRationale());
        }
        other.getWarnings().forEach(kept::addWarning);
    }

    private static List<UUID> mergeIds(List<UUID> left, List<UUID> right) {
        LinkedIdSet out = new LinkedIdSet();
        if (left != null) {
            left.stream().filter(Objects::nonNull).forEach(out::add);
        }
        if (right != null) {
            right.stream().filter(Objects::nonNull).forEach(out::add);
        }
        return out.toList();
    }

    private static List<com.aifishing.lake.processing.dto.FeatureType> mergeTypes(
            List<com.aifishing.lake.processing.dto.FeatureType> left,
            List<com.aifishing.lake.processing.dto.FeatureType> right
    ) {
        java.util.LinkedHashSet<com.aifishing.lake.processing.dto.FeatureType> out = new java.util.LinkedHashSet<>();
        if (left != null) {
            out.addAll(left);
        }
        if (right != null) {
            out.addAll(right);
        }
        return List.copyOf(out);
    }

    private static final class LinkedIdSet {
        private final java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();

        void add(UUID id) {
            ids.add(id);
        }

        List<UUID> toList() {
            return List.copyOf(ids);
        }
    }
}
