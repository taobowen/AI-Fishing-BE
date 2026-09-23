package com.aifishing.planning.route;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.CompressionSummary;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.TargetKind;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Structured Beam Search counters for GENERATE_PROFILE. Recording is a no-op when
 * {@code recording} is false so an unattached profiler cannot leak counts.
 */
public final class BeamSearchDiagnostics {

    private static final Set<BeamRejectReason> TRIP_WINDOW_REASONS = Set.of(
            BeamRejectReason.DEPARTURE_AFTER_TRIP_END,
            BeamRejectReason.RETURN_RESERVE,
            BeamRejectReason.RETURN_WEATHER_HARD_REJECT
    );

    private final boolean recording;
    private int selectedPhysicalZones;
    private int selectedStandalonePoints;
    private int selectedStandalonePaths;
    private int operationalZoneScopes;
    private int visitOptions;
    private final Map<String, Integer> visitOptionsByKind = new LinkedHashMap<>();
    private final EnumMap<BeamRejectReason, Integer> rejects = new EnumMap<>(BeamRejectReason.class);
    private int candidatesConsidered;
    private int successorsAccepted;
    private final IncrementStats incrementStats = new IncrementStats();
    private final Map<String, IncrementStats> incrementByVisitKind = new LinkedHashMap<>();
    private final Map<String, IncrementStats> incrementByCandidateKind = new LinkedHashMap<>();
    private BeamTerminationReason termination;
    private Layer currentLayer;
    private Layer lastActiveLayer;

    public BeamSearchDiagnostics() {
        this(true);
    }

    public BeamSearchDiagnostics(boolean recording) {
        this.recording = recording;
    }

    public void recordSearchEntry(
            List<RankedCandidate> ranked,
            List<FishingVisitOption> options,
            CompressionSummary compression
    ) {
        if (!recording) {
            return;
        }
        int zones = 0;
        int points = 0;
        int paths = 0;
        Set<UUID> physicalZoneIds = new HashSet<>();
        if (ranked != null) {
            for (RankedCandidate candidate : ranked) {
                if (candidate == null || candidate.spot() == null) {
                    continue;
                }
                CandidateSpot spot = candidate.spot();
                TargetKind kind = spot.getTargetKind();
                if (kind == TargetKind.ZONE) {
                    zones++;
                    if (spot.getZoneId() != null) {
                        physicalZoneIds.add(spot.getZoneId());
                    }
                } else if (kind != null && kind.isPathLike()) {
                    paths++;
                } else {
                    points++;
                }
            }
        }
        this.operationalZoneScopes = zones;
        this.selectedStandalonePoints = points;
        this.selectedStandalonePaths = paths;
        this.selectedPhysicalZones = compression != null && compression.zonesSelected() > 0
                ? compression.zonesSelected()
                : physicalZoneIds.size();
        this.visitOptions = options == null ? 0 : options.size();
        this.visitOptionsByKind.clear();
        if (options != null) {
            for (FishingVisitOption option : options) {
                String key = candidateKindLabel(option == null ? null : option.kind());
                visitOptionsByKind.merge(key, 1, Integer::sum);
            }
        }
    }

    public void beginLayer(int depth, int afterStops) {
        if (!recording) {
            return;
        }
        if (currentLayer != null && currentLayer.candidatesConsidered > 0) {
            lastActiveLayer = currentLayer.copy();
        }
        currentLayer = new Layer(depth, afterStops);
    }

    public void reject(BeamRejectReason reason) {
        reject(reason, 1);
    }

    public void reject(BeamRejectReason reason, int count) {
        if (!recording || reason == null || count <= 0) {
            return;
        }
        ensureLayer();
        rejects.merge(reason, count, Integer::sum);
        candidatesConsidered += count;
        currentLayer.rejects.merge(reason, count, Integer::sum);
        currentLayer.candidatesConsidered += count;
    }

    public void accept() {
        if (!recording) {
            return;
        }
        ensureLayer();
        candidatesConsidered++;
        successorsAccepted++;
        currentLayer.candidatesConsidered++;
        currentLayer.successorsAccepted++;
    }

    /**
     * Reclassify expand-accepted successors that later fail dominance or beam-width.
     * Does not increment {@code candidatesConsidered} again.
     */
    public void reclassifyAccepted(BeamRejectReason reason, int count) {
        if (!recording || reason == null || count <= 0) {
            return;
        }
        ensureLayer();
        int moved = Math.min(count, Math.max(0, currentLayer.successorsAccepted));
        if (moved <= 0) {
            return;
        }
        currentLayer.successorsAccepted -= moved;
        successorsAccepted = Math.max(0, successorsAccepted - moved);
        rejects.merge(reason, moved, Integer::sum);
        currentLayer.rejects.merge(reason, moved, Integer::sum);
    }

