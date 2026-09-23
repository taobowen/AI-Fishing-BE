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
        return options(ranked, spatial, Integer.MAX_VALUE);
    }

    public List<FishingVisitOption> options(List<RankedCandidate> ranked, PlanningProperties.Spatial spatial, int totalCap) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }
        PlanningProperties.Spatial spec = spatial == null ? new PlanningProperties.Spatial() : spatial;
        List<List<FishingVisitOption>> expanded = new ArrayList<>();
        for (RankedCandidate candidate : ranked) {
            expanded.add(expand(candidate, spec, spec.getMaxPortalPairsPerZone()));
        }
        if (totalCap <= 0 || totalCap == Integer.MAX_VALUE) {
            List<FishingVisitOption> all = new ArrayList<>();
            expanded.forEach(all::addAll);
            return all;
        }
        int share = Math.max(1, totalCap / ranked.size());
        List<FishingVisitOption> out = new ArrayList<>();
        int[] taken = new int[expanded.size()];
        for (int i = 0; i < expanded.size(); i++) {
            int n = Math.min(share, expanded.get(i).size());
            if (n > 0) {
                out.addAll(expanded.get(i).subList(0, n));
            }
            taken[i] = n;
        }
        boolean progressed = true;
        while (out.size() < totalCap && progressed) {
            progressed = false;
            for (int i = 0; i < expanded.size() && out.size() < totalCap; i++) {
                if (taken[i] < expanded.get(i).size()) {
                    out.add(expanded.get(i).get(taken[i]++));
                    progressed = true;
                }
            }
        }
        return out;
    }

    private List<FishingVisitOption> expand(RankedCandidate candidate, PlanningProperties.Spatial spatial, int pairCap) {
        List<FishingVisitOption> options = new ArrayList<>();
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
        int cap = pairCap <= 0 ? Integer.MAX_VALUE : pairCap;
        if (kind == TargetKind.POINT || portals.size() < 2) {
            VisitPortal portal = portals.isEmpty()
                    ? new VisitPortal("p", candidate.spot().getEntryPoint())
                    : portals.get(0);
            options.add(option(candidate, portal, portal, targetOrZone, scopeId, PathTraversal.FORWARD, null));
            return options;
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
            return options.size() > cap ? new ArrayList<>(options.subList(0, cap)) : options;
        }
        int pairs = 0;
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
