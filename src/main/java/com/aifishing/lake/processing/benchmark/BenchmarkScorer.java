package com.aifishing.lake.processing.benchmark;

import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BenchmarkScorer {

    private final FeatureMatcher matcher;

    public BenchmarkScorer(FeatureMatcher matcher) {
        this.matcher = matcher;
    }

    public Map<String, Object> scoreAgainstGold(List<LakeFeature> predicted, List<GoldFeature> gold) {
        if (gold == null || gold.isEmpty()) {
            return null;
        }
        List<FeatureMatcher.Match> matches = matcher.matchPredictedToGold(predicted, gold);
        Map<String, Object> overall = metrics(predicted.size(), gold.size(), matches);
        Map<String, Object> perType = new LinkedHashMap<>();
        for (FeatureType type : FeatureType.values()) {
            List<LakeFeature> predType = predicted.stream().filter(feature -> feature.getType() == type).toList();
            List<GoldFeature> goldType = gold.stream().filter(feature -> feature.type() == type).toList();
            if (predType.isEmpty() && goldType.isEmpty()) {
                continue;
            }
            List<FeatureMatcher.Match> typeMatches = matcher.matchPredictedToGold(predType, goldType);
            perType.put(type.name(), metrics(predType.size(), goldType.size(), typeMatches));
        }
        overall.put("perType", perType);
        return overall;
    }

    public Map<String, Object> agreement(List<LakeFeature> gis, List<LakeFeature> vision) {
        List<FeatureMatcher.Match> matches = matcher.matchPipelines(gis, vision);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matches.size());
        result.put("gisOnly", Math.max(0, gis.size() - matches.size()));
        result.put("visionOnly", Math.max(0, vision.size() - matches.size()));
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (FeatureType type : FeatureType.values()) {
            long typeMatches = matches.stream().filter(match -> match.type() == type).count();
            byType.put(type.name(), (int) typeMatches);
        }
        result.put("matchedByType", byType);
        return result;
    }

    public double inLakePercent(List<LakeFeature> features, Geometry lakeBoundary) {
        if (features.isEmpty()) {
            return 100.0;
        }
        if (lakeBoundary == null) {
            return 100.0;
        }
        long inside = features.stream()
                .filter(feature -> feature.getGeometry() != null && feature.getGeometry().intersects(lakeBoundary))
                .count();
        return round(100.0 * inside / features.size());
    }

    private Map<String, Object> metrics(int predicted, int gold, List<FeatureMatcher.Match> matches) {
        double precision = predicted == 0 ? 0 : matches.size() / (double) predicted;
        double recall = gold == 0 ? 0 : matches.size() / (double) gold;
        double f1 = (precision + recall) == 0 ? 0 : 2 * precision * recall / (precision + recall);
        double meanIou = matches.stream().mapToDouble(FeatureMatcher.Match::iou).average().orElse(0);
        double meanDistance = matches.stream().mapToDouble(FeatureMatcher.Match::distanceM).average().orElse(0);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("precision", round(precision));
        metrics.put("recall", round(recall));
        metrics.put("f1", round(f1));
        metrics.put("matched", matches.size());
        metrics.put("predicted", predicted);
        metrics.put("gold", gold);
        metrics.put("falsePositives", Math.max(0, predicted - matches.size()));
        metrics.put("missed", Math.max(0, gold - matches.size()));
        metrics.put("meanIou", round(meanIou));
        metrics.put("meanLocationErrorM", round(meanDistance));
        return metrics;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
