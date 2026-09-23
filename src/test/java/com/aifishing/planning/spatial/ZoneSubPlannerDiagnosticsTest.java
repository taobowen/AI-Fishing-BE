package com.aifishing.planning.spatial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneSubPlannerDiagnosticsTest {

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void unattachedProfilerDoesNotRecordZoneSubPlannerCalls() {
        UUID zoneId = UUID.randomUUID();
        GenerateProfiler.current().zoneSubPlanner().beginCompute(
                "computePackage", zoneId, UUID.randomUUID(), 4, 4, 2, 2, "in", "out", 90,
                Instant.parse("2026-09-21T12:00:00Z"), 400);
        GenerateProfiler.current().zoneSubPlanner().markCacheMiss();
        GenerateProfiler.current().zoneSubPlanner().endCompute();
        assertThat(GenerateProfiler.attached()).isFalse();

        GenerateProfiler profiler = GenerateProfiler.begin();
        assertThat(profiler.zoneSubPlanner().computeCalls()).isZero();
        assertThat(profiler.snapshot().get("zoneSubPlanner")).isNotNull();
    }

    @Test
    void equivalentCallsWithDifferentArrivalAreRepeatedSpatialWork() {
        GenerateProfiler profiler = GenerateProfiler.begin();
        ZoneSubPlannerDiagnostics diag = profiler.zoneSubPlanner();
        UUID zoneId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        Instant first = Instant.parse("2026-09-21T12:00:00Z");
        Instant second = Instant.parse("2026-09-21T12:15:00Z");
        diag.beginCompute("computePackage", zoneId, scopeId, 6, 6, 2, 2, "a", "b", 135, first, 400);
        diag.markCacheMiss();
        diag.addStageNs(ZoneSubPlannerDiagnostics.Stage.ASTAR, 12_000_000L);
        diag.endCompute();
        diag.beginCompute("computePackage", zoneId, scopeId, 6, 6, 2, 2, "a", "b", 135, second, 400);
        diag.markCacheMiss();
        diag.addStageNs(ZoneSubPlannerDiagnostics.Stage.ASTAR, 11_000_000L);
        diag.endCompute();

        Map<String, Object> map = diag.toMap();
        assertThat(map.get("computeCalls")).isEqualTo(2);
        assertThat(map.get("uniqueEquivalentCalls")).isEqualTo(1);
        assertThat(map.get("uniqueTimedCalls")).isEqualTo(2);
        assertThat(map.get("repeatedEquivalentCalls")).isEqualTo(1);
        assertThat((Double) map.get("repeatedEquivalentPct")).isEqualTo(50.0);
        Map<?, ?> deltas = (Map<?, ?>) map.get("inputDeltas");
        assertThat(deltas.get("equivalentKeysWithMultipleArrivals")).isEqualTo(1);
        assertThat(deltas.get("uniqueArrivalEpochSeconds")).isEqualTo(2);
        assertThat(map.get("dominantStage")).isEqualTo("astar");
        assertThat(map.get("snapshotClasses")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) map.get("snapshotClasses")).get("astar")).isEqualTo("STATIC_PER_SNAPSHOT");
    }

    @Test
    void cacheHitDoesNotCountAsMissAndAppearsInProfilerLog() {
        GenerateProfiler profiler = GenerateProfiler.begin();
        ZoneSubPlannerDiagnostics diag = profiler.zoneSubPlanner();
        UUID zoneId = UUID.randomUUID();
        Instant arrival = Instant.parse("2026-09-21T12:00:00Z");
        diag.beginCompute("computePackage", zoneId, UUID.randomUUID(), 3, 3, 1, 1, "p", "p", 45, arrival, 180);
        diag.markCacheMiss();
        diag.endCompute();
        diag.beginCompute("computePackage", zoneId, UUID.randomUUID(), 3, 3, 1, 1, "p", "p", 45, arrival, 180);
        diag.markCacheHit();
        diag.endCompute();

        assertThat(diag.cacheHits()).isEqualTo(1);
        assertThat(diag.computeCalls()).isEqualTo(2);
        Map<String, Object> log = profiler.generateLog(null, null, null, "GIS", null);
        assertThat(log).containsKey("zoneSubPlanner");
        @SuppressWarnings("unchecked")
        Map<String, Object> zoneSub = (Map<String, Object>) log.get("zoneSubPlanner");
        assertThat(zoneSub.get("cacheHits")).isEqualTo(1);
        assertThat(zoneSub.get("cacheMisses")).isEqualTo(1);
    }
}
