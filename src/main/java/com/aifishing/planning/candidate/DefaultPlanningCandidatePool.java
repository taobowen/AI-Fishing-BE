package com.aifishing.planning.candidate;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.domain.PlanningInputTargetSource;
import com.aifishing.planning.domain.TripPlanningInputSnapshot;
import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.PathTraversal;
import com.aifishing.planning.spatial.SpatialUtility;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitPortal;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mode-aware candidate pool from frozen planning-input targets + AI snapshot spots.
 * ZONE templates contribute intersecting snapshot POINT/PATH targets as TEMPLATE opportunities.
 */
@Component
public class DefaultPlanningCandidatePool implements PlanningCandidatePool {

    public static final String TEMPLATE_ZONE_EMPTY = "TEMPLATE_ZONE_EMPTY";

    private final PlanningInputTargetLoader inputLoader;
    private final PlanningOpportunityDeduper opportunityDeduper;

    public DefaultPlanningCandidatePool(
            PlanningInputTargetLoader inputLoader,
            PlanningOpportunityDeduper opportunityDeduper
    ) {
        this.inputLoader = inputLoader;
        this.opportunityDeduper = opportunityDeduper;
    }

    @Override
    public Result build(
            PlanningMode mode,
            List<CandidateSpot> aiSpots,
            PlanningContext context,
            UUID planningRunId
    ) {
        PlanningMode effective = PlanningMode.orAi(mode);
        List<String> warnings = new ArrayList<>();
        List<CandidateSpot> taggedAi = tagAi(aiSpots);
        FrozenInputs frozen = loadFrozen(planningRunId, context, warnings);
        List<CandidateSpot> union = new ArrayList<>();
        switch (effective) {
            case AI -> union.addAll(taggedAi);
            case HYBRID -> {
                union.addAll(taggedAi);
                union.addAll(frozen.templateSpots());
            }
            case CUSTOM -> union.addAll(frozen.templateSpots());
        }
        union.addAll(frozen.requiredSpots());
        List<CandidateSpot> deduped = opportunityDeduper.dedupe(union);
        List<CandidateSpot> required = new ArrayList<>();
        List<CandidateSpot> pool = new ArrayList<>();
        for (CandidateSpot spot : deduped) {
            if (spot.getCandidateSource() == CandidateSource.REQUIRED) {
                required.add(spot);
            } else if (effective == PlanningMode.AI && spot.getCandidateSource() != CandidateSource.AI) {
                // AI mode only keeps snapshot opportunities; required stay as constraints.
                continue;
            } else if (effective == PlanningMode.CUSTOM && spot.getCandidateSource() == CandidateSource.AI) {
                continue;
            } else {
                pool.add(spot);
            }
        }
        return new Result(pool, required, warnings);
    }

    private FrozenInputs loadFrozen(UUID planningRunId, PlanningContext context, List<String> warnings) {
        if (planningRunId == null) {
            return FrozenInputs.empty();
        }
        TripPlanningInputSnapshot snapshot = inputLoader.findSnapshot(planningRunId).orElse(null);
        if (snapshot == null) {
            return FrozenInputs.empty();
        }
        List<TripPlanningInputTarget> targets = inputLoader.findTargets(snapshot.getId());
        List<CandidateSpot> template = new ArrayList<>();
        List<CandidateSpot> required = new ArrayList<>();
        int index = 0;
        for (TripPlanningInputTarget target : targets) {
            if (target.getSource() == PlanningInputTargetSource.REQUIRED_POINT) {
                CandidateSpot spot = toRequiredPoint(target, context, index++);
                if (spot != null) {
                    required.add(spot);
                }
                continue;
            }
            if (target.getSource() != PlanningInputTargetSource.TEMPLATE) {
                continue;
            }
            if (target.getKind() == TemplateTargetKind.ZONE) {
                List<CandidateSpot> fromZone = zoneOpportunities(target, context, warnings, index);
                index += fromZone.size();
                template.addAll(fromZone);
            } else {
                CandidateSpot spot = toTemplateSpot(target, context, index++);
                if (spot != null) {
                    template.add(spot);
                }
            }
        }
        return new FrozenInputs(template, required);
    }

