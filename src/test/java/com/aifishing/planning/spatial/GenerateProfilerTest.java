package com.aifishing.planning.spatial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateProfilerTest {

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void unattachedCurrentIsNoopAndDoesNotLeakCounts() {
        GenerateProfiler.current().count("astarExpandedCells", 10);
        assertThat(GenerateProfiler.attached()).isFalse();
        GenerateProfiler profiler = GenerateProfiler.begin();
        assertThat(profiler.counter("astarExpandedCells")).isZero();
    }

    @Test
    void detachAttachAndMergePreserveStrategyStages() {
        GenerateProfiler http = GenerateProfiler.begin();
        http.start(GenerateProfiler.STRATEGY_AI);
        http.end(GenerateProfiler.STRATEGY_AI);
        http.count("strategyAiCalls");
        GenerateProfiler detached = GenerateProfiler.detach();
        assertThat(GenerateProfiler.attached()).isFalse();
        assertThat(detached).isSameAs(http);

        GenerateProfiler worker = GenerateProfiler.begin();
        worker.mergeFrom(detached);
        worker.count("beamExpansions", 3);
        worker.end(GenerateProfiler.TOTAL_GENERATE);

        Map<String, Object> log = worker.generateLog(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "GIS", UUID.randomUUID());
        assertThat(log).containsKeys("strategyAiMs", "beamExpansions", "totalMs", "beamSearch", "zoneSubPlanner");
        assertThat((Long) log.get("beamExpansions")).isEqualTo(3L);
        assertThat(worker.counter("strategyAiCalls")).isEqualTo(1);
        assertThat(worker.generateLogJson(null, null, null, null, null)).contains("beamExpansions");
    }
}
