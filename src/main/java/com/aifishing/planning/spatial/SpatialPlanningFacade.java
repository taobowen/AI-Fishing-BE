package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.SpotScore;
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
        Map<UUID, RankedCandidate> byTargetId = new HashMap<>();
        List<CandidateSpot> atomics = new ArrayList<>();
        for (RankedCandidate candidate : ranked) {
            UUID id = candidate.spot().getFishingTargetId();
            if (id != null) {
                byTargetId.put(id, candidate);
            }
            atomics.add(candidate.spot());
        }
        List<CandidateSpot> zoneVisits;
        if (context.spatialSnapshot() != null) {
            zoneVisits = scopesFromSnapshot(context, byTargetId);
        } else {
            List<CandidateSpot> clustered = zoneBuilder.cluster(atomics, context.geometry(), context.properties());
            zoneVisits = new ArrayList<>();
            for (CandidateSpot zone : clustered) {
                zoneVisits.addAll(scopeDeriver.derive(zone, context));
            }
        }
        List<RankedCandidate> out = new ArrayList<>();
        Set<UUID> zoneMemberIds = new HashSet<>();
        for (CandidateSpot zone : zoneVisits) {
            zone.getZoneMembers().forEach(member -> {
                if (member.getFishingTargetId() != null) {
                    zoneMemberIds.add(member.getFishingTargetId());
                }
            });
            double score = zone.getZoneMembers().stream()
                    .map(member -> byTargetId.get(member.getFishingTargetId()))
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(item -> item.score().finalScore())
                    .average()
                    .orElse(zone.getStrategyWeight());
            out.add(new RankedCandidate(zone, new SpotScore(score, ranked.get(0).score().breakdown()), null));
        }
        for (RankedCandidate candidate : ranked) {
            UUID id = candidate.spot().getFishingTargetId();
            if (id != null && zoneMemberIds.contains(id)) {
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
            if (zoneBeyondBoatReach(zone, context)) {
                continue;
            }
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
            scopes.addAll(scopeDeriver.derive(physical, context));
        }
        return scopes;
    }

    private static boolean zoneBeyondBoatReach(LakeFishingZone zone, PlanningContext context) {
        if (context.routeStartPoint() == null || zone.getRepresentativePoint() == null) {
            return false;
        }
        double capKm = BoatCapabilityFilter.travelCapKm(context);
        if (!Double.isFinite(capKm)) {
            return false;
        }
        return GeoMetrics.distanceM(context.routeStartPoint(), zone.getRepresentativePoint()) / 1000.0 > capKm;
    }
}
