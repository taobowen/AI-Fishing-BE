package com.aifishing.planning.candidate;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.SpotRankingService;
import com.aifishing.planning.route.RegionalLaunchSummary;
import com.aifishing.planning.search.SearchParameterResolver;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.SpatialSnapshotView;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.planning.spatial.ZoneReachability;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class MacroCandidateShortlist {

    private final SpotRankingService rankingService;

    public MacroCandidateShortlist(SpotRankingService rankingService) {
        this.rankingService = rankingService;
    }

    public List<CandidateSpot> attachSnapshotMembership(List<CandidateSpot> spots, PlanningContext context) {
        if (spots == null || spots.isEmpty() || context == null || context.spatialSnapshot() == null) {
            return spots == null ? List.of() : spots;
        }
        Map<UUID, UUID> targetToZone = new HashMap<>();
        context.spatialSnapshot().membersByZone().forEach((zoneId, members) -> {
            for (LakeFishingZoneMember member : members) {
                if (member.getFishingTargetId() != null) {
                    targetToZone.putIfAbsent(member.getFishingTargetId(), zoneId);
                }
            }
        });
        List<CandidateSpot> out = new ArrayList<>(spots.size());
        for (CandidateSpot spot : spots) {
            UUID zoneId = spot.getZoneId() != null ? spot.getZoneId() : targetToZone.get(spot.getFishingTargetId());
            if (zoneId != null && spot.getZoneId() == null) {
                CandidateSpot tagged = spot.copy();
                tagged.setZoneId(zoneId);
                out.add(tagged);
            } else {
                out.add(spot);
            }
        }
        return out;
    }

    public List<CandidateSpot> select(List<CandidateSpot> accepted, PlanningContext context) {
        return selectWorld(accepted, context).beamSpots();
    }

    public MacroCandidateWorld selectWorld(List<CandidateSpot> accepted, PlanningContext context) {
        PlanningProperties.Candidates limits = context.properties().getCandidates();
        int visitCap = SearchParameterResolver.macroVisitOptionCap(context);
        int maxZones = Math.max(1, limits.getMaxMacroZones());
        int maxAtomics = Math.max(1, limits.getMaxUnassignedAtomics());
        int perType = limits.getMaxPerRegionFeatureType();
        double cellM = limits.getRegionCellSizeM();
        GenerateProfiler.current().compression().setMacroVisitOptionCap(visitCap);

        Map<UUID, CandidateSpot> byId = new HashMap<>();
        for (CandidateSpot spot : accepted) {
            if (spot.getFishingTargetId() != null) {
                byId.put(spot.getFishingTargetId(), spot);
            }
        }
        Point origin = context.routeStartPoint();
        if (origin == null && !accepted.isEmpty()) {
            origin = accepted.get(0).getLocation();
        }

        SpatialSnapshotView view = context.spatialSnapshot();
        List<ZoneDraft> zoneDrafts = new ArrayList<>();
        Set<UUID> assigned = new HashSet<>();
        int minMembers = context.properties().getSpatial().getClusterMinMembers();
        if (view != null) {
            for (LakeFishingZone zone : view.zones()) {
                List<LakeFishingZoneMember> members = view.membersByZone().getOrDefault(zone.getId(), List.of());
                if (members.size() < minMembers) {
                    continue;
                }
                List<CandidateSpot> reachable = new ArrayList<>();
                for (LakeFishingZoneMember member : members) {
                    CandidateSpot spot = byId.get(member.getFishingTargetId());
                    if (spot != null) {
                        reachable.add(spot);
                        assigned.add(spot.getFishingTargetId());
                    }
                }
                if (reachable.isEmpty()) {
                    continue;
                }
                List<VisitPortal> portals = view.portalsByZone().getOrDefault(zone.getId(), List.of());
                if (!ZoneReachability.uncertainGeometry(portals)
                        && ZoneReachability.clearlyUnreachable(zone.getRepresentativePoint(), context)) {
                    continue;
                }
                List<CandidateSpot> representatives = typeCapped(reachable, perType);
                CandidateSpot physical = physicalZone(zone, reachable, portals);
                double score = RegionalLaunchSummary.score(
                        representatives.isEmpty() ? reachable : representatives,
                        origin,
                        context,
                        rankingService,
                        ignored -> com.aifishing.feedback.ranking.EmpiricalEvidence.none()
                );
                String region = regionKey(origin, zone.getRepresentativePoint(), cellM);
                zoneDrafts.add(new ZoneDraft(physical, representatives, reachable, score, region, zone.getId()));
            }
        }

        List<CandidateSpot> unassigned = new ArrayList<>();
        for (CandidateSpot spot : accepted) {
            UUID id = spot.getFishingTargetId();
            if (id == null || assigned.contains(id)) {
                continue;
            }
            if (ZoneReachability.clearlyUnreachable(spot.getLocation(), context)) {
                continue;
            }
            unassigned.add(spot);
        }
        GenerateProfiler.current().compression().setSelectedTargetsWithNoPhysicalZone(unassigned.size());

        List<ZoneDraft> keptZones = allocateZones(zoneDrafts, maxZones);
        int droppedZones = Math.max(0, zoneDrafts.size() - keptZones.size());

        Map<String, List<CandidateSpot>> buckets = new LinkedHashMap<>();
        for (CandidateSpot spot : unassigned) {
            buckets.computeIfAbsent(regionKey(origin, spot.getLocation(), cellM), ignored -> new ArrayList<>()).add(spot);
        }
        int typeDropped = 0;
        List<BucketDraft> bucketDrafts = new ArrayList<>();
        for (Map.Entry<String, List<CandidateSpot>> entry : buckets.entrySet()) {
            List<CandidateSpot> ranked = rankAtomics(entry.getValue(), context);
            List<CandidateSpot> capped = typeCapped(ranked, perType);
            typeDropped += Math.max(0, ranked.size() - capped.size());
            bucketDrafts.add(new BucketDraft(entry.getKey(), capped));
        }
        bucketDrafts.sort(Comparator.comparing(BucketDraft::regionKey));
        List<CandidateSpot> keptAtomics = allocateAtomics(bucketDrafts, maxAtomics);
        int droppedAtomics = Math.max(0, unassigned.size() - keptAtomics.size() - typeDropped);

        GenerateProfiler.current().compression().add(CandidateCompressionReason.FEATURE_TYPE_BUDGET, typeDropped);
        GenerateProfiler.current().compression().add(
                CandidateCompressionReason.REGIONAL_CANDIDATE_BUDGET, droppedAtomics + droppedZones);
        GenerateProfiler.current().compression().setZonesSelected(keptZones.size());
        GenerateProfiler.current().compression().setAtomicsSelected(keptAtomics.size());

        Map<String, Integer> byRegion = new LinkedHashMap<>();
        List<MacroCandidateWorld.PhysicalZoneOption> zoneOptions = new ArrayList<>();
        List<CandidateSpot> beam = new ArrayList<>();
        for (ZoneDraft draft : keptZones) {
            byRegion.merge(draft.regionKey, 1, Integer::sum);
            zoneOptions.add(new MacroCandidateWorld.PhysicalZoneOption(
                    draft.zoneId, draft.physical, draft.representatives, draft.reachable, draft.regionKey));
            beam.add(draft.physical);
        }
        Map<String, List<CandidateSpot>> keptByBucket = new LinkedHashMap<>();
        Set<UUID> keptAtomicIds = new HashSet<>();
        for (CandidateSpot spot : keptAtomics) {
            keptAtomicIds.add(spot.getFishingTargetId());
        }
        for (BucketDraft bucket : bucketDrafts) {
            List<CandidateSpot> selected = new ArrayList<>();
            for (CandidateSpot spot : bucket.atomics) {
                if (keptAtomicIds.contains(spot.getFishingTargetId())) {
                    selected.add(spot);
                }
            }
            if (!selected.isEmpty()) {
                keptByBucket.put(bucket.regionKey, selected);
                byRegion.merge(bucket.regionKey, selected.size(), Integer::sum);
            }
        }
        List<MacroCandidateWorld.UnassignedBucket> unassignedOptions = new ArrayList<>();
        for (Map.Entry<String, List<CandidateSpot>> entry : keptByBucket.entrySet()) {
            unassignedOptions.add(new MacroCandidateWorld.UnassignedBucket(entry.getKey(), entry.getValue()));
            beam.addAll(entry.getValue());
        }
        GenerateProfiler.current().compression().setMacroOptionsByRegion(byRegion);
        return new MacroCandidateWorld(zoneOptions, unassignedOptions, beam, byRegion);
    }

    private List<CandidateSpot> rankAtomics(List<CandidateSpot> spots, PlanningContext context) {
        List<ScoredAtomic> scored = new ArrayList<>();
        for (CandidateSpot spot : spots) {
            double intrinsic = rankingService.intrinsicFishingQuality(
                    spot, context, null, com.aifishing.feedback.ranking.EmpiricalEvidence.none());
            scored.add(new ScoredAtomic(spot, intrinsic));
        }
        scored.sort(Comparator.comparingDouble(ScoredAtomic::score).reversed()
                .thenComparing(item -> String.valueOf(item.spot.getFishingTargetId())));
        return scored.stream().map(item -> item.spot).toList();
    }

    private static List<CandidateSpot> typeCapped(List<CandidateSpot> ranked, int maxPerType) {
        if (maxPerType <= 0) {
            return List.copyOf(ranked);
        }
        Map<FeatureType, Integer> counts = new HashMap<>();
        List<CandidateSpot> kept = new ArrayList<>();
        for (CandidateSpot spot : ranked) {
            FeatureType type = spot.getType();
            int used = counts.getOrDefault(type, 0);
            if (type != null && used >= maxPerType) {
                continue;
            }
            kept.add(spot);
            if (type != null) {
                counts.put(type, used + 1);
            }
        }
        return kept;
    }

    private static List<ZoneDraft> allocateZones(List<ZoneDraft> drafts, int cap) {
        if (drafts.isEmpty() || cap <= 0) {
            return List.of();
        }
        Map<String, List<ZoneDraft>> byRegion = new LinkedHashMap<>();
        List<ZoneDraft> sorted = new ArrayList<>(drafts);
        sorted.sort(Comparator.comparing(ZoneDraft::regionKey)
                .thenComparing(item -> String.valueOf(item.zoneId)));
        for (ZoneDraft draft : sorted) {
            byRegion.computeIfAbsent(draft.regionKey, ignored -> new ArrayList<>()).add(draft);
        }
        for (List<ZoneDraft> region : byRegion.values()) {
            region.sort(Comparator.comparingDouble(ZoneDraft::score).reversed()
                    .thenComparing(item -> String.valueOf(item.zoneId)));
        }
        List<String> keys = new ArrayList<>(byRegion.keySet());
        keys.sort(String::compareTo);
        List<ZoneDraft> kept = new ArrayList<>();
        int[] idx = new int[keys.size()];
        boolean progressed = true;
        while (kept.size() < cap && progressed) {
            progressed = false;
            for (int i = 0; i < keys.size() && kept.size() < cap; i++) {
                List<ZoneDraft> region = byRegion.get(keys.get(i));
                if (idx[i] < region.size()) {
                    kept.add(region.get(idx[i]++));
                    progressed = true;
                }
            }
        }
        return kept;
    }

    private static List<CandidateSpot> allocateAtomics(List<BucketDraft> buckets, int cap) {
        if (buckets.isEmpty() || cap <= 0) {
            return List.of();
        }
        List<CandidateSpot> kept = new ArrayList<>();
        int[] idx = new int[buckets.size()];
        boolean progressed = true;
        while (kept.size() < cap && progressed) {
            progressed = false;
            for (int i = 0; i < buckets.size() && kept.size() < cap; i++) {
                List<CandidateSpot> atomics = buckets.get(i).atomics;
                if (idx[i] < atomics.size()) {
                    kept.add(atomics.get(idx[i]++));
                    progressed = true;
                }
            }
        }
        return kept;
    }

    static String regionKey(Point origin, Point point, double cellM) {
        if (point == null) {
            return "unknown";
        }
        Point base = origin == null ? point : origin;
        double east = (point.getX() - base.getX()) * GeoMetrics.metersPerDegreeLng(base.getY());
        double north = (point.getY() - base.getY()) * GeoMetrics.metersPerDegreeLat();
        int e = (int) Math.floor(east / cellM);
        int n = (int) Math.floor(north / cellM);
        return "e" + e + ":n" + n;
    }

    private static CandidateSpot physicalZone(
            LakeFishingZone zone,
            List<CandidateSpot> reachableMembers,
            List<VisitPortal> portals
    ) {
        CandidateSpot physical = new CandidateSpot();
        physical.setFeatureId(zone.getId());
        physical.setZoneId(zone.getId());
        physical.setTargetKind(TargetKind.ZONE);
        physical.setTargetGeometry(zone.getGeometry());
        physical.setFishingCorridor(zone.getGeometry());
        physical.setVisitEnvelope(zone.getGeometry());
        physical.setLocation(zone.getRepresentativePoint());
        physical.setZoneMembers(reachableMembers);
        physical.setPipeline(zone.getFeaturePipeline());
        physical.setAnalysisVersion(zone.getFeatureAnalysisVersion());
        physical.setPortals(portals);
        if (!portals.isEmpty()) {
            physical.setEntryPoint(portals.get(0).point());
            physical.setExitPoint(portals.get(portals.size() - 1).point());
        } else {
            physical.setEntryPoint(zone.getRepresentativePoint());
            physical.setExitPoint(zone.getRepresentativePoint());
        }
        physical.setStrategyWeight(reachableMembers.stream().mapToDouble(CandidateSpot::getStrategyWeight).average().orElse(0.5));
        List<com.aifishing.strategy.domain.TechniquePreference> techniques = new ArrayList<>();
        for (CandidateSpot member : reachableMembers) {
            techniques.addAll(member.getTechniques());
        }
        physical.setTechniques(techniques);
        if (!reachableMembers.isEmpty()) {
            physical.setType(reachableMembers.get(0).getType());
        }
        return physical;
    }

    private record ZoneDraft(
            CandidateSpot physical,
            List<CandidateSpot> representatives,
            List<CandidateSpot> reachable,
            double score,
            String regionKey,
            UUID zoneId
    ) {
    }

    private record BucketDraft(String regionKey, List<CandidateSpot> atomics) {
    }

    private record ScoredAtomic(CandidateSpot spot, double score) {
    }
}
