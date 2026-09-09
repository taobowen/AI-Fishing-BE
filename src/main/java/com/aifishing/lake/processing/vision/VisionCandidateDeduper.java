package com.aifishing.lake.processing.vision;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.geo.FeatureGeometryMatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VisionCandidateDeduper {

    private VisionCandidateDeduper() {
    }

    public static List<VisionCandidate> dedupe(List<VisionCandidate> candidates) {
        List<VisionCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparing(
                (VisionCandidate candidate) -> candidate.modelConfidence() == null ? 0.0 : candidate.modelConfidence()
        ).reversed());
        List<VisionCandidate> kept = new ArrayList<>();
        for (VisionCandidate candidate : ranked) {
            boolean duplicate = kept.stream().anyMatch(existing ->
                    existing.type() == candidate.type()
                            && FeatureGeometryMatch.matches(candidate.type(), existing.geometry(), candidate.geometry()));
            if (!duplicate) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    public static List<VisionCandidate> ofType(List<VisionCandidate> candidates, FeatureType type) {
        return candidates.stream().filter(candidate -> candidate.type() == type).toList();
    }
}
