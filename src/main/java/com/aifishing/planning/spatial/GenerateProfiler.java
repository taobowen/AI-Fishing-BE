package com.aifishing.planning.spatial;

import com.aifishing.planning.candidate.CompressionSummary;
import com.aifishing.planning.route.BeamSearchDiagnostics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread-local Generate Plan timings and counters. Snapshot-build stages remain for
 * {@code SpatialSnapshotJob}; Generate logs emit the Generate subset.
 */
public final class GenerateProfiler {

    public static final String STRATEGY_CONTEXT = "STRATEGY_CONTEXT";
    public static final String WEATHER_RESOLVE = "WEATHER_RESOLVE";
    public static final String STRATEGY_AI = "STRATEGY_AI";
    public static final String SPATIAL_SNAPSHOT_LOAD = "SPATIAL_SNAPSHOT_LOAD";
    public static final String DYNAMIC_SCORING = "DYNAMIC_SCORING";
    public static final String VISIT_OPTION_BUILD = "VISIT_OPTION_BUILD";
    public static final String ZONE_SUBPLANNER = "ZONE_SUBPLANNER";
    public static final String BEAM_SEARCH = "BEAM_SEARCH";
    public static final String TRANSIT_MATERIALIZATION = "TRANSIT_MATERIALIZATION";
    public static final String TACTICS_AI = "TACTICS_AI";
    public static final String PLAN_VALIDATION = "PLAN_VALIDATION";
    public static final String PLAN_PERSIST = "PLAN_PERSIST";
    public static final String TOTAL_GENERATE = "TOTAL_GENERATE";

    /** Snapshot-job stages (not typically in Generate logs). */
    public static final String STATIC_SPATIAL_LOAD = SPATIAL_SNAPSHOT_LOAD;
    public static final String TARGET_BUILD = "TARGET_BUILD";
    public static final String TARGET_SPLIT = "TARGET_SPLIT";
    public static final String STATIC_SAMPLE_BUILD = "STATIC_SAMPLE_BUILD";
    public static final String PHYSICAL_ZONE_BUILD = "PHYSICAL_ZONE_BUILD";
    public static final String PORTAL_BUILD = "PORTAL_BUILD";
    public static final String WATER_PATH_BUILD = "WATER_PATH_BUILD";
    public static final String OPERATIONAL_VISIT_BUILD = VISIT_OPTION_BUILD;
    public static final String DYNAMIC_TIME_SCORING = DYNAMIC_SCORING;
    public static final String ZONE_SUBPLAN = ZONE_SUBPLANNER;
    public static final String MACRO_ROUTE_SEARCH = BEAM_SEARCH;
    public static final String PERSIST = PLAN_PERSIST;
    public static final String TOTAL = TOTAL_GENERATE;

    private static final ThreadLocal<GenerateProfiler> CURRENT = new ThreadLocal<>();
    private static final GenerateProfiler NOOP = new GenerateProfiler(false);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final boolean recording;
    private final Map<String, Long> stageMillis = new LinkedHashMap<>();
    private final Map<String, Long> stageStarted = new LinkedHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Map<String, String> tags = new LinkedHashMap<>();
    private final CompressionSummary compression = new CompressionSummary();
    private final BeamSearchDiagnostics beamSearch;
    private final ZoneSubPlannerDiagnostics zoneSubPlanner;

    private GenerateProfiler() {
        this(true);
    }

    private GenerateProfiler(boolean recording) {
        this.recording = recording;
        this.beamSearch = new BeamSearchDiagnostics(recording);
        this.zoneSubPlanner = new ZoneSubPlannerDiagnostics(recording);
        if (!recording) {
            return;
        }
        for (String stage : new String[]{
                STRATEGY_CONTEXT, WEATHER_RESOLVE, STRATEGY_AI, SPATIAL_SNAPSHOT_LOAD,
                TARGET_BUILD, TARGET_SPLIT, STATIC_SAMPLE_BUILD, PHYSICAL_ZONE_BUILD, PORTAL_BUILD,
                WATER_PATH_BUILD, DYNAMIC_SCORING, VISIT_OPTION_BUILD, ZONE_SUBPLANNER, BEAM_SEARCH,
                TRANSIT_MATERIALIZATION, TACTICS_AI, PLAN_VALIDATION, PLAN_PERSIST, TOTAL_GENERATE
        }) {
            stageMillis.put(stage, 0L);
        }
    }

    public static GenerateProfiler begin() {
        GenerateProfiler profiler = new GenerateProfiler();
        CURRENT.set(profiler);
        RequestScoringCache.open();
        RequestSpatialCache.open();
        profiler.start(TOTAL_GENERATE);
        return profiler;
    }

    public static GenerateProfiler current() {
        GenerateProfiler profiler = CURRENT.get();
        return profiler == null ? NOOP : profiler;
    }

    public static boolean attached() {
        return CURRENT.get() != null;
    }

    public static void attach(GenerateProfiler profiler) {
        if (profiler != null && profiler.recording) {
            CURRENT.set(profiler);
        }
    }