    private List<CandidateSpot> zoneOpportunities(
            TripPlanningInputTarget zoneTarget,
            PlanningContext context,
            List<String> warnings,
            int startIndex
    ) {
        Geometry zone = preferredWaterZone(zoneTarget.getGeometry(), context);
        if (zone == null || zone.isEmpty() || context.spatialSnapshot() == null) {
            if (!warnings.contains(TEMPLATE_ZONE_EMPTY)) {
                warnings.add(TEMPLATE_ZONE_EMPTY);
            }
            return List.of();
        }
        List<CandidateSpot> out = new ArrayList<>();
        int index = startIndex;
        for (CandidateSpot ai : tagAi(context.spatialSnapshot().targets().stream()
                .filter(row -> row.getTargetKind() != TargetKind.ZONE)
                .map(row -> snapshotRowToSpot(row, context))
                .toList())) {
            if (ai.getTargetKind() == TargetKind.ZONE) {
                continue;
            }
            Geometry geom = ai.getTargetGeometry() != null ? ai.getTargetGeometry() : ai.getLocation();
            if (geom == null || geom.isEmpty()) {
                continue;
            }
            try {
                if (!zone.intersects(geom)) {
                    continue;
                }
            } catch (RuntimeException ignored) {
                continue;
            }
            CandidateSpot copy = ai.copy();
            copy.setCandidateSource(CandidateSource.TEMPLATE);
            copy.addUnderlyingSource(CandidateSource.AI);
            copy.setStrategyWeight(Math.max(copy.getStrategyWeight(), 0.55));
            if (copy.getStrategyRationale() == null || copy.getStrategyRationale().isBlank()) {
                copy.setStrategyRationale(zoneTarget.getName() == null
                        ? "Template zone opportunity"
                        : "Template zone: " + zoneTarget.getName());
            }
            if (copy.planningIdentity() == null) {
                copy.setFishingTargetId(UUID.nameUUIDFromBytes(
                        ("tmpl-zone-" + zoneTarget.getId() + "-" + index).getBytes()));
            }
            stampStrategy(copy, context);
            out.add(copy);
            index++;
        }
        if (out.isEmpty() && !warnings.contains(TEMPLATE_ZONE_EMPTY)) {
            warnings.add(TEMPLATE_ZONE_EMPTY);
        }
        return out;
    }

