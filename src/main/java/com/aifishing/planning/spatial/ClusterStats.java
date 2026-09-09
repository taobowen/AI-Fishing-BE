package com.aifishing.planning.spatial;

import java.util.LinkedHashMap;
import java.util.Map;

public record ClusterStats(
        long theoreticalPairCount,
        long neighborPairCount,
        long expensiveConnectivityChecks,
        long astarConnectivityChecks
) {
    public static ClusterStats empty() {
        return new ClusterStats(0, 0, 0, 0);
    }

    public Map<String, Object> toCounts() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("theoreticalPairCount", theoreticalPairCount);
        out.put("neighborPairCount", neighborPairCount);
        out.put("expensiveConnectivityChecks", expensiveConnectivityChecks);
        out.put("astarConnectivityChecks", astarConnectivityChecks);
        return out;
    }
}
