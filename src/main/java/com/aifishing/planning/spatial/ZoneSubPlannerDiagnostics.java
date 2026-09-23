package com.aifishing.planning.spatial;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Structured ZoneSubPlanner timings for GENERATE_PROFILE. Recording is a no-op when
 * {@code recording} is false. Does not affect planning.
 */
public final class ZoneSubPlannerDiagnostics {

    private static final int TOP_N = 10;

    private final boolean recording;
    private int packagesCalls;
    private int computeCalls;
    private int cacheHits;
    private int cacheMisses;
    private int orientationRequests;
    private int orientationCacheHits;
    private int orientationCacheMisses;
    private long orientationResolveNs;
    private final Set<UUID> zoneIds = new HashSet<>();
    private final Set<UUID> scopeIds = new HashSet<>();
    private final Set<String> equivalentKeys = new HashSet<>();
    private final Set<String> timedKeys = new HashSet<>();
    private final Set<String> spatialNoDwellKeys = new HashSet<>();
    private int repeatedEquivalentCalls;
    private int repeatedTimedCalls;
    private final Set<Long> arrivalEpochSeconds = new HashSet<>();
    private final Map<Integer, Integer> dwellCallCounts = new LinkedHashMap<>();
    private final Map<Integer, Integer> remainingMemberCallCounts = new LinkedHashMap<>();
    private final Map<String, Set<String>> equivalentToTimed = new LinkedHashMap<>();
    private final Map<String, StageStats> stages = new LinkedHashMap<>();
    private final Map<String, ScopeStats> scopes = new LinkedHashMap<>();
    private final List<SlowCall> slowest = new ArrayList<>();
    private ComputeAcc current;

    public ZoneSubPlannerDiagnostics() {
        this(true);
    }

    public ZoneSubPlannerDiagnostics(boolean recording) {
        this.recording = recording;
        if (recording) {
            for (Stage stage : Stage.values()) {
                stages.put(stage.jsonName(), new StageStats());
            }
        }
    }

    public void beginCompute(
            String method,
            UUID zoneId,
            UUID visitScopeId,
            int memberCount,
            int remainingMemberCount,
            int entryPortalCount,
            int exitPortalCount,
            String entryPortalId,
            String exitPortalId,
            int requestedDwellMinutes,
            Instant arrival,
            int remainingMinutes
    ) {
        if (!recording) {
            return;
        }
        current = new ComputeAcc(
                method,
                zoneId,
                visitScopeId,
                memberCount,
                remainingMemberCount,
                entryPortalCount,
                exitPortalCount,
                entryPortalId,
                exitPortalId,
                requestedDwellMinutes,
                arrival,
                remainingMinutes
        );
        current.startedNs = System.nanoTime();
        computeCalls++;
        if (zoneId != null) {
            zoneIds.add(zoneId);
        }
        if (visitScopeId != null) {
            scopeIds.add(visitScopeId);
        }
        String equivalent = equivalentKey(zoneId, visitScopeId, entryPortalId, exitPortalId, requestedDwellMinutes, remainingMemberCount);
        String timed = timedKey(equivalent, arrival);
        String spatial = spatialKey(zoneId, visitScopeId, entryPortalId, exitPortalId);
        if (!equivalentKeys.add(equivalent)) {
            repeatedEquivalentCalls++;
        }
        if (!timedKeys.add(timed)) {
            repeatedTimedCalls++;
        }
        spatialNoDwellKeys.add(spatial);
        recordInputDelta(equivalent, timed, arrival, requestedDwellMinutes, remainingMemberCount);
        scope(zoneId, visitScopeId).recordCall(
                memberCount, remainingMemberCount, entryPortalCount, exitPortalCount, entryPortalId, exitPortalId, requestedDwellMinutes);
    }

    public void markCacheHit() {
        if (!recording || current == null) {
            return;
        }
        current.cacheHit = true;
        cacheHits++;
    }

    public void markCacheMiss() {
        if (!recording || current == null) {
            return;
        }
        current.cacheHit = false;
        cacheMisses++;
    }

    public void recordOrientationRequest() {
        if (!recording) {
            return;
        }
        orientationRequests++;
    }

    public void recordOrientationCacheHit() {
        if (!recording) {
            return;
        }
        orientationCacheHits++;
    }

    public void recordOrientationCacheMiss() {
        if (!recording) {
            return;
        }
        orientationCacheMisses++;
    }