    public void recordNonPositiveIncrement(MacroVisitKind visitKind, TargetKind targetKind, double increment) {
        if (!recording) {
            return;
        }
        incrementStats.add(increment);
        incrementByVisitKind.computeIfAbsent(visitKindLabel(visitKind), ignored -> new IncrementStats()).add(increment);
        incrementByCandidateKind.computeIfAbsent(candidateKindLabel(targetKind), ignored -> new IncrementStats()).add(increment);
    }

    public void terminate(BeamTerminationReason reason) {
        if (!recording || reason == null || termination != null) {
            return;
        }
        if (currentLayer != null && currentLayer.candidatesConsidered > 0) {
            lastActiveLayer = currentLayer.copy();
        }
        if (reason == BeamTerminationReason.NO_SUCCESSORS && lastLayerOnly(BeamRejectReason.EXPANSION_BUDGET_EXCEEDED)) {
            termination = BeamTerminationReason.EXPANSION_BUDGET_EXCEEDED;
        } else if (reason == BeamTerminationReason.NO_SUCCESSORS && lastLayerIsTripWindowOnly()) {
            termination = BeamTerminationReason.TRIP_WINDOW_EXHAUSTED;
        } else {
            termination = reason;
        }
    }

    public boolean hasTermination() {
        return termination != null;
    }

    public BeamTerminationReason termination() {
        return termination;
    }

    public int rejectCount(BeamRejectReason reason) {
        return rejects.getOrDefault(reason, 0);
    }

    public int candidatesConsidered() {
        return candidatesConsidered;
    }

    public int successorsAccepted() {
        return successorsAccepted;
    }

    public Layer currentLayer() {
        return currentLayer;
    }

    public int selectedPhysicalZones() {
        return selectedPhysicalZones;
    }

    public int selectedStandalonePoints() {
        return selectedStandalonePoints;
    }

    public int selectedStandalonePaths() {
        return selectedStandalonePaths;
    }

    public int operationalZoneScopes() {
        return operationalZoneScopes;
    }

    public int visitOptionCount() {
        return visitOptions;
    }

    public Layer lastLayer() {
        if (currentLayer != null) {
            return currentLayer;
        }
        return lastActiveLayer;
    }

    public IncrementStats nonPositiveIncrement() {
        return incrementStats;
    }

