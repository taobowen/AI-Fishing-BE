package com.aifishing.planning.spatial;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SpatialPlanningFacade {

    private final FishingTargetBuilder targetBuilder;
    private final FishingZoneBuilder zoneBuilder;
    private final ZoneVisitScopeDeriver scopeDeriver;

    public SpatialPlanningFacade(
            FishingTargetBuilder targetBuilder,
            FishingZoneBuilder zoneBuilder,
            ZoneVisitScopeDeriver scopeDeriver
    ) {
        this.targetBuilder = targetBuilder;
        this.zoneBuilder = zoneBuilder;
        this.scopeDeriver = scopeDeriver;
    }

    public List<CandidateSpot> enrich(List<CandidateSpot> spots, PlanningContext context) {
        if (context.spatialSnapshot() != null) {
            return spots;
        }
        return targetBuilder.enrich(spots, context.geometry(), context.properties());
    }

    public List<RankedCandidate> attachZones(List<RankedCandidate> ranked, PlanningContext context) {
        if (ranked == null || ranked.isEmpty()) {
            return ranked == null ? List.of() : ranked;
        }
        GenerateProfiler.current().start(GenerateProfiler.OPERATIONAL_VISIT_BUILD);
        boolean alreadyZoned = ranked.stream().anyMatch(item -> item.spot().getTargetKind() == TargetKind.ZONE);
        List<CandidateSpot> zoneVisits;
        Map<UUID, RankedCandidate> byTargetId = new HashMap<>();
        List<CandidateSpot> atomics = new ArrayList<>();
        for (RankedCandidate candidate : ranked) {
            ZoneScoreAggregation.index(byTargetId, candidate);
            if (candidate.spot().getTargetKind() != TargetKind.ZONE) {
                atomics.add(candidate.spot());
            }
        }
        if (alreadyZoned) {
            zoneVisits = new ArrayList<>();
            for (RankedCandidate candidate : ranked) {
                if (candidate.spot().getTargetKind() == TargetKind.ZONE) {
                    zoneVisits.addAll(scopeDeriver.derive(candidate.spot(), context));
                }
            }
            GenerateProfiler.current().set("physicalZonesConsidered",
                    ranked.stream().filter(item -> item.spot().getTargetKind() == TargetKind.ZONE).count());
        } else if (context.spatialSnapshot() != null) {
            zoneVisits = scopesFromSnapshot(context, byTargetId);
        } else {
            List<CandidateSpot> clustered = zoneBuilder.cluster(atomics, context.geometry(), context.properties());
            GenerateProfiler.current().set("physicalZonesConsidered", clustered.size());
            zoneVisits = new ArrayList<>();
            for (CandidateSpot zone : clustered) {
                zoneVisits.addAll(scopeDeriver.derive(zone, context));
            }
        }
        List<RankedCandidate> out = new ArrayList<>();
        Set<UUID> zoneMemberIds = new HashSet<>();
        for (CandidateSpot zone : zoneVisits) {
            RankedCandidate aggregated = ZoneScoreAggregation.aggregate(zone, byTargetId);
            if (aggregated == null) {
                continue;
            }
            zone.getZoneMembers().forEach(member -> {
                if (member.getFishingTargetId() != null) {
                    zoneMemberIds.add(member.getFishingTargetId());
                }
                if (member.getFeatureId() != null) {
                    zoneMemberIds.add(member.getFeatureId());
                }
            });
            out.add(aggregated);
        }
        for (RankedCandidate candidate : ranked) {
            if (candidate.spot().getTargetKind() == TargetKind.ZONE) {
                continue;
            }
            UUID targetId = candidate.spot().getFishingTargetId();
            UUID featureId = candidate.spot().getFeatureId();
            if ((targetId != null && zoneMemberIds.contains(targetId))
                    || (featureId != null && zoneMemberIds.contains(featureId))) {
                continue;
            }
            out.add(candidate);
        }
        GenerateProfiler.current().end(GenerateProfiler.OPERATIONAL_VISIT_BUILD);
        GenerateProfiler.current().count("macroCandidateCount", out.size());
        return out;
    }

    private List<CandidateSpot> scopesFromSnapshot(
            PlanningContext context,
            Map<UUID, RankedCandidate> byTargetId
    ) {
        SpatialSnapshotView view = context.spatialSnapshot();
        List<CandidateSpot> scopes = new ArrayList<>();
        int considered = 0;
        for (LakeFishingZone zone : view.zones()) {
            List<LakeFishingZoneMember> members = view.membersByZone().getOrDefault(zone.getId(), List.of());
            List<CandidateSpot> memberSpots = new ArrayList<>();
            for (LakeFishingZoneMember member : members) {
                RankedCandidate ranked = byTargetId.get(member.getFishingTargetId());
                if (ranked != null) {
                    memberSpots.add(ranked.spot());
                }
            }
            if (memberSpots.size() < context.properties().getSpatial().getClusterMinMembers()) {
                continue;
            }
            List<VisitPortal> portals = view.portalsByZone().getOrDefault(zone.getId(), List.of());
            if (!ZoneReachability.uncertainGeometry(portals)
                    && ZoneReachability.clearlyUnreachable(zone.getRepresentativePoint(), context)) {
                continue;
            }
            considered++;
            CandidateSpot physical = new CandidateSpot();
            physical.setFeatureId(zone.getId());
            physical.setZoneId(zone.getId());
            physical.setTargetKind(TargetKind.ZONE);
            physical.setTargetGeometry(zone.getGeometry());
            physical.setFishingCorridor(zone.getGeometry());
            physical.setVisitEnvelope(zone.getGeometry());
            physical.setLocation(zone.getRepresentativePoint());
            physical.setZoneMembers(memberSpots);
            physical.setPipeline(zone.getFeaturePipeline());
            physical.setAnalysisVersion(zone.getFeatureAnalysisVersion());
            physical.setPortals(view.portalsByZone().getOrDefault(zone.getId(), List.of()));
            if (!physical.getPortals().isEmpty()) {
                physical.setEntryPoint(physical.getPortals().get(0).point());
                physical.setExitPoint(physical.getPortals().get(physical.getPortals().size() - 1).point());
            }
            physical.setStrategyWeight(memberSpots.stream().mapToDouble(CandidateSpot::getStrategyWeight).average().orElse(0.5));
            List<com.aifishing.strategy.domain.TechniquePreference> techniques = new ArrayList<>();
            for (CandidateSpot member : memberSpots) {
                techniques.addAll(member.getTechniques());
            }
            physical.setTechniques(techniques);
            scopes.addAll(scopeDeriver.derive(physical, context));
        }
        GenerateProfiler.current().set("physicalZonesConsidered", considered);
        return scopes;
    }
}