    public void recordOrientationResolveNs(long nanos) {
        if (!recording || nanos <= 0) {
            return;
        }
        orientationResolveNs += nanos;
    }

    public void addStageNs(Stage stage, long nanos) {
        if (!recording || current == null || nanos <= 0) {
            return;
        }
        current.stageNs.merge(stage, nanos, Long::sum);
    }

    public void addStandaloneStageNs(Stage stage, long nanos) {
        if (!recording || stage == null || nanos <= 0) {
            return;
        }
        stages.computeIfAbsent(stage.jsonName(), ignored -> new StageStats()).add(nanos / 1_000_000.0);
    }

    public void recordWaterPath(boolean cacheHit, long lookupNs, long astarNs, long persistNs) {
        if (!recording) {
            return;
        }
        addStageNs(Stage.WATER_PATH_LOOKUP, lookupNs);
        addStageNs(Stage.ASTAR, astarNs);
        addStageNs(Stage.WATER_PATH_PERSIST, persistNs);
        if (current != null) {
            current.hops++;
            if (cacheHit) {
                current.waterPathHits++;
            } else {
                current.waterPathMisses++;
            }
        }
    }

    public void recordMemberEval() {
        if (!recording || current == null) {
            return;
        }
        current.memberEvals++;
    }

    public void recordPackagesResult(UUID zoneId, UUID visitScopeId, int dwellsConsidered, int packagesRetained) {
        if (!recording) {
            return;
        }
        packagesCalls++;
        ScopeStats scope = scope(zoneId, visitScopeId);
        scope.dwellsConsidered = Math.max(scope.dwellsConsidered, dwellsConsidered);
        scope.packagesRetained = Math.max(scope.packagesRetained, packagesRetained);
        scope.packagesCalls++;
    }

    public void endCompute(int packagesRetainedIfKnown, int sequencesConsidered) {
        if (!recording || current == null) {
            return;
        }
        long totalNs = Math.max(0, System.nanoTime() - current.startedNs);
        current.totalNs = totalNs;
        current.sequencesConsidered = sequencesConsidered;
        for (Map.Entry<Stage, Long> entry : current.stageNs.entrySet()) {
            stages.computeIfAbsent(entry.getKey().jsonName(), ignored -> new StageStats())
                    .add(entry.getValue() / 1_000_000.0);
        }
        long accounted = 0;
        for (Long ns : current.stageNs.values()) {
            accounted += ns;
        }
        long other = Math.max(0, totalNs - accounted);
        if (other > 0) {
            stages.computeIfAbsent(Stage.OTHER.jsonName(), ignored -> new StageStats()).add(other / 1_000_000.0);
            current.stageNs.merge(Stage.OTHER, other, Long::sum);
        }
        ScopeStats scope = scope(current.zoneId, current.visitScopeId);
        scope.totalMs += totalNs / 1_000_000.0;
        scope.computeCalls++;
        if (current.cacheHit) {
            scope.cacheHits++;
        }
        scope.memberSequencesConsidered += sequencesConsidered;
        if (packagesRetainedIfKnown >= 0) {
            scope.packagesRetained = Math.max(scope.packagesRetained, packagesRetainedIfKnown);
        }
        rememberSlow(current.toSlowCall());
        current = null;
    }

    public void endCompute() {
        if (!recording || current == null) {
            return;
        }
        endCompute(-1, current.sequencesConsidered);
    }

    public void setSequencesConsidered(int sequences) {
        if (!recording || current == null) {
            return;
        }
        current.sequencesConsidered = Math.max(0, sequences);
    }

