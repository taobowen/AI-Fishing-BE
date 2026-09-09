package com.aifishing.lake.processing.benchmark;

import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.lake.processing.geo.FeatureGeometryMatch;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class FeatureMatcher {

    public List<Match> matchPredictedToGold(List<LakeFeature> predicted, List<GoldFeature> gold) {
        List<Candidate> candidates = new ArrayList<>();
        for (int p = 0; p < predicted.size(); p++) {
            LakeFeature feature = predicted.get(p);
            for (int g = 0; g < gold.size(); g++) {
                GoldFeature reference = gold.get(g);
                if (feature.getType() != reference.type()) {
                    continue;
                }
                if (!FeatureGeometryMatch.matches(feature.getType(), feature.getGeometry(), reference.geometry())) {
                    continue;
                }
                candidates.add(new Candidate(p, g, feature.getType(), feature.getGeometry(), reference.geometry()));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::quality).reversed());
        Set<Integer> usedPredicted = new HashSet<>();
        Set<Integer> usedGold = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (usedPredicted.contains(candidate.predictedIndex) || usedGold.contains(candidate.goldIndex)) {
                continue;
            }
            usedPredicted.add(candidate.predictedIndex);
            usedGold.add(candidate.goldIndex);
            matches.add(new Match(
                    candidate.type,
                    candidate.predictedIndex,
                    candidate.goldIndex,
                    FeatureGeometryMatch.iou(candidate.predicted, candidate.gold),
                    GeoMetrics.distanceM(candidate.predicted, candidate.gold)
            ));
        }
        return matches;
    }

    public List<Match> matchPipelines(List<LakeFeature> left, List<LakeFeature> right) {
        List<GoldFeature> asGold = right.stream()
                .map(feature -> new GoldFeature(feature.getType(), feature.getGeometry(), null, null))
                .toList();
        return matchPredictedToGold(left, asGold);
    }

    public record Match(
            FeatureType type,
            int predictedIndex,
            int goldIndex,
            double iou,
            double distanceM
    ) {
    }

    private record Candidate(
            int predictedIndex,
            int goldIndex,
            FeatureType type,
            Geometry predicted,
            Geometry gold
    ) {
        double quality() {
            return FeatureGeometryMatch.matchQuality(type, predicted, gold);
        }
    }
}
