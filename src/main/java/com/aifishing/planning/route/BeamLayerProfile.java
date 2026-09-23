package com.aifishing.planning.route;

import com.aifishing.planning.spatial.RequestScoringCache;
import com.aifishing.planning.spatial.RequestSpatialCache;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Beam-layer timing. Off unless {@code BEAM_PROFILE=true}. When off, {@link #open}
 * returns immediately and does not allocate or read the clock.
 */
public final class BeamLayerProfile {

    static final boolean ENABLED = Boolean.parseBoolean(System.getenv().getOrDefault("BEAM_PROFILE", "false"));

    public enum Stage {
        SUCCESSOR,
        HARD,
        WATER,
        SCORING,
        STATE_COPY,
        VISITED,
        DEDUPE,
        SORT,
        OTHER
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private BeamLayerProfile() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    static void begin(String lakeId) {
        if (!ENABLED) {
            return;
        }
        Trace trace = new Trace();
        trace.lakeId = lakeId;
        trace.gcCollections = gcCollections();
        trace.gcTimeMs = gcTimeMs();
        trace.wallStart = System.nanoTime();
        CURRENT.set(trace);
    }

    static void flush() {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        CURRENT.remove();
        if (trace == null) {
            return;
        }
        trace.gcCollections = gcCollections() - trace.gcCollections;
        trace.gcTimeMs = gcTimeMs() - trace.gcTimeMs;
        trace.wallMs = (System.nanoTime() - trace.wallStart) / 1_000_000.0;
        try {
            Path path = Path.of("target", "beam-profile-" + (trace.lakeId == null ? "unknown" : trace.lakeId) + ".json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, JSON.writeValueAsString(trace.toMap()));
        } catch (Exception ignored) {
            // Profiling must not fail the plan.
        }
    }

    static void beginLayer(int round, int depth, int frontier, int width) {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        LayerStats layer = new LayerStats(round, depth, frontier, width);
        trace.layer = layer;
        trace.layers.add(layer);
    }

    static void endLayer(int generated, int deduped, int kept, Map<String, Integer> rejects) {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null || trace.layer == null) {
            return;
        }
        trace.layer.generated = generated;
        trace.layer.deduped = deduped;
        trace.layer.kept = kept;
        if (rejects != null) {
            trace.layer.rejects.putAll(rejects);
        }
    }

    public static void noteReplayAvoided(int legs) {
        if (!ENABLED || legs <= 0) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.committedLegReplayAvoided += legs;
    }

    public static void noteNavigatedUpdate() {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.navigatedKmUpdates++;
    }

    public static void noteWaterLookup() {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.waterPathLookups++;
    }

    static void noteSort(int inputSize, int keep) {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("input", inputSize);
        row.put("keep", keep);
        row.put("mode", "full TimSort then prefix");
        trace.sorts.add(row);
        if (trace.layer != null) {
            trace.layer.sorted = inputSize;
        }
    }

    static void stateCopied(int stopCount) {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.stateCopies++;
        trace.stopListCopies++;
        trace.copiedStops += stopCount;
        if (trace.layer != null) {
            trace.layer.stateCopies++;
        }
    }

    static void environmentCopied() {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.environmentMaps++;
    }

    static void scoreCandidate(String candidateId, String kind, String from, String to) {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.evaluateAtCalls++;
        if (trace.layer != null) {
            trace.layer.scored++;
        }
        if (candidateId != null) {
            trace.byCandidate.merge(candidateId, 1, Integer::sum);
        }
        String key = candidateId + "|" + kind + "|" + from + "|" + to;
        trace.byOd.merge(key, 1, Integer::sum);
    }

    public static long open(Stage stage) {
        if (!ENABLED) {
            return 0L;
        }
        Trace trace = CURRENT.get();
        if (trace == null) {
            return 0L;
        }
        long now = System.nanoTime();
        trace.frames.add(new Frame(stage, now == 0L ? 1L : now, trace.nestedWall));
        return now == 0L ? 1L : now;
    }

    public static void close(long token) {
        if (token == 0L) {
            return;
        }
        Trace trace = CURRENT.get();
        if (trace == null || trace.frames.isEmpty()) {
            return;
        }
        Frame frame = trace.frames.remove(trace.frames.size() - 1);
        long elapsed = Math.max(0L, System.nanoTime() - frame.started);
        long nested = trace.nestedWall - frame.nestedAtStart;
        long exclusive = Math.max(0L, elapsed - nested);
        int index = frame.stage.ordinal();
        trace.stageNs[index] += exclusive;
        if (trace.layer != null) {
            trace.layer.stageNs[index] += exclusive;
        }
        trace.nestedWall = frame.nestedAtStart + elapsed;
    }

    private static long gcCollections() {
        long count = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, bean.getCollectionCount());
        }
        return count;
    }

    private static long gcTimeMs() {
        long time = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            time += Math.max(0, bean.getCollectionTime());
        }
        return time;
    }

    private static final class Frame {
        private final Stage stage;
        private final long started;
        private final long nestedAtStart;

        private Frame(Stage stage, long started, long nestedAtStart) {
            this.stage = stage;
            this.started = started;
            this.nestedAtStart = nestedAtStart;
        }
    }

    private static final class LayerStats {
        private final int round;
        private final int depth;
        private final int frontier;
        private final int width;
        private int generated;
        private int scored;
        private int deduped;
        private int kept;
        private int sorted;
        private int stateCopies;
        private final long[] stageNs = new long[Stage.values().length];
        private final Map<String, Integer> rejects = new LinkedHashMap<>();

        private LayerStats(int round, int depth, int frontier, int width) {
            this.round = round;
            this.depth = depth;
            this.frontier = frontier;
            this.width = width;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("round", round);
            row.put("depth", depth);
            row.put("frontier", frontier);
            row.put("width", width);
            row.put("generated", generated);
            row.put("scored", scored);
            row.put("deduped", deduped);
            row.put("kept", kept);
            row.put("sorted", sorted);
            row.put("stateCopies", stateCopies);
            row.put("rejects", rejects);
            row.put("stagesMs", stages(stageNs));
            return row;
        }
    }

    private static final class Trace {
        private String lakeId;
        private long wallStart;
        private double wallMs;
        private long gcCollections;
        private long gcTimeMs;
        private long nestedWall;
        private int evaluateAtCalls;
        private int stateCopies;
        private int stopListCopies;
        private int copiedStops;
        private int environmentMaps;
        private long committedLegReplayAvoided;
        private long navigatedKmUpdates;
        private long waterPathLookups;
        private final long[] stageNs = new long[Stage.values().length];
        private final List<Frame> frames = new ArrayList<>();
        private final List<LayerStats> layers = new ArrayList<>();
        private final List<Map<String, Object>> sorts = new ArrayList<>();
        private final Map<String, Integer> byCandidate = new HashMap<>();
        private final Map<String, Integer> byOd = new HashMap<>();
        private LayerStats layer;

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lakeId", lakeId);
            out.put("beamWallMs", round(wallMs));
            out.put("visitedState", "none");
            out.put("staticScoreRecomputed", false);
            out.put("sortMode", "full TimSort then prefix");
            List<Map<String, Object>> layerRows = new ArrayList<>();
            for (LayerStats layer : layers) {
                layerRows.add(layer.toMap());
            }
            out.put("layers", layerRows);
            Map<String, Double> stages = stages(stageNs);
            out.put("stagesMs", stages);
            Map<String, Object> navigation = new LinkedHashMap<>();
            navigation.put("committedLegReplayAvoided", committedLegReplayAvoided);
            navigation.put("navigatedKmUpdates", navigatedKmUpdates);
            navigation.put("waterPathLookups", waterPathLookups);
            navigation.put("waterPathLookupMs", stages.get(Stage.WATER.name()));
            out.put("navigation", navigation);
            double sum = stages.values().stream().mapToDouble(Double::doubleValue).sum();
            Map<String, Double> share = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : stages.entrySet()) {
                share.put(entry.getKey(), sum <= 0 ? 0 : round(100.0 * entry.getValue() / sum));
            }
            out.put("stageSharePct", share);
            out.put("accountedMs", round(sum));
            Map<String, Object> copies = new LinkedHashMap<>();
            copies.put("beamStateObjects", stateCopies);
            copies.put("stopListCopies", stopListCopies);
            copies.put("copiedStopSlots", copiedStops);
            copies.put("environmentMaps", environmentMaps);
            out.put("copies", copies);
            out.put("sorts", sorts);
            out.put("evaluateAtCalls", evaluateAtCalls);
            out.put("uniqueCandidatesScored", byCandidate.size());
            out.put("maxEvaluateAtPerCandidate", byCandidate.values().stream().mapToInt(Integer::intValue).max().orElse(0));
            out.put("uniqueOdPairs", byOd.size());
            out.put("maxOdRepeats", byOd.values().stream().mapToInt(Integer::intValue).max().orElse(0));
            out.put("odPairsRepeated", byOd.values().stream().filter(count -> count > 1).count());
            List<Map.Entry<String, Integer>> topOd = new ArrayList<>(byOd.entrySet());
            topOd.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
            List<Map<String, Object>> top = new ArrayList<>();
            for (int i = 0; i < Math.min(12, topOd.size()); i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("od", topOd.get(i).getKey());
                row.put("evaluations", topOd.get(i).getValue());
                top.add(row);
            }
            out.put("topOd", top);
            List<Map.Entry<String, Integer>> topCand = new ArrayList<>(byCandidate.entrySet());
            topCand.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
            List<Map<String, Object>> candRows = new ArrayList<>();
            for (int i = 0; i < Math.min(8, topCand.size()); i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("candidate", topCand.get(i).getKey());
                row.put("evaluateAt", topCand.get(i).getValue());
                candRows.add(row);
            }
            out.put("topCandidates", candRows);
            Map<String, Object> gc = new LinkedHashMap<>();
            gc.put("collections", gcCollections);
            gc.put("timeMs", gcTimeMs);
            out.put("gc", gc);
            RequestScoringCache scoring = RequestScoringCache.current();
            if (scoring != null) {
                Map<String, Object> packageCache = new LinkedHashMap<>();
                packageCache.put("hits", scoring.packageHits());
                packageCache.put("misses", scoring.packageMisses());
                packageCache.put("hitRate", RequestScoringCache.hitRate(scoring.packageHits(), scoring.packageMisses()));
                packageCache.put("utilityHits", scoring.utilityHits());
                packageCache.put("utilityMisses", scoring.utilityMisses());
                packageCache.put("utilityHitRate", RequestScoringCache.hitRate(scoring.utilityHits(), scoring.utilityMisses()));
                packageCache.put("avoidedComputeCount", scoring.avoidedComputeCount());
                out.put("packageCache", packageCache);
            }
            RequestSpatialCache spatial = RequestSpatialCache.current();
            if (spatial != null) {
                Map<String, Object> geometry = new LinkedHashMap<>();
                geometry.put("geodesicHits", spatial.geodesicHits());
                geometry.put("geodesicMisses", spatial.geodesicMisses());
                geometry.put("geodesicHitRate", RequestSpatialCache.hitRate(spatial.geodesicHits(), spatial.geodesicMisses()));
                geometry.put("landCrossingHits", spatial.landHits());
                geometry.put("landCrossingMisses", spatial.landMisses());
                geometry.put("landCrossingHitRate", RequestSpatialCache.hitRate(spatial.landHits(), spatial.landMisses()));
                geometry.put("returnGeometryHits", spatial.returnHits());
                geometry.put("returnGeometryMisses", spatial.returnMisses());
                geometry.put("returnGeometryHitRate", RequestSpatialCache.hitRate(spatial.returnHits(), spatial.returnMisses()));
                geometry.put("avoidedComputations", spatial.avoidedComputations());
                out.put("spatialCache", geometry);
            }
            return out;
        }
    }

    private static Map<String, Double> stages(long[] nanos) {
        Map<String, Double> out = new LinkedHashMap<>();
        Stage[] stages = Stage.values();
        for (int i = 0; i < stages.length; i++) {
            out.put(stages[i].name(), round(nanos[i] / 1_000_000.0));
        }
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
