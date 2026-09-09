package com.aifishing.planning.spatial;

import com.aifishing.planning.PlanningProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Observable skip record for malformed spatial products.
 * A snapshot may be READY only while skip ratios stay under configured thresholds.
 */
public final class SpatialSkipLedger {

    public static final String KIND_TARGET = "TARGET";
    public static final String KIND_PARTITION = "PARTITION";
    private static final int MAX_RECORDED_IDS = 50;

    public record Skip(String kind, String id, String reason) {
    }

    private final List<Skip> skips = new ArrayList<>();
    private int attemptedPartitions;

    public void skipTarget(UUID id, String reason) {
        skip(KIND_TARGET, id == null ? "unknown" : id.toString(), reason);
    }

    public void skipPartition(String id, String reason) {
        skip(KIND_PARTITION, id == null ? "unknown" : id, reason);
    }

    public void skip(String kind, String id, String reason) {
        skips.add(new Skip(kind, id, reason == null ? "UNKNOWN" : reason));
    }

    public void addAttemptedPartition() {
        attemptedPartitions++;
    }

    public int skippedTargetCount() {
        return (int) skips.stream().filter(skip -> KIND_TARGET.equals(skip.kind())).count();
    }

    public int skippedPartitionCount() {
        return (int) skips.stream().filter(skip -> KIND_PARTITION.equals(skip.kind())).count();
    }

    public int attemptedPartitionCount() {
        return attemptedPartitions;
    }

    public List<Skip> skips() {
        return List.copyOf(skips);
    }

    public boolean exceedsThreshold(int generatedTargetCount, PlanningProperties.Spatial spatial) {
        return targetRatio(generatedTargetCount) > spatial.getMaxSkippedTargetRatio()
                || partitionRatio() > spatial.getMaxSkippedPartitionRatio();
    }

    public String failureMessage(int generatedTargetCount, PlanningProperties.Spatial spatial) {
        return "SPATIAL_QUALITY_THRESHOLD: skippedTargets="
                + skippedTargetCount() + "/" + (generatedTargetCount + skippedTargetCount())
                + " (max " + spatial.getMaxSkippedTargetRatio() + "), skippedPartitions="
                + skippedPartitionCount() + "/" + Math.max(1, attemptedPartitions)
                + " (max " + spatial.getMaxSkippedPartitionRatio() + ")";
    }

    public Map<String, Object> toCounts(int generatedTargetCount) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("skippedTargetCount", skippedTargetCount());
        out.put("skippedPartitionCount", skippedPartitionCount());
        out.put("attemptedPartitionCount", attemptedPartitions);
        out.put("skippedTargetRatio", round(targetRatio(generatedTargetCount)));
        out.put("skippedPartitionRatio", round(partitionRatio()));
        Map<String, Integer> byReason = new LinkedHashMap<>();
        List<Map<String, String>> targetIds = new ArrayList<>();
        List<Map<String, String>> partitionIds = new ArrayList<>();
        for (Skip skip : skips) {
            byReason.merge(skip.reason(), 1, Integer::sum);
            Map<String, String> row = Map.of("id", skip.id(), "reason", skip.reason());
            if (KIND_TARGET.equals(skip.kind()) && targetIds.size() < MAX_RECORDED_IDS) {
                targetIds.add(row);
            }
            if (KIND_PARTITION.equals(skip.kind()) && partitionIds.size() < MAX_RECORDED_IDS) {
                partitionIds.add(row);
            }
        }
        out.put("skippedByReason", byReason);
        out.put("skippedTargets", targetIds);
        out.put("skippedPartitions", partitionIds);
        return out;
    }

    private double targetRatio(int generatedTargetCount) {
        int skipped = skippedTargetCount();
        int denom = generatedTargetCount + skipped;
        return denom == 0 ? 0 : skipped / (double) denom;
    }

    private double partitionRatio() {
        if (attemptedPartitions <= 0) {
            return skippedPartitionCount() > 0 ? 1.0 : 0;
        }
        return skippedPartitionCount() / (double) attemptedPartitions;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
