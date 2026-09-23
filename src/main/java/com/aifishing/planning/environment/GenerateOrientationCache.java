package com.aifishing.planning.environment;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.spatial.ZoneVisitState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Request-scoped memoization of {@link LocalOrientation}. Keyed by fishing-target
 * identity only — never arrival, dwell, weather, portal, or Beam state.
 * Lifetime is one Generate Plan {@link com.aifishing.planning.service.PlanningContext}.
 */
public final class GenerateOrientationCache {

    private final Map<String, LocalOrientation> byTarget = new HashMap<>();

    public Lookup getOrResolve(
            UUID snapshotId,
            CandidateSpot spot,
            LakePlanningGeometry geometry,
            LocalOrientationService service
    ) {
        UUID targetId = ZoneVisitState.memberId(spot);
        if (targetId == null) {
            return new Lookup(service.resolve(spot, geometry), false);
        }
        String key = key(snapshotId, targetId);
        LocalOrientation cached = byTarget.get(key);
        if (cached != null) {
            return new Lookup(cached, true);
        }
        LocalOrientation resolved = service.resolve(spot, geometry);
        byTarget.put(key, resolved);
        return new Lookup(resolved, false);
    }

    public boolean contains(UUID snapshotId, UUID targetId) {
        return targetId != null && byTarget.containsKey(key(snapshotId, targetId));
    }

    public int size() {
        return byTarget.size();
    }

    static String key(UUID snapshotId, UUID targetId) {
        if (snapshotId == null) {
            return targetId.toString();
        }
        return snapshotId + ":" + targetId;
    }

    public record Lookup(LocalOrientation orientation, boolean hit) {
    }
}