    public static GenerateProfiler detach() {
        GenerateProfiler profiler = CURRENT.get();
        CURRENT.remove();
        RequestScoringCache.close();
        RequestSpatialCache.close();
        return profiler;
    }

    public static void clear() {
        CURRENT.remove();
        RequestScoringCache.close();
        RequestSpatialCache.close();
    }

    public void start(String stage) {
        if (!recording) {
            return;
        }
        stageStarted.put(stage, System.nanoTime());
    }

    public void end(String stage) {
        if (!recording) {
            return;
        }
        Long started = stageStarted.remove(stage);
        if (started == null) {
            return;
        }
        long millis = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
        stageMillis.merge(stage, millis, Long::sum);
    }

    public void count(String name) {
        count(name, 1);
    }

    public void count(String name, long delta) {
        if (!recording) {
            return;
        }
        counters.merge(name, delta, Long::sum);
    }

    public void set(String name, long value) {
        if (!recording) {
            return;
        }
        counters.put(name, value);
    }

    public void tag(String name, String value) {
        if (!recording || name == null || name.isBlank()) {
            return;
        }
        tags.put(name, value == null ? "" : value);
    }

    public CompressionSummary compression() {
        if (!recording) {
            return new CompressionSummary();
        }
        return compression;
    }

    public BeamSearchDiagnostics beamSearch() {
        return beamSearch;
    }

    public ZoneSubPlannerDiagnostics zoneSubPlanner() {
        return zoneSubPlanner;
    }

    public void mergeFrom(GenerateProfiler other) {
        if (!recording || other == null || other == this || !other.recording) {
            return;
        }
        other.stageMillis.forEach((stage, millis) -> {
            if (TOTAL_GENERATE.equals(stage)) {
                return;
            }
            stageMillis.merge(stage, millis, Long::sum);
        });
        other.counters.forEach((name, value) -> counters.merge(name, value, Long::sum));
        other.tags.forEach(tags::putIfAbsent);
        compression.merge(other.compression);
        beamSearch.merge(other.beamSearch);
        zoneSubPlanner.merge(other.zoneSubPlanner);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stagesMs", new LinkedHashMap<>(stageMillis));
        out.put("counters", new LinkedHashMap<>(counters));
        if (!tags.isEmpty()) {
            out.put("tags", new LinkedHashMap<>(tags));
        }
        Map<String, Object> compressionMap = compression.toMap();
        if (!compressionMap.isEmpty()) {
            out.put("compressionSummary", compressionMap);
        }
        Map<String, Object> beam = beamSearch.toMap();
        if (!beam.isEmpty()) {
            out.put("beamSearch", beam);
        }
        Map<String, Object> zoneSub = zoneSubPlanner.toMap();
        if (!zoneSub.isEmpty()) {
            out.put("zoneSubPlanner", zoneSub);
        }
        return out;
    }

