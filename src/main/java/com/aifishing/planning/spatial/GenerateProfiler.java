package com.aifishing.planning.spatial;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-local Generate Plan timings and counters. Stages match Phase 8.9.1.
 */
public final class GenerateProfiler {

    public static final String STATIC_SPATIAL_LOAD = "STATIC_SPATIAL_LOAD";
    public static final String TARGET_BUILD = "TARGET_BUILD";
    public static final String TARGET_SPLIT = "TARGET_SPLIT";
    public static final String STATIC_SAMPLE_BUILD = "STATIC_SAMPLE_BUILD";
    public static final String PHYSICAL_ZONE_BUILD = "PHYSICAL_ZONE_BUILD";
    public static final String PORTAL_BUILD = "PORTAL_BUILD";
    public static final String WATER_PATH_BUILD = "WATER_PATH_BUILD";
    public static final String OPERATIONAL_VISIT_BUILD = "OPERATIONAL_VISIT_BUILD";
    public static final String DYNAMIC_TIME_SCORING = "DYNAMIC_TIME_SCORING";
    public static final String ZONE_SUBPLAN = "ZONE_SUBPLAN";
    public static final String MACRO_ROUTE_SEARCH = "MACRO_ROUTE_SEARCH";
    public static final String PERSIST = "PERSIST";
    public static final String TOTAL = "TOTAL";

    private static final ThreadLocal<GenerateProfiler> CURRENT = new ThreadLocal<>();

    private final Map<String, Long> stageMillis = new LinkedHashMap<>();
    private final Map<String, Long> stageStarted = new LinkedHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();

    private GenerateProfiler() {
        for (String stage : new String[]{
                STATIC_SPATIAL_LOAD, TARGET_BUILD, TARGET_SPLIT, STATIC_SAMPLE_BUILD,
                PHYSICAL_ZONE_BUILD, PORTAL_BUILD, WATER_PATH_BUILD, OPERATIONAL_VISIT_BUILD,
                DYNAMIC_TIME_SCORING, ZONE_SUBPLAN, MACRO_ROUTE_SEARCH, PERSIST, TOTAL
        }) {
            stageMillis.put(stage, 0L);
        }
    }

    public static GenerateProfiler begin() {
        GenerateProfiler profiler = new GenerateProfiler();
        CURRENT.set(profiler);
        profiler.start(TOTAL);
        return profiler;
    }

    public static GenerateProfiler current() {
        GenerateProfiler profiler = CURRENT.get();
        return profiler == null ? new GenerateProfiler() : profiler;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public void start(String stage) {
        stageStarted.put(stage, System.nanoTime());
    }

    public void end(String stage) {
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
        counters.merge(name, delta, Long::sum);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stagesMs", new LinkedHashMap<>(stageMillis));
        out.put("counters", new LinkedHashMap<>(counters));
        return out;
    }

    public long stageMs(String stage) {
        return stageMillis.getOrDefault(stage, 0L);
    }

    public long counter(String name) {
        return counters.getOrDefault(name, 0L);
    }
}