    public void merge(ZoneSubPlannerDiagnostics other) {
        if (!recording || other == null || other == this || !other.recording) {
            return;
        }
        packagesCalls += other.packagesCalls;
        computeCalls += other.computeCalls;
        cacheHits += other.cacheHits;
        cacheMisses += other.cacheMisses;
        orientationRequests += other.orientationRequests;
        orientationCacheHits += other.orientationCacheHits;
        orientationCacheMisses += other.orientationCacheMisses;
        orientationResolveNs += other.orientationResolveNs;
        zoneIds.addAll(other.zoneIds);
        scopeIds.addAll(other.scopeIds);
        for (String key : other.equivalentKeys) {
            if (!equivalentKeys.add(key)) {
                repeatedEquivalentCalls++;
            }
        }
        for (String key : other.timedKeys) {
            if (!timedKeys.add(key)) {
                repeatedTimedCalls++;
            }
        }
        spatialNoDwellKeys.addAll(other.spatialNoDwellKeys);
        repeatedEquivalentCalls += other.repeatedEquivalentCalls;
        repeatedTimedCalls += other.repeatedTimedCalls;
        arrivalEpochSeconds.addAll(other.arrivalEpochSeconds);
        other.dwellCallCounts.forEach((key, value) -> dwellCallCounts.merge(key, value, Integer::sum));
        other.remainingMemberCallCounts.forEach((key, value) -> remainingMemberCallCounts.merge(key, value, Integer::sum));
        other.equivalentToTimed.forEach((key, values) ->
                equivalentToTimed.computeIfAbsent(key, ignored -> new HashSet<>()).addAll(values));
        other.stages.forEach((name, stats) -> stages.computeIfAbsent(name, ignored -> new StageStats()).merge(stats));
        other.scopes.forEach((key, stats) -> scopes.computeIfAbsent(key, ignored -> new ScopeStats(stats.zoneId, stats.visitScopeId)).merge(stats));
        for (SlowCall call : other.slowest) {
            rememberSlow(call);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!recording) {
            return out;
        }
        out.put("packagesCalls", packagesCalls);
        out.put("computeCalls", computeCalls);
        out.put("uniqueZoneIds", zoneIds.size());
        out.put("uniqueVisitScopeIds", scopeIds.size());
        out.put("uniqueEquivalentCalls", equivalentKeys.size());
        out.put("uniqueTimedCalls", timedKeys.size());
        out.put("uniqueSpatialPortalCalls", spatialNoDwellKeys.size());
        out.put("repeatedEquivalentCalls", repeatedEquivalentCalls);
        out.put("repeatedTimedCalls", repeatedTimedCalls);
        out.put("repeatedEquivalentPct", pct(repeatedEquivalentCalls, computeCalls));
        out.put("repeatedTimedPct", pct(repeatedTimedCalls, computeCalls));
        out.put("cacheHits", cacheHits);
        out.put("cacheMisses", cacheMisses);
        out.put("cacheHitPct", pct(cacheHits, computeCalls));
        out.put("orientationRequests", orientationRequests);
        out.put("orientationCacheHits", orientationCacheHits);
        out.put("orientationCacheMisses", orientationCacheMisses);
        out.put("orientationResolveMs", orientationResolveNs / 1_000_000.0);
        out.put("orientationCacheHitRate", pct(orientationCacheHits, orientationRequests));
        int spatialKeysWithMultipleArrivals = 0;
        for (Set<String> timed : equivalentToTimed.values()) {
            if (timed.size() > 1) {
                spatialKeysWithMultipleArrivals++;
            }
        }
        Map<String, Object> inputDeltas = new LinkedHashMap<>();
        inputDeltas.put("uniqueArrivalEpochSeconds", arrivalEpochSeconds.size());
        inputDeltas.put("dwellCallCounts", new LinkedHashMap<>(dwellCallCounts));
        inputDeltas.put("remainingMemberCallCounts", new LinkedHashMap<>(remainingMemberCallCounts));
        inputDeltas.put("equivalentKeysWithMultipleArrivals", spatialKeysWithMultipleArrivals);
        inputDeltas.put("changingInputs", List.of(
                "arrival (in cache key and greedy scoring)",
                "visitMinutes/dwell",
                "remainingMembers (STATE_DEPENDENT)",
                "entryPortalId/exitPortalId"
        ));
        out.put("inputDeltas", inputDeltas);
        Map<String, Object> stageMap = new LinkedHashMap<>();
        String dominant = null;
        double dominantTotal = -1;
        double pathTotal = 0;
        int pathCount = 0;
        for (Stage stage : Stage.values()) {
            StageStats stats = stages.get(stage.jsonName());
            if (stats == null || stats.count == 0) {
                continue;
            }
            if (stage == Stage.LOCAL_PATHFINDING) {
                continue;
            }
            Map<String, Object> row = stats.toMap();
            row.put("snapshotClass", stage.snapshotClass.name());
            stageMap.put(stage.jsonName(), row);
            if (stage == Stage.WATER_PATH_LOOKUP || stage == Stage.ASTAR || stage == Stage.WATER_PATH_PERSIST) {
                pathTotal += stats.sum;
                pathCount += stats.count;
            }
            if (stats.sum > dominantTotal) {
                dominantTotal = stats.sum;
                dominant = stage.jsonName();
            }
        }
        if (pathCount > 0) {
            Map<String, Object> path = new LinkedHashMap<>();
            path.put("count", pathCount);
            path.put("totalMs", pathTotal);
            path.put("snapshotClass", SnapshotClass.STATIC_PER_SNAPSHOT.name());
            stageMap.put(Stage.LOCAL_PATHFINDING.jsonName(), path);
            if (pathTotal > dominantTotal) {
                dominant = Stage.LOCAL_PATHFINDING.jsonName();
            }
        }
        if (dominant != null) {
            out.put("dominantStage", dominant);
        }
        out.put("stagesMs", stageMap);
        List<Map<String, Object>> scopeRows = new ArrayList<>();
        for (ScopeStats scope : scopes.values()) {
            scopeRows.add(scope.toMap());
        }
        scopeRows.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("totalMs", 0)).doubleValue(),
                ((Number) a.getOrDefault("totalMs", 0)).doubleValue()));
        out.put("scopes", scopeRows);
        List<Map<String, Object>> top = new ArrayList<>();
        for (SlowCall call : slowest) {
            top.add(call.toMap());
        }
        out.put("topSlowest", top);
        out.put("snapshotClasses", snapshotClassGuide());
        return out;
    }

    public int computeCalls() {
        return computeCalls;
    }

    public int cacheHits() {
        return cacheHits;
    }

    public int orientationRequests() {
        return orientationRequests;
    }

    public int orientationCacheHits() {
        return orientationCacheHits;
    }

    public int orientationCacheMisses() {
        return orientationCacheMisses;
    }

    public int uniqueEquivalentCalls() {
        return equivalentKeys.size();
    }

    private void recordInputDelta(String equivalent, String timed, Instant arrival, int dwell, int remainingMembers) {
        if (arrival != null) {
            arrivalEpochSeconds.add(arrival.getEpochSecond());
        }
        dwellCallCounts.merge(dwell, 1, Integer::sum);
        remainingMemberCallCounts.merge(remainingMembers, 1, Integer::sum);
        equivalentToTimed.computeIfAbsent(equivalent, ignored -> new HashSet<>()).add(timed);
    }

    private ScopeStats scope(UUID zoneId, UUID visitScopeId) {
        String key = String.valueOf(zoneId) + "|" + visitScopeId;
        return scopes.computeIfAbsent(key, ignored -> new ScopeStats(zoneId, visitScopeId));
    }

    private void rememberSlow(SlowCall call) {
        slowest.add(call);
        slowest.sort(Comparator.comparingDouble((SlowCall row) -> row.totalMs).reversed());
        if (slowest.size() > TOP_N) {
            slowest.subList(TOP_N, slowest.size()).clear();
        }
    }

    private static Map<String, String> snapshotClassGuide() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Stage stage : Stage.values()) {
            out.put(stage.jsonName(), stage.snapshotClass.name());
        }
        return out;
    }

    private static String equivalentKey(
            UUID zoneId,
            UUID visitScopeId,
            String entryPortalId,
            String exitPortalId,
            int dwell,
            int remainingMembers
    ) {
        return zoneId + "|" + visitScopeId + "|" + nullToEmpty(entryPortalId) + "|" + nullToEmpty(exitPortalId)
                + "|" + dwell + "|" + remainingMembers;
    }

    private static String timedKey(String equivalent, Instant arrival) {
        return equivalent + "|" + (arrival == null ? "" : arrival.toString());
    }

    private static String spatialKey(UUID zoneId, UUID visitScopeId, String entryPortalId, String exitPortalId) {
        return zoneId + "|" + visitScopeId + "|" + nullToEmpty(entryPortalId) + "|" + nullToEmpty(exitPortalId);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double pct(int part, int whole) {
        if (whole <= 0) {
            return 0;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    public enum SnapshotClass {
        STATIC_PER_SNAPSHOT,
        STATIC_PER_ZONE,
        STATIC_PER_SCOPE,
        STATE_DEPENDENT,
        TIME_WEATHER_DEPENDENT
    }

    public enum Stage {
        PACKAGE_ENUMERATION("packageEnumeration", SnapshotClass.STATE_DEPENDENT),
        MEMBER_FILTER("memberFilter", SnapshotClass.STATE_DEPENDENT),
        PORTAL_PAIR_GENERATION("portalPairGeneration", SnapshotClass.STATIC_PER_SCOPE),
        LOCAL_PATHFINDING("localPathfinding", SnapshotClass.STATIC_PER_SNAPSHOT),
        WATER_PATH_LOOKUP("waterPathLookup", SnapshotClass.STATIC_PER_SNAPSHOT),
        ASTAR("astar", SnapshotClass.STATIC_PER_SNAPSHOT),
        WATER_PATH_PERSIST("waterPathPersist", SnapshotClass.STATIC_PER_SNAPSHOT),
        MEMBER_SEQUENCING("memberSequencing", SnapshotClass.TIME_WEATHER_DEPENDENT),
        GEOMETRY("geometry", SnapshotClass.STATIC_PER_ZONE),
        ORIENTATION("orientation", SnapshotClass.STATIC_PER_ZONE),
        PACKAGE_UTILITY_SCORING("packageUtilityScoring", SnapshotClass.TIME_WEATHER_DEPENDENT),
        OTHER("other", SnapshotClass.STATE_DEPENDENT);

        private final String jsonName;
        private final SnapshotClass snapshotClass;

        Stage(String jsonName, SnapshotClass snapshotClass) {
            this.jsonName = jsonName;
            this.snapshotClass = snapshotClass;
        }

        public String jsonName() {
            return jsonName;
        }
    }

    static final class StageStats {
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

        void merge(StageStats other) {
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

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("count", count);
            if (count > 0) {
                out.put("totalMs", sum);
                out.put("avgMs", sum / count);
                out.put("minMs", min);
                out.put("maxMs", max);
            }
            return out;
        }
    }

    static final class ScopeStats {
        private final UUID zoneId;
        private final UUID visitScopeId;
        private int memberCount;
        private int remainingMemberCount;
        private int entryPortalCount;
        private int exitPortalCount;
        private final Set<String> portalPairs = new HashSet<>();
        private final Set<Integer> dwells = new HashSet<>();
        private int dwellsConsidered;
        private int memberSequencesConsidered;
        private int packagesRetained;
        private int packagesCalls;
        private int computeCalls;
        private int cacheHits;
        private double totalMs;

        ScopeStats(UUID zoneId, UUID visitScopeId) {
            this.zoneId = zoneId;
            this.visitScopeId = visitScopeId;
        }

        void recordCall(
                int memberCount,
                int remainingMemberCount,
                int entryPortalCount,
                int exitPortalCount,
                String entryPortalId,
                String exitPortalId,
                int dwell
        ) {
            this.memberCount = Math.max(this.memberCount, memberCount);
            this.remainingMemberCount = Math.max(this.remainingMemberCount, remainingMemberCount);
            this.entryPortalCount = Math.max(this.entryPortalCount, entryPortalCount);
            this.exitPortalCount = Math.max(this.exitPortalCount, exitPortalCount);
            portalPairs.add(nullToEmpty(entryPortalId) + "->" + nullToEmpty(exitPortalId));
            dwells.add(dwell);
            dwellsConsidered = Math.max(dwellsConsidered, dwells.size());
        }

        void merge(ScopeStats other) {
            if (other == null) {
                return;
            }
            memberCount = Math.max(memberCount, other.memberCount);
            remainingMemberCount = Math.max(remainingMemberCount, other.remainingMemberCount);
            entryPortalCount = Math.max(entryPortalCount, other.entryPortalCount);
            exitPortalCount = Math.max(exitPortalCount, other.exitPortalCount);
            portalPairs.addAll(other.portalPairs);
            dwells.addAll(other.dwells);
            dwellsConsidered = Math.max(dwellsConsidered, other.dwellsConsidered);
            memberSequencesConsidered += other.memberSequencesConsidered;
            packagesRetained = Math.max(packagesRetained, other.packagesRetained);
            packagesCalls += other.packagesCalls;
            computeCalls += other.computeCalls;
            cacheHits += other.cacheHits;
            totalMs += other.totalMs;
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (zoneId != null) {
                out.put("zoneId", zoneId.toString());
            }
            if (visitScopeId != null) {
                out.put("visitScopeId", visitScopeId.toString());
            }
            out.put("memberCount", memberCount);
            out.put("remainingMemberCount", remainingMemberCount);
            out.put("entryPortalCount", entryPortalCount);
            out.put("exitPortalCount", exitPortalCount);
            out.put("portalPairCount", portalPairs.size());
            out.put("dwellOptionsConsidered", Math.max(dwellsConsidered, dwells.size()));
            out.put("memberSequencesConsidered", memberSequencesConsidered);
            out.put("packagesRetained", packagesRetained);
            out.put("packagesCalls", packagesCalls);
            out.put("computeCalls", computeCalls);
            out.put("cacheHits", cacheHits);
            out.put("totalMs", totalMs);
            return out;
        }
    }

    private static final class ComputeAcc {
        private final String method;
        private final UUID zoneId;
        private final UUID visitScopeId;
        private final int memberCount;
        private final int remainingMemberCount;
        private final int entryPortalCount;
        private final int exitPortalCount;
        private final String entryPortalId;
        private final String exitPortalId;
        private final int requestedDwellMinutes;
        private final Instant arrival;
        private final int remainingMinutes;
        private long startedNs;
        private long totalNs;
        private boolean cacheHit;
        private int hops;
        private int waterPathHits;
        private int waterPathMisses;
        private int memberEvals;
        private int sequencesConsidered;
        private final Map<Stage, Long> stageNs = new LinkedHashMap<>();

        private ComputeAcc(
                String method,
                UUID zoneId,
                UUID visitScopeId,
                int memberCount,
                int remainingMemberCount,
                int entryPortalCount,
                int exitPortalCount,
                String entryPortalId,
                String exitPortalId,
                int requestedDwellMinutes,
                Instant arrival,
                int remainingMinutes
        ) {
            this.method = method;
            this.zoneId = zoneId;
            this.visitScopeId = visitScopeId;
            this.memberCount = memberCount;
            this.remainingMemberCount = remainingMemberCount;
            this.entryPortalCount = entryPortalCount;
            this.exitPortalCount = exitPortalCount;
            this.entryPortalId = entryPortalId;
            this.exitPortalId = exitPortalId;
            this.requestedDwellMinutes = requestedDwellMinutes;
            this.arrival = arrival;
            this.remainingMinutes = remainingMinutes;
        }

        private SlowCall toSlowCall() {
            Map<String, Double> breakdown = new LinkedHashMap<>();
            for (Map.Entry<Stage, Long> entry : stageNs.entrySet()) {
                breakdown.put(entry.getKey().jsonName(), entry.getValue() / 1_000_000.0);
            }
            return new SlowCall(
                    method,
                    zoneId,
                    visitScopeId,
                    memberCount,
                    remainingMemberCount,
                    entryPortalCount,
                    exitPortalCount,
                    entryPortalId,
                    exitPortalId,
                    requestedDwellMinutes,
                    remainingMinutes,
                    arrival,
                    cacheHit,
                    hops,
                    waterPathHits,
                    waterPathMisses,
                    memberEvals,
                    sequencesConsidered,
                    totalNs / 1_000_000.0,
                    breakdown
            );
        }
    }

    private record SlowCall(
            String method,
            UUID zoneId,
            UUID visitScopeId,
            int memberCount,
            int remainingMemberCount,
            int entryPortalCount,
            int exitPortalCount,
            String entryPortalId,
            String exitPortalId,
            int requestedDwellMinutes,
            int remainingMinutes,
            Instant arrival,
            boolean cacheHit,
            int hops,
            int waterPathHits,
            int waterPathMisses,
            int memberEvals,
            int sequencesConsidered,
            double totalMs,
            Map<String, Double> stagesMs
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("method", method);
            if (zoneId != null) {
                out.put("zoneId", zoneId.toString());
            }
            if (visitScopeId != null) {
                out.put("visitScopeId", visitScopeId.toString());
            }
            out.put("memberCount", memberCount);
            out.put("remainingMemberCount", remainingMemberCount);
            out.put("entryPortalCount", entryPortalCount);
            out.put("exitPortalCount", exitPortalCount);
            out.put("entryPortalId", entryPortalId);
            out.put("exitPortalId", exitPortalId);
            out.put("requestedDwellMinutes", requestedDwellMinutes);
            out.put("remainingMinutes", remainingMinutes);
            if (arrival != null) {
                out.put("arrival", arrival.toString());
            }
            out.put("cacheHit", cacheHit);
            out.put("hops", hops);
            out.put("waterPathHits", waterPathHits);
            out.put("waterPathMisses", waterPathMisses);
            out.put("memberEvals", memberEvals);
            out.put("sequencesConsidered", sequencesConsidered);
            out.put("totalMs", totalMs);
            out.put("stagesMs", new LinkedHashMap<>(stagesMs));
            return out;
        }
    }
}