    /**
     * Preferred fishing area = frozen ZONE ∩ usable lake water (water clip already applied
     * at template write; re-intersect at plan time for safety).
     */
    private static Geometry preferredWaterZone(Geometry zoneGeometry, PlanningContext context) {
        if (zoneGeometry == null || zoneGeometry.isEmpty()) {
            return null;
        }
        Geometry zone = zoneGeometry;
        if (context != null && context.geometry() != null && context.geometry().hasWater()) {
            try {
                Geometry clipped = context.geometry().water().intersection(zoneGeometry);
                if (clipped == null || clipped.isEmpty()) {
                    return null;
                }
                clipped.setSRID(4326);
                zone = clipped;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        zone.setSRID(4326);
        return zone;
    }

    private CandidateSpot snapshotRowToSpot(
            com.aifishing.planning.spatial.domain.LakeFishingTarget row,
            PlanningContext context
    ) {
        CandidateSpot spot = new CandidateSpot();
        UUID source = row.getSourceFeatureIds() == null || row.getSourceFeatureIds().isEmpty()
                ? null
                : row.getSourceFeatureIds().get(0);
        spot.setFeatureId(source);
        spot.setFishingTargetId(row.getId());
        spot.setCoverageIds(List.of(row.getId()));
        spot.setType(row.getSemanticType() == null ? FeatureType.POINT : row.getSemanticType());
        spot.setSourceGeometry(row.getGeometry());
        spot.setTargetGeometry(row.getGeometry());
        spot.setLocation(row.getRepresentativePoint());
        spot.setEntryPoint(row.getEntryPoint() == null ? row.getRepresentativePoint() : row.getEntryPoint());
        spot.setExitPoint(row.getExitPoint() == null ? spot.getEntryPoint() : row.getExitPoint());
        spot.setFishingCorridor(row.getFishingCorridor());
        spot.setSelectedFishingPath(row.getSelectedFishingPath());
        TargetKind kind = row.getTargetKind() == TargetKind.SEGMENT || row.getTargetKind() == TargetKind.AREA
                ? TargetKind.PATH
                : row.getTargetKind();
        spot.setTargetKind(kind == null || kind == TargetKind.ZONE ? TargetKind.POINT : kind);
        spot.setClosedLoop(row.isClosedLoop());
        spot.setCandidateSource(CandidateSource.AI);
        stampStrategy(spot, context);
        return spot;
    }

    private CandidateSpot toTemplateSpot(
            TripPlanningInputTarget target,
            PlanningContext context,
            int index
    ) {
        if (target.getKind() == TemplateTargetKind.POINT) {
            return pointSpot(
                    target.getGeometry(),
                    CandidateSource.TEMPLATE,
                    target.getName(),
                    context,
                    "tmpl-point-" + target.getId() + "-" + index,
                    0.55
            );
        }
        if (target.getKind() == TemplateTargetKind.PATH) {
            return pathSpot(
                    target.getGeometry(),
                    CandidateSource.TEMPLATE,
                    target.getName(),
                    context,
                    "tmpl-path-" + target.getId() + "-" + index,
                    0.55
            );
        }
        return null;
    }

    private CandidateSpot toRequiredPoint(
            TripPlanningInputTarget target,
            PlanningContext context,
            int index
    ) {
        return pointSpot(
                target.getGeometry(),
                CandidateSource.REQUIRED,
                target.getName(),
                context,
                "req-point-" + target.getId() + "-" + index,
                0.75
        );
    }

    private CandidateSpot pointSpot(
            Geometry geometry,
            CandidateSource source,
            String name,
            PlanningContext context,
            String identityKey,
            double weight
    ) {
        Point point = asPoint(geometry);
        if (point == null) {
            return null;
        }
        double width = context.properties().getSpatial().getCorridorWidthDefaultM();
        CandidateSpot spot = new CandidateSpot();
        spot.setCandidateSource(source);
        spot.setType(FeatureType.POINT);
        spot.setFeatureId(UUID.nameUUIDFromBytes(identityKey.getBytes()));
        spot.setFishingTargetId(UUID.nameUUIDFromBytes(identityKey.getBytes()));
        spot.setCoverageIds(List.of(spot.getFishingTargetId()));
        spot.setSourceGeometry(point);
        spot.setTargetKind(TargetKind.POINT);
        spot.setTargetGeometry(point);
        spot.setLocation(point);
        spot.setEntryPoint(point);
        spot.setExitPoint(point);
        spot.setFishingCorridorWidthM(width);
        spot.setPortals(List.of(new VisitPortal("p0", point)));
        spot.setClosedLoop(false);
        spot.setStrategyWeight(weight);
        spot.setFeatureConfidence(0.9);
        spot.setStrategyRationale(name == null || name.isBlank()
                ? source.name() + " point"
                : name);
        spot.setStaticSamples(List.of(new SpatialUtility.Sample(point, 0)));
        stampStrategy(spot, context);
        return spot;
    }

    private CandidateSpot pathSpot(
            Geometry geometry,
            CandidateSource source,
            String name,
            PlanningContext context,
            String identityKey,
            double weight
    ) {
        LineString line = asLine(geometry);
        if (line == null || line.getNumPoints() < 2) {
            return null;
        }
        double width = context.properties().getSpatial().getCorridorWidthDefaultM();
        Point entry = line.getPointN(0);
        Point exit = line.getPointN(line.getNumPoints() - 1);
        entry.setSRID(4326);
        exit.setSRID(4326);
        Point mid = line.getCentroid();
        mid.setSRID(4326);
        CandidateSpot spot = new CandidateSpot();
        spot.setCandidateSource(source);
        spot.setType(FeatureType.DROP_OFF);
        spot.setFeatureId(UUID.nameUUIDFromBytes(identityKey.getBytes()));
        spot.setFishingTargetId(UUID.nameUUIDFromBytes(identityKey.getBytes()));
        spot.setCoverageIds(List.of(spot.getFishingTargetId()));
        spot.setSourceGeometry(line);
        spot.setTargetKind(TargetKind.PATH);
        spot.setTargetGeometry(line);
        spot.setSelectedFishingPath(line);
        spot.setLocation(context.geometry().validFishingPoint(mid) ? mid : entry);
        spot.setEntryPoint(entry);
        spot.setExitPoint(exit);
        spot.setFishingCorridorWidthM(width);
        spot.setPortals(List.of(new VisitPortal("a", entry), new VisitPortal("b", exit)));
        spot.setClosedLoop(GeoMetrics.distanceM(entry, exit) <= 12 && GeoMetrics.lengthM(line) > 40);
        spot.setPathTopology(spot.isClosedLoop() ? "CLOSED" : "OPEN");
        spot.setTraversal(PathTraversal.FORWARD);
        spot.setStrategyWeight(weight);
        spot.setFeatureConfidence(0.9);
        spot.setStrategyRationale(name == null || name.isBlank()
                ? source.name() + " path"
                : name);
        stampStrategy(spot, context);
        return spot;
    }

    private static void stampStrategy(CandidateSpot spot, PlanningContext context) {
        if (context == null || context.strategyRun() == null) {
            return;
        }
        Pipeline pipeline = context.strategyRun().getFeaturePipeline();
        if (pipeline != null) {
            spot.setPipeline(pipeline);
        }
        String version = context.strategyRun().getFeatureAnalysisVersion();
        if (version != null) {
            spot.setAnalysisVersion(version);
        }
    }

    private static List<CandidateSpot> tagAi(List<CandidateSpot> aiSpots) {
        if (aiSpots == null || aiSpots.isEmpty()) {
            return List.of();
        }
        List<CandidateSpot> out = new ArrayList<>(aiSpots.size());
        for (CandidateSpot spot : aiSpots) {
            CandidateSpot copy = spot.copy();
            if (copy.getCandidateSource() == null || copy.getCandidateSource() == CandidateSource.AI) {
                copy.setCandidateSource(CandidateSource.AI);
            }
            out.add(copy);
        }
        return out;
    }

    private static Point asPoint(Geometry geometry) {
        if (geometry instanceof Point point && !point.isEmpty()) {
            point.setSRID(4326);
            return point;
        }
        if (geometry != null && !geometry.isEmpty()) {
            Point centroid = geometry.getCentroid();
            centroid.setSRID(4326);
            return centroid;
        }
        return null;
    }

    private static LineString asLine(Geometry geometry) {
        if (geometry instanceof LineString line && !(geometry instanceof MultiLineString) && line.getNumPoints() >= 2) {
            line.setSRID(4326);
            return line;
        }
        if (geometry instanceof MultiLineString multi && multi.getNumGeometries() == 1) {
            LineString line = (LineString) multi.getGeometryN(0);
            if (line.getNumPoints() >= 2) {
                line.setSRID(4326);
                return line;
            }
        }
        return null;
    }

    private record FrozenInputs(List<CandidateSpot> templateSpots, List<CandidateSpot> requiredSpots) {
        static FrozenInputs empty() {
            return new FrozenInputs(List.of(), List.of());
        }

        FrozenInputs {
            templateSpots = templateSpots == null ? List.of() : List.copyOf(templateSpots);
            requiredSpots = requiredSpots == null ? List.of() : List.copyOf(requiredSpots);
        }
    }
}
