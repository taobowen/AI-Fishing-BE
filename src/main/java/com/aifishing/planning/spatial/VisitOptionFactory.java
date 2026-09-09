package com.aifishing.planning.spatial;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class VisitOptionFactory {

    public List<FishingVisitOption> options(List<RankedCandidate> ranked, PlanningProperties.Spatial spatial) {
        List<FishingVisitOption> options = new ArrayList<>();
        for (RankedCandidate candidate : ranked) {
            TargetKind kind = candidate.spot().getTargetKind();
            List<VisitPortal> portals = candidate.spot().getPortals();
            UUID identity = candidate.spot().planningIdentity();
            UUID scopeId = candidate.spot().getVisitScopeId();
            UUID targetOrZone = candidate.spot().getTargetKind() == TargetKind.ZONE
                    ? candidate.spot().getZoneId()
                    : candidate.spot().getFishingTargetId();
            if (targetOrZone == null) {
                targetOrZone = identity;
            }
            if (kind == TargetKind.POINT || portals.size() < 2) {
                VisitPortal portal = portals.isEmpty()
                        ? new VisitPortal("p", candidate.spot().getEntryPoint())
                        : portals.get(0);
                options.add(option(candidate, portal, portal, targetOrZone, scopeId, PathTraversal.FORWARD, null));
                continue;
            }
            if (kind == TargetKind.PATH || kind == TargetKind.SEGMENT) {
                VisitPortal a = portals.get(0);
                VisitPortal b = portals.get(Math.min(1, portals.size() - 1));
                if (candidate.spot().isClosedLoop()) {
                    options.add(option(candidate, a, a, targetOrZone, scopeId, PathTraversal.CLOCKWISE, null));
                    options.add(option(candidate, a, a, targetOrZone, scopeId, PathTraversal.COUNTER_CLOCKWISE, null));
                } else {
                    options.add(option(candidate, a, b, targetOrZone, scopeId, PathTraversal.FORWARD, null));
                    options.add(option(candidate, b, a, targetOrZone, scopeId, PathTraversal.REVERSE, null));
                }
                continue;
            }
            int pairs = 0;
            int cap = spatial.getMaxPortalPairsPerZone();
            for (int i = 0; i < portals.size() && pairs < cap; i++) {
                for (int j = 0; j < portals.size() && pairs < cap; j++) {
                    if (i == j && kind == TargetKind.ZONE) {
                        continue;
                    }
                    VisitPortal entry = portals.get(i);
                    VisitPortal exit = portals.get(j);
                    options.add(option(candidate, entry, exit, targetOrZone, scopeId, PathTraversal.FORWARD, null));
                    pairs++;
                }
            }
            if (pairs == 0) {
                VisitPortal portal = portals.get(0);
                options.add(option(candidate, portal, portal, targetOrZone, scopeId, PathTraversal.FORWARD, null));
            }
        }
        return options;
    }

    private static FishingVisitOption option(
            RankedCandidate candidate,
            VisitPortal entry,
            VisitPortal exit,
            UUID targetOrZone,
            UUID scopeId,
            PathTraversal traversal,
            String span
    ) {
        VisitOptionKey key = VisitOptionKey.of(targetOrZone, scopeId, entry, exit, traversal, span, 0, 0);
        return new FishingVisitOption(
                candidate,
                entry,
                exit,
                key.cacheKey(),
                key,
                traversal
        );
    }
}