    public void merge(BeamSearchDiagnostics other) {
        if (!recording || other == null || other == this || !other.recording) {
            return;
        }
        selectedPhysicalZones += other.selectedPhysicalZones;
        selectedStandalonePoints += other.selectedStandalonePoints;
        selectedStandalonePaths += other.selectedStandalonePaths;
        operationalZoneScopes += other.operationalZoneScopes;
        visitOptions += other.visitOptions;
        other.visitOptionsByKind.forEach((key, value) -> visitOptionsByKind.merge(key, value, Integer::sum));
        other.rejects.forEach((reason, count) -> rejects.merge(reason, count, Integer::sum));
        candidatesConsidered += other.candidatesConsidered;
        successorsAccepted += other.successorsAccepted;
        incrementStats.merge(other.incrementStats);
        other.incrementByVisitKind.forEach((key, stats) ->
                incrementByVisitKind.computeIfAbsent(key, ignored -> new IncrementStats()).merge(stats));
        other.incrementByCandidateKind.forEach((key, stats) ->
                incrementByCandidateKind.computeIfAbsent(key, ignored -> new IncrementStats()).merge(stats));
        if (termination == null) {
            termination = other.termination;
        }
        if (other.lastActiveLayer != null) {
            lastActiveLayer = other.lastActiveLayer.copy();
        }
        if (other.currentLayer != null && other.currentLayer.candidatesConsidered > 0) {
            currentLayer = other.currentLayer.copy();
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("selectedPhysicalZones", selectedPhysicalZones);
        out.put("selectedStandalonePoints", selectedStandalonePoints);
        out.put("selectedStandalonePaths", selectedStandalonePaths);
        out.put("operationalZoneScopes", operationalZoneScopes);
        out.put("visitOptions", visitOptions);
        if (!visitOptionsByKind.isEmpty()) {
            out.put("visitOptionsByKind", new LinkedHashMap<>(visitOptionsByKind));
        }
        out.put("candidatesConsidered", candidatesConsidered);
        out.put("successorsAccepted", successorsAccepted);
        out.put("rejects", positiveRejects(rejects));
        if (incrementStats.count > 0) {
            Map<String, Object> incrementMap = incrementStats.toMap();
            incrementMap.put("byVisitKind", statsMap(incrementByVisitKind));
            incrementMap.put("byCandidateKind", statsMap(incrementByCandidateKind));
            out.put("nonPositiveIncrement", incrementMap);
        }
        if (termination != null) {
            out.put("termination", termination.name());
        }
        Layer layer = lastLayer();
        if (layer != null) {
            out.put("lastLayer", layer.toMap());
        }
        return out;
    }

    private void ensureLayer() {
        if (currentLayer == null) {
            currentLayer = new Layer(0, 0);
        }
    }

    private boolean lastLayerOnly(BeamRejectReason reason) {
        Layer layer = lastLayer();
        if (layer == null || layer.successorsAccepted > 0 || layer.candidatesConsidered == 0) {
            return false;
        }
        return layer.rejectCount(reason) == layer.candidatesConsidered;
    }

    private boolean lastLayerIsTripWindowOnly() {
        Layer layer = lastLayer();
        if (layer == null || layer.successorsAccepted > 0 || layer.candidatesConsidered == 0) {
            return false;
        }
        boolean anyTime = false;
        for (Map.Entry<BeamRejectReason, Integer> entry : layer.rejects.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            if (!TRIP_WINDOW_REASONS.contains(entry.getKey())) {
                return false;
            }
            anyTime = true;
        }
        return anyTime;
    }

    static String visitKindLabel(MacroVisitKind kind) {
        if (kind == null) {
            return "UNKNOWN";
        }
        return switch (kind) {
            case NEW_ZONE_VISIT -> "NEW_ZONE";
            case NEW_ATOMIC -> "NEW_ATOMIC";
            case EXTEND_CURRENT_ZONE -> "EXTEND_CURRENT_ZONE";
            case REVISIT_PARTIALLY_CONSUMED_ZONE -> "REVISIT_PARTIALLY_CONSUMED_ZONE";
            case EXTEND_CURRENT_ATOMIC -> "EXTEND_CURRENT_ATOMIC";
        };
    }

    static String candidateKindLabel(TargetKind kind) {
        if (kind == TargetKind.ZONE) {
            return "ZONE";
        }
        if (kind != null && kind.isPathLike()) {
            return "PATH";
        }
        return "POINT";
    }

    private static Map<String, Integer> positiveRejects(EnumMap<BeamRejectReason, Integer> source) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (BeamRejectReason reason : BeamRejectReason.values()) {
            int count = source.getOrDefault(reason, 0);
            if (count > 0) {
                out.put(reason.name(), count);
            }
        }
        return out;
    }

    private static Map<String, Object> statsMap(Map<String, IncrementStats> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((key, stats) -> {
            if (stats.count > 0) {
                out.put(key, stats.toMap());
            }
        });
        return out;
    }

    public static final class Layer {
        private final int depth;
        private final int afterStops;
        private int candidatesConsidered;
        private int successorsAccepted;
        private final EnumMap<BeamRejectReason, Integer> rejects = new EnumMap<>(BeamRejectReason.class);

        Layer(int depth, int afterStops) {
            this.depth = depth;
            this.afterStops = afterStops;
        }

        Layer copy() {
            Layer copy = new Layer(depth, afterStops);
            copy.candidatesConsidered = candidatesConsidered;
            copy.successorsAccepted = successorsAccepted;
            copy.rejects.putAll(rejects);
            return copy;
        }

        public int depth() {
            return depth;
        }

        public int afterStops() {
            return afterStops;
        }

        public int candidatesConsidered() {
            return candidatesConsidered;
        }

        public int successorsAccepted() {
            return successorsAccepted;
        }

        public int rejectCount(BeamRejectReason reason) {
            return rejects.getOrDefault(reason, 0);
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("depth", depth);
            out.put("afterStops", afterStops);
            out.put("candidatesConsidered", candidatesConsidered);
            out.put("successorsAccepted", successorsAccepted);
            out.put("rejects", positiveRejects(rejects));
            return out;
        }
    }

    public static final class IncrementStats {
        private int count;
        private double sum;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        void add(double value) {
            count++;
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        void merge(IncrementStats other) {
            if (other == null || other.count <= 0) {
                return;
            }
            if (count == 0) {
                count = other.count;
                sum = other.sum;
                min = other.min;
                max = other.max;
                return;
            }
            count += other.count;
            sum += other.sum;
            min = Math.min(min, other.min);
            max = Math.max(max, other.max);
        }

        public int count() {
            return count;
        }

        public double min() {
            return count == 0 ? 0 : min;
        }

        public double max() {
            return count == 0 ? 0 : max;
        }

        public double avg() {
            return count == 0 ? 0 : sum / count;
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("count", count);
            if (count > 0) {
                out.put("min", min);
                out.put("max", max);
                out.put("avg", sum / count);
            }
            return out;
        }
    }
}