    public Map<String, Object> generateLog(
            UUID planningRunId,
            UUID tripId,
            UUID lakeId,
            String pipeline,
            UUID snapshotId
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (planningRunId != null) {
            out.put("planningRunId", planningRunId.toString());
        }
        if (tripId != null) {
            out.put("tripId", tripId.toString());
        }
        if (lakeId != null) {
            out.put("lakeId", lakeId.toString());
        }
        if (pipeline != null) {
            out.put("pipeline", pipeline);
        }
        if (snapshotId != null) {
            out.put("snapshotId", snapshotId.toString());
        }
        putMs(out, "strategyContextMs", STRATEGY_CONTEXT);
        putMs(out, "weatherResolveMs", WEATHER_RESOLVE);
        putMs(out, "strategyAiMs", STRATEGY_AI);
        putMs(out, "snapshotLoadMs", SPATIAL_SNAPSHOT_LOAD);
        putMs(out, "scoringMs", DYNAMIC_SCORING);
        putMs(out, "visitOptionBuildMs", VISIT_OPTION_BUILD);
        putMs(out, "zoneSubPlannerMs", ZONE_SUBPLANNER);
        putMs(out, "beamMs", BEAM_SEARCH);
        putMs(out, "transitMs", TRANSIT_MATERIALIZATION);
        putMs(out, "tacticsAiMs", TACTICS_AI);
        putMs(out, "validationMs", PLAN_VALIDATION);
        putMs(out, "persistMs", PLAN_PERSIST);
        putMs(out, "totalMs", TOTAL_GENERATE);
        copyCounter(out, "strategyAiCalls");
        copyCounter(out, "strategyAiRetries");
        copyCounter(out, "snapshotTargetCount");
        copyCounter(out, "snapshotZoneCount");
        copyCounter(out, "macroCandidateCount");
        copyCounter(out, "macroVisitOptionCount");
        copyCounter(out, "candidatesBeforeDedup");
        copyCounter(out, "candidatesAfterDedup");
        copyCounter(out, "physicalZonesConsidered");
        copyCounter(out, "zoneSubPlannerCalls");
        copyCounter(out, "zoneSubPlannerCacheHits");
        RequestScoringCache scoring = RequestScoringCache.current();
        if (scoring != null) {
            out.put("packageCacheHits", scoring.packageHits());
            out.put("packageCacheMisses", scoring.packageMisses());
            out.put("packageCacheHitRate", RequestScoringCache.hitRate(scoring.packageHits(), scoring.packageMisses()));
            out.put("utilityCacheHits", scoring.utilityHits());
            out.put("utilityCacheMisses", scoring.utilityMisses());
            out.put("utilityCacheHitRate", RequestScoringCache.hitRate(scoring.utilityHits(), scoring.utilityMisses()));
            out.put("alongPathCacheHits", scoring.alongHits());
            out.put("evaluateAtCacheHits", scoring.evaluationHits());
            out.put("avoidedComputeCount", scoring.avoidedComputeCount());
        }
        RequestSpatialCache spatial = RequestSpatialCache.current();
        if (spatial != null) {
            out.put("geodesicCacheHits", spatial.geodesicHits());
            out.put("geodesicCacheMisses", spatial.geodesicMisses());
            out.put("geodesicCacheHitRate", RequestSpatialCache.hitRate(spatial.geodesicHits(), spatial.geodesicMisses()));
            out.put("landCrossingCacheHits", spatial.landHits());
            out.put("landCrossingCacheMisses", spatial.landMisses());
            out.put("landCrossingCacheHitRate", RequestSpatialCache.hitRate(spatial.landHits(), spatial.landMisses()));
            out.put("returnGeometryCacheHits", spatial.returnHits());
            out.put("returnGeometryCacheMisses", spatial.returnMisses());
            out.put("returnGeometryCacheHitRate", RequestSpatialCache.hitRate(spatial.returnHits(), spatial.returnMisses()));
            out.put("spatialAvoidedComputations", spatial.avoidedComputations());
        }
        copyCounter(out, "beamWidthEffective");
        copyCounter(out, "maxStopsEffective");
        copyCounter(out, "lookaheadHorizonEffective");
        copyCounter(out, "beamDepthReached");
        copyCounter(out, "beamExpansions");
        copyCounter(out, "beamStatesPruned");
        copyCounter(out, "expansionBudget");
        copyCounter(out, "expansionBudgetUsed");
        copyCounter(out, "usableMinutes");
        copyCounter(out, "representativeDwellMinutes");
        copyCounter(out, "representativeInterStopMinutes");
        copyCounter(out, "expectedStops");
        copyCounter(out, "conservativeDwellMinutes");
        copyCounter(out, "optimisticInterStopMinutes");
        copyCounter(out, "maxFeasibleStops");
        copyCounter(out, "effectiveMaxStops");
        copyCounter(out, "routeUtilityMilli");
        copyCounter(out, "searchTimeGuardHit");
        copyCounter(out, "tacticsRequested");
        copyCounter(out, "waterPathCacheHits");
        copyCounter(out, "waterPathCacheMisses");
        copyCounter(out, "astarCalls");
        copyCounter(out, "astarExpandedCells");
        copyCounter(out, "astarTimeNs");
        copyCounter(out, "waterPathLookups");
        copyCounter(out, "committedLegReplayAvoided");
        copyCounter(out, "navigatedKmUpdates");
        copyCounter(out, "tacticsAiCalls");
        if (tags.containsKey("searchMode")) {
            out.put("searchMode", tags.get("searchMode"));
        }
        if (tags.containsKey("searchModeReason")) {
            out.put("searchModeReason", tags.get("searchModeReason"));
        }
        if (tags.containsKey("tacticsStatus")) {
            out.put("tacticsStatus", tags.get("tacticsStatus"));
        }
        Map<String, Object> compressionMap = compression.toMap();
        if (!compressionMap.isEmpty()) {
            out.put("compressionSummary", compressionMap);
        }
        Map<String, Object> beam = beamSearch.toMap();
        if (!beam.isEmpty()) {
            out.put("beamSearch", beam);
        }
        Map<String, Object> zoneSub = zoneSubPlanner.toMap();
        if (!zoneSub.isEmpty()) {
            out.put("zoneSubPlanner", zoneSub);
        }
        return out;
    }

    public String generateLogJson(
            UUID planningRunId,
            UUID tripId,
            UUID lakeId,
            String pipeline,
            UUID snapshotId
    ) {
        try {
            return JSON.writeValueAsString(generateLog(planningRunId, tripId, lakeId, pipeline, snapshotId));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    public long stageMs(String stage) {
        return stageMillis.getOrDefault(stage, 0L);
    }

    public long counter(String name) {
        return counters.getOrDefault(name, 0L);
    }

    private void putMs(Map<String, Object> out, String key, String stage) {
        out.put(key, stageMs(stage));
    }

    private void copyCounter(Map<String, Object> out, String name) {
        if (counters.containsKey(name)) {
            out.put(name, counters.get(name));
        }
    }
}
