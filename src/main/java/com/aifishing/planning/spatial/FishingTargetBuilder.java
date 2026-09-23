package com.aifishing.planning.spatial;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.common.geo.PolygonalGeometries;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateLocationService;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.locationtech.jts.algorithm.InteriorPointArea;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FishingTargetBuilder {

    private final CandidateLocationService locationService;
    private final LocalMetricCrs localMetricCrs;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public FishingTargetBuilder(CandidateLocationService locationService, LocalMetricCrs localMetricCrs) {
        this.locationService = locationService;
        this.localMetricCrs = localMetricCrs;
    }

    public List<CandidateSpot> enrich(
            List<CandidateSpot> spots,
            LakePlanningGeometry lake,
            PlanningProperties properties
    ) {
        return enrich(spots, lake, properties, null, new SpatialSkipLedger());
    }

    public List<CandidateSpot> enrich(
            List<CandidateSpot> spots,
            LakePlanningGeometry lake,
            PlanningProperties properties,
            UUID snapshotId
    ) {
        return enrich(spots, lake, properties, snapshotId, new SpatialSkipLedger());
    }

    public List<CandidateSpot> enrich(
            List<CandidateSpot> spots,
            LakePlanningGeometry lake,
            PlanningProperties properties,
            UUID snapshotId,
            SpatialSkipLedger ledger
    ) {
        GenerateProfiler.current().start(GenerateProfiler.TARGET_BUILD);
        PlanningProperties.Spatial spatial = properties.getSpatial();
        SpatialSkipLedger skips = ledger == null ? new SpatialSkipLedger() : ledger;
        List<CandidateSpot> out = new ArrayList<>();
        for (CandidateSpot spot : spots) {
            for (CandidateSpot built : enrichOne(spot, lake, spatial, snapshotId)) {
                if (built.getLocation() == null || built.getLocation().isEmpty()) {
                    skips.skipTarget(built.getFeatureId() == null ? built.getFishingTargetId() : built.getFeatureId(),
                            "EMPTY_LOCATION");
                    continue;
                }
                out.add(built);
            }
        }
        out = mergeCoincident(out, lake);
        GenerateProfiler.current().end(GenerateProfiler.TARGET_BUILD);
        GenerateProfiler.current().count("generatedTargetCount", out.size());
        GenerateProfiler.current().count("pathCount", out.stream().filter(s -> s.getTargetKind().isPathLike()).count());
        return out;
    }

    private List<CandidateSpot> enrichOne(
            CandidateSpot spot,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            UUID snapshotId
    ) {
        Geometry source = spot.getSourceGeometry() == null ? spot.getLocation() : spot.getSourceGeometry();
        if (source == null || source.isEmpty()) {
            applyPoint(spot, spot.getLocation(), lake, spatial.getCorridorWidthDefaultM(), snapshotId, 0, "point");
            return List.of(spot);
        }
        FeatureType type = spot.getType();
        ensureEvidence(spot);
        if (type == FeatureType.ISLAND_EDGE) {
            return islandEdgePaths(spot, source, lake, spatial, snapshotId);
        }
        if (type == FeatureType.DROP_OFF || source.getDimension() == 1) {
            GenerateProfiler.current().start(GenerateProfiler.TARGET_SPLIT);
            List<LineString> parts = splitMeaningfully(asLines(source), spatial);
            GenerateProfiler.current().end(GenerateProfiler.TARGET_SPLIT);
            return splitPaths(spot, parts, lake, spatial, snapshotId, corridorWidth(type, spatial));
        }
        if (type == FeatureType.HUMP || type == FeatureType.BASIN) {
            applyRepresentativePoint(spot, source, lake, spatial.getCorridorWidthDefaultM(), snapshotId, "representative_point");
            return List.of(spot);
        }
        if (type == FeatureType.FLAT && source.getDimension() >= 2) {
            return flatTargets(spot, source, lake, spatial, snapshotId);
        }
        applyPoint(spot, spot.getLocation(), lake, spatial.getCorridorWidthDefaultM(), snapshotId, 0, "point");
        return List.of(spot);
    }

    private List<CandidateSpot> islandEdgePaths(
            CandidateSpot spot,
            Geometry source,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            UUID snapshotId
    ) {
        GenerateProfiler.current().start(GenerateProfiler.TARGET_SPLIT);
        List<LineString> parts = islandEdgeParts(source, spatial);
        GenerateProfiler.current().end(GenerateProfiler.TARGET_SPLIT);
        return splitPaths(spot, parts, lake, spatial, snapshotId, corridorWidth(FeatureType.ISLAND_EDGE, spatial));
    }

    private List<LineString> islandEdgeParts(Geometry source, PlanningProperties.Spatial spatial) {
        List<LineString> shores = new ArrayList<>();
        if (source instanceof org.locationtech.jts.geom.Polygon polygon) {
            shores.addAll(oppositeShores(polygon.getExteriorRing()));
        } else if (source instanceof org.locationtech.jts.geom.MultiPolygon multi) {
            for (int i = 0; i < multi.getNumGeometries(); i++) {
                if (multi.getGeometryN(i) instanceof org.locationtech.jts.geom.Polygon polygon) {
                    shores.addAll(oppositeShores(polygon.getExteriorRing()));
                }
            }
        } else {
            for (LineString line : asLines(source)) {
                shores.addAll(isClosedLoop(line) ? oppositeShores(line) : List.of(line));
            }
        }
        List<LineString> parts = new ArrayList<>();
        for (LineString shore : shores) {
            for (LineString turn : splitAtTurns(shore)) {
                for (LineString piece : splitLine(turn, spatial.getMaxSegmentLengthM())) {
                    if (GeoMetrics.lengthM(piece) >= 30) {
                        parts.add(piece);
                    }
                }
            }
        }
        return parts;
    }

    private List<LineString> oppositeShores(LineString ring) {
        if (ring == null || ring.getNumPoints() < 4) {
            return ring == null ? List.of() : List.of(ring);
        }
        int count = ring.getNumPoints();
        boolean closed = ring.getCoordinateN(0).equals2D(ring.getCoordinateN(count - 1));
        int unique = closed ? count - 1 : count;
        if (unique < 4) {
            return List.of(ring);
        }
        int bestI = 0;
        int bestJ = 1;
        double best = -1;
        for (int i = 0; i < unique; i++) {
            for (int j = i + 1; j < unique; j++) {
                double distance = GeoMetrics.distanceM(
                        point(ring.getCoordinateN(i)),
                        point(ring.getCoordinateN(j))
                );
                if (distance > best) {
                    best = distance;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        List<LineString> shores = new ArrayList<>();
        LineString forward = chain(ring, bestI, bestJ, unique);
        LineString backward = chain(ring, bestJ, bestI, unique);
        if (forward != null) {
            shores.add(forward);
        }
        if (backward != null) {
            shores.add(backward);
        }
        return shores.isEmpty() ? List.of(ring) : shores;
    }

    private LineString chain(LineString ring, int from, int to, int count) {
        List<Coordinate> coordinates = new ArrayList<>();
        int index = from;
        coordinates.add(ring.getCoordinateN(index));
        int guard = 0;
        while (index != to && guard <= count) {
            index = (index + 1) % count;
            coordinates.add(ring.getCoordinateN(index));
            guard++;
        }
        if (coordinates.size() < 2) {
            return null;
        }
        LineString line = factory.createLineString(coordinates.toArray(Coordinate[]::new));
        line.setSRID(GeoMapper.SRID);
        return line;
    }

    private List<CandidateSpot> flatTargets(
            CandidateSpot spot,
            Geometry source,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            UUID snapshotId
    ) {
        applyRepresentativePoint(spot, source, lake, spatial.getCorridorWidthDefaultM(), snapshotId, "flat_point");
        List<CandidateSpot> targets = new ArrayList<>();
        targets.add(spot);
        LineString edge = usefulFlatEdge(source, lake, spatial);
        if (edge == null) {
            return targets;
        }
        CandidateSpot path = copySpot(spot);
        applyPath(path, edge, lake, spatial.getCorridorWidthDefaultM(), snapshotId, 1, 0, GeoMetrics.lengthM(edge), "flat_edge");
        if (path.getTargetKind() == TargetKind.PATH) {
            targets.add(path);
        }
        return targets;
    }

    private LineString usefulFlatEdge(Geometry source, LakePlanningGeometry lake, PlanningProperties.Spatial spatial) {
        Geometry waterGeom = waterIntersect(source, lake);
        Geometry boundary = waterGeom == null || waterGeom.isEmpty() ? source.getBoundary() : waterGeom.getBoundary();
        LineString longest = longestLine(boundary);
        if (longest == null || isClosedLoop(longest)) {
            return null;
        }
        double length = GeoMetrics.lengthM(longest);
        if (length < 30 || length > spatial.getMaxSegmentLengthM()) {
            return null;
        }
        return longest;
    }

    private void applyRepresentativePoint(
            CandidateSpot spot,
            Geometry source,
            LakePlanningGeometry lake,
            double widthM,
            UUID snapshotId,
            String reason
    ) {
        Geometry waterGeom = waterIntersect(source, lake);
        Point interior = null;
        if (waterGeom != null && !waterGeom.isEmpty() && waterGeom.getDimension() >= 2) {
            interior = point(new InteriorPointArea(waterGeom).getInteriorPoint());
        }
        Point location = firstValid(interior, lake);
        if (location == null) {
            location = firstValid(spot.getLocation(), lake);
        }
        applyPoint(spot, location == null ? spot.getLocation() : location, lake, widthM, snapshotId, 0, reason);
    }

    private List<CandidateSpot> splitPaths(
            CandidateSpot spot,
            List<LineString> lines,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            UUID snapshotId,
            double widthM
    ) {
        if (lines.isEmpty()) {
            applyPoint(spot, spot.getLocation(), lake, widthM, snapshotId, 0, "point");
            return List.of(spot);
        }
        List<CandidateSpot> paths = new ArrayList<>();
        double chainage = 0;
        for (int i = 0; i < lines.size(); i++) {
            CandidateSpot copy = i == 0 ? spot : copySpot(spot);
            double length = GeoMetrics.lengthM(lines.get(i));
            String reason = lines.size() == 1 ? "whole" : (length >= spatial.getMaxSegmentLengthM() - 1 ? "max_length" : "orientation_change");
            applyPath(copy, lines.get(i), lake, widthM, snapshotId, i, chainage, chainage + length, reason);
            chainage += length;
            paths.add(copy);
        }
        return paths;
    }

    private static final double COINCIDENT_M = 5.0;
    private static final double SAME_DEPTH_M = 1.0;

    private List<CandidateSpot> mergeCoincident(List<CandidateSpot> spots, LakePlanningGeometry lake) {
        List<CandidateSpot> kept = new ArrayList<>();
        for (CandidateSpot spot : spots) {
            ensureEvidence(spot);
            CandidateSpot match = null;
            for (CandidateSpot existing : kept) {
                if (sameOpportunity(existing, spot, lake)) {
                    match = existing;
                    break;
                }
            }
            if (match == null) {
                kept.add(spot);
            } else {
                absorb(match, spot);
            }
        }
        return kept;
    }

    private boolean sameOpportunity(CandidateSpot left, CandidateSpot right, LakePlanningGeometry lake) {
        if (left.getRepresentativeDepthM() == null || right.getRepresentativeDepthM() == null) {
            return false;
        }
        if (Math.abs(left.getRepresentativeDepthM() - right.getRepresentativeDepthM()) > SAME_DEPTH_M) {
            return false;
        }
        if (!geometriesCoincide(left, right)) {
            return false;
        }
        Point from = left.getLocation();
        Point to = right.getLocation();
        if (from == null || to == null) {
            return false;
        }
        if (GeoMetrics.distanceM(from, to) <= 1) {
            return true;
        }
        return !lake.landCrossing(from, to);
    }

    private boolean geometriesCoincide(CandidateSpot left, CandidateSpot right) {
        Geometry a = left.getTargetGeometry();
        Geometry b = right.getTargetGeometry();
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) {
            return false;
        }
        if (a instanceof Point && b instanceof Point) {
            return GeoMetrics.distanceM(a, b) <= COINCIDENT_M;
        }
        if (a instanceof Point && b instanceof LineString) {
            return GeoMetrics.distanceM(a, b) <= COINCIDENT_M;
        }
        if (b instanceof Point && a instanceof LineString) {
            return GeoMetrics.distanceM(a, b) <= COINCIDENT_M;
        }
        if (a instanceof LineString && b instanceof LineString) {
            return pathsCoincide((LineString) a, (LineString) b);
        }
        return GeoMetrics.distanceM(a, b) <= COINCIDENT_M;
    }

    private boolean pathsCoincide(LineString left, LineString right) {
        double shorter = Math.min(GeoMetrics.lengthM(left), GeoMetrics.lengthM(right));
        if (shorter < 1) {
            return false;
        }
        try {
            Geometry buffered = left.buffer(GeoMetrics.bufferDegrees(COINCIDENT_M, GeoMetrics.referenceLat(left)));
            Geometry overlap = buffered.intersection(right);
            return overlap != null && GeoMetrics.lengthM(overlap) >= shorter * 0.8;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void absorb(CandidateSpot kept, CandidateSpot other) {
        kept.setSourceFeatureIds(unionIds(kept.getSourceFeatureIds(), other.getSourceFeatureIds()));
        kept.setEvidenceTypes(unionTypes(kept.getEvidenceTypes(), other.getEvidenceTypes()));
        List<UUID> coverage = new ArrayList<>(kept.coverageIds());
        for (UUID id : other.coverageIds()) {
            if (!coverage.contains(id)) {
                coverage.add(id);
            }
        }
        for (UUID id : kept.getSourceFeatureIds()) {
            if (!coverage.contains(id)) {
                coverage.add(id);
            }
        }
        kept.setCoverageIds(coverage);
        if (other.getMinDepthM() != null) {
            kept.setMinDepthM(kept.getMinDepthM() == null
                    ? other.getMinDepthM()
                    : Math.min(kept.getMinDepthM(), other.getMinDepthM()));
        }
        if (other.getMaxDepthM() != null) {
            kept.setMaxDepthM(kept.getMaxDepthM() == null
                    ? other.getMaxDepthM()
                    : Math.max(kept.getMaxDepthM(), other.getMaxDepthM()));
        }
    }

    private static List<UUID> unionIds(List<UUID> left, List<UUID> right) {
        List<UUID> ids = new ArrayList<>();
        if (left != null) {
            ids.addAll(left);
        }
        if (right != null) {
            for (UUID id : right) {
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static List<FeatureType> unionTypes(List<FeatureType> left, List<FeatureType> right) {
        List<FeatureType> types = new ArrayList<>();
        if (left != null) {
            types.addAll(left);
        }
        if (right != null) {
            for (FeatureType type : right) {
                if (type != null && !types.contains(type)) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    private static void ensureEvidence(CandidateSpot spot) {
        if ((spot.getSourceFeatureIds() == null || spot.getSourceFeatureIds().isEmpty()) && spot.getFeatureId() != null) {
            spot.setSourceFeatureIds(List.of(spot.getFeatureId()));
        }
        if ((spot.getEvidenceTypes() == null || spot.getEvidenceTypes().isEmpty()) && spot.getType() != null) {
            spot.setEvidenceTypes(List.of(spot.getType()));
        }
    }

    private void applyPoint(CandidateSpot spot, Point location, LakePlanningGeometry lake, double widthM) {
        applyPoint(spot, location, lake, widthM, null, 0, "point");
    }

    private void applyPoint(
            CandidateSpot spot,
            Point location,
            LakePlanningGeometry lake,
            double widthM,
            UUID snapshotId,
            int index,
            String splitReason
    ) {
        Point water = firstValid(location, lake);
        spot.setTargetKind(TargetKind.POINT);
        spot.setTargetGeometry(water);
        spot.setLocation(water);
        spot.setEntryPoint(water);
        spot.setExitPoint(water);
        spot.setFishingCorridorWidthM(widthM);
        spot.setPortals(List.of(new VisitPortal("p0", water)));
        spot.setClosedLoop(false);
        spot.setSplitReason(splitReason);
        spot.setChainageStartM(0);
        spot.setChainageEndM(0);
        if (water != null) {
            spot.setFishingCorridor(bufferWater(water, widthM / 2.0, lake));
            spot.setStaticSamples(List.of(new SpatialUtility.Sample(water, 0)));
        }
        assignIdentity(spot, snapshotId, index, TargetKind.POINT);
    }

    private void applyPath(
            CandidateSpot spot,
            LineString line,
            LakePlanningGeometry lake,
            double widthM,
            UUID snapshotId,
            int index,
            double chainageStart,
            double chainageEnd,
            String splitReason
    ) {
        Geometry corridor = waterSideCorridor(line, lake, widthM);
        Point entry = waterSideEnd(line, 0, lake, corridor);
        Point exit = waterSideEnd(line, line.getNumPoints() - 1, lake, corridor);
        if (entry == null || exit == null) {
            applyPoint(spot, spot.getLocation(), lake, widthM, snapshotId, index, "path_fallback_point");
            return;
        }
        boolean closed = GeoMetrics.distanceM(line.getStartPoint(), line.getEndPoint()) <= 12
                && GeoMetrics.lengthM(line) > 40;
        Point mid = firstValid(point(midCoordinate(line)), lake);
        spot.setTargetKind(TargetKind.PATH);
        spot.setTargetGeometry(line);
        spot.setFishingCorridor(corridor);
        spot.setLocation(mid != null ? mid : entry);
        spot.setEntryPoint(entry);
        spot.setExitPoint(exit);
        spot.setFishingCorridorWidthM(widthM);
        spot.setPortals(List.of(new VisitPortal("a", entry), new VisitPortal("b", exit)));
        spot.setSelectedFishingPath(fishingPath(entry, exit, corridor, lake));
        spot.setClosedLoop(closed);
        spot.setPathTopology(closed ? "CLOSED" : "OPEN");
        spot.setChainageStartM(chainageStart);
        spot.setChainageEndM(chainageEnd);
        spot.setSplitReason(splitReason);
        GenerateProfiler.current().start(GenerateProfiler.STATIC_SAMPLE_BUILD);
        spot.setStaticSamples(sampleAlong(spot.getSelectedFishingPath() == null ? line : spot.getSelectedFishingPath(), entry, exit, new PlanningProperties.Spatial()));
        GenerateProfiler.current().end(GenerateProfiler.STATIC_SAMPLE_BUILD);
        assignIdentity(spot, snapshotId, index, TargetKind.PATH);
    }

    private List<SpatialUtility.Sample> sampleAlong(Geometry path, Point from, Point to, PlanningProperties.Spatial spatial) {
        return new SpatialUtility(new com.aifishing.planning.environment.TimeAdjustedSpotUtility(
                new com.aifishing.planning.environment.SolarPositionService(),
                new com.aifishing.planning.environment.BoatWeatherPenalty()
        )).samples(path, from, to, spatial);
    }

    private static PlanningProperties.Spatial unusedSpatial() {
        return new PlanningProperties.Spatial();
    }

    private void assignIdentity(CandidateSpot spot, UUID snapshotId, int index, TargetKind kind) {
        String fingerprint = fingerprint(spot.getTargetGeometry(), index);
        UUID id = snapshotId == null
                ? UUID.randomUUID()
                : SpatialIds.targetId(snapshotId, spot.getFeatureId(), index, kind, fingerprint);
        spot.setFishingTargetId(id);
        List<UUID> coverage = new ArrayList<>();
        coverage.add(id);
        if (spot.getSourceFeatureIds() != null) {
            for (UUID sourceId : spot.getSourceFeatureIds()) {
                if (sourceId != null && !coverage.contains(sourceId)) {
                    coverage.add(sourceId);
                }
            }
        }
        spot.setCoverageIds(coverage);
    }

    private static String fingerprint(Geometry geometry, int index) {
        if (geometry == null || geometry.isEmpty()) {
            return "empty:" + index;
        }
        Coordinate c = geometry.getCoordinate();
        return index + ":" + Math.round(c.x * 1e6) + ":" + Math.round(c.y * 1e6) + ":" + geometry.getNumPoints();
    }

    Geometry waterSideCorridor(LineString line, LakePlanningGeometry lake, double widthM) {
        if (!lake.hasWater()) {
            return null;
        }
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(line, line.getCoordinate().x);
        Geometry metricLine = projected.toMetric(line);
        Geometry left = projected.toWgs84(metricLine.buffer(widthM, 8, org.locationtech.jts.operation.buffer.BufferParameters.CAP_FLAT));
        Geometry right = projected.toWgs84(metricLine.buffer(-widthM, 8, org.locationtech.jts.operation.buffer.BufferParameters.CAP_FLAT));
        Geometry leftWater = waterIntersect(left, lake);
        Geometry rightWater = waterIntersect(right, lake);
        Geometry chosen = larger(leftWater, rightWater);
        if (chosen == null || chosen.isEmpty()) {
            chosen = waterIntersect(projected.toWgs84(metricLine.buffer(widthM)), lake);
        }
        return GeometrySanitizer.validateFixAndNormalize(chosen, 5);
    }

    private Geometry waterIntersect(Geometry geometry, LakePlanningGeometry lake) {
        if (geometry == null || geometry.isEmpty() || !lake.hasWater()) {
            return null;
        }
        Geometry water;
        try {
            water = PolygonalGeometries.of(lake.water().intersection(geometry));
        } catch (RuntimeException ex) {
            return null;
        }
        for (Geometry island : lake.islands()) {
            if (island != null && !island.isEmpty() && water != null && !water.isEmpty()) {
                try {
                    water = PolygonalGeometries.of(water.difference(island));
                } catch (Exception ignored) {
                    // keep previous
                }
            }
        }
        return water;
    }

    private Point waterSideEnd(LineString line, int index, LakePlanningGeometry lake, Geometry corridor) {
        Coordinate coordinate = line.getCoordinateN(Math.max(0, Math.min(line.getNumPoints() - 1, index)));
        Point origin = point(coordinate);
        if (corridor != null && !corridor.isEmpty()) {
            Point onCorridor = corridor.getInteriorPoint();
            if (onCorridor instanceof Point p && lake.validFishingPoint(p)) {
                // nearest corridor coordinate
            }
            Coordinate[] coords = corridor.getCoordinates();
            Point best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (Coordinate candidate : coords) {
                Point p = point(candidate);
                if (!lake.validFishingPoint(p)) {
                    continue;
                }
                double d = GeoMetrics.distanceM(origin, p);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return firstValid(origin, lake);
    }

    private List<LineString> splitMeaningfully(List<LineString> lines, PlanningProperties.Spatial spatial) {
        List<LineString> parts = new ArrayList<>();
        for (LineString line : lines) {
            if (isClosedLoop(line) && GeoMetrics.lengthM(line) <= spatial.getMaxSegmentLengthM() * 2) {
                parts.add(line);
                continue;
            }
            for (LineString turn : splitAtTurns(line)) {
                parts.addAll(splitLine(turn, spatial.getMaxSegmentLengthM()));
            }
        }
        return parts;
    }

    private static boolean isClosedLoop(LineString line) {
        if (line == null || line.getNumPoints() < 4) {
            return false;
        }
        return GeoMetrics.distanceM(line.getStartPoint(), line.getEndPoint()) <= 12
                && GeoMetrics.lengthM(line) > 40;
    }

    private List<LineString> splitAtTurns(LineString line) {
        if (line == null || line.getNumPoints() < 3) {
            return line == null ? List.of() : List.of(line);
        }
        List<LineString> parts = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        current.add(line.getCoordinateN(0));
        current.add(line.getCoordinateN(1));
        for (int i = 2; i < line.getNumPoints(); i++) {
            Coordinate a = current.get(current.size() - 2);
            Coordinate b = current.get(current.size() - 1);
            Coordinate c = line.getCoordinateN(i);
            double heading1 = Math.atan2(b.y - a.y, b.x - a.x);
            double heading2 = Math.atan2(c.y - b.y, c.x - b.x);
            double delta = Math.abs(Math.toDegrees(heading2 - heading1));
            if (delta > 180) {
                delta = 360 - delta;
            }
            if (delta >= 50 && current.size() >= 2) {
                LineString part = factory.createLineString(current.toArray(Coordinate[]::new));
                part.setSRID(GeoMapper.SRID);
                parts.add(part);
                current = new ArrayList<>();
                current.add(b);
            }
            current.add(c);
        }
        if (current.size() >= 2) {
            LineString last = factory.createLineString(current.toArray(Coordinate[]::new));
            last.setSRID(GeoMapper.SRID);
            parts.add(last);
        }
        return parts.isEmpty() ? List.of(line) : parts;
    }

    private List<LineString> splitLines(List<LineString> lines, double maxLengthM) {
        List<LineString> parts = new ArrayList<>();
        for (LineString line : lines) {
            parts.addAll(splitLine(line, maxLengthM));
        }
        return parts;
    }

    private List<LineString> splitLine(LineString line, double maxLengthM) {
        if (line == null || line.getNumPoints() < 2) {
            return List.of();
        }
        double total = GeoMetrics.lengthM(line);
        if (total <= maxLengthM) {
            return List.of(line);
        }
        List<LineString> parts = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        current.add(line.getCoordinateN(0));
        double acc = 0;
        for (int i = 1; i < line.getNumPoints(); i++) {
            Coordinate prev = current.get(current.size() - 1);
            Coordinate next = line.getCoordinateN(i);
            double remaining = GeoMetrics.distanceM(prev, next, prev.y);
            while (acc + remaining > maxLengthM && remaining > 1) {
                double need = maxLengthM - acc;
                double t = need / remaining;
                Coordinate cut = new Coordinate(
                        prev.x + t * (next.x - prev.x),
                        prev.y + t * (next.y - prev.y)
                );
                current.add(cut);
                LineString part = factory.createLineString(current.toArray(Coordinate[]::new));
                part.setSRID(GeoMapper.SRID);
                parts.add(part);
                current = new ArrayList<>();
                current.add(cut);
                prev = cut;
                acc = 0;
                remaining = GeoMetrics.distanceM(prev, next, prev.y);
            }
            current.add(next);
            acc += remaining;
        }
        if (current.size() >= 2) {
            LineString last = factory.createLineString(current.toArray(Coordinate[]::new));
            last.setSRID(GeoMapper.SRID);
            parts.add(last);
        }
        return parts;
    }

    private List<LineString> asLines(Geometry geometry) {
        List<LineString> lines = new ArrayList<>();
        if (geometry instanceof LineString lineString) {
            lines.add(lineString);
            return lines;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof LineString lineString) {
                lines.add(lineString);
            } else if (part.getDimension() == 1) {
                LineString longest = longestLine(part);
                if (longest != null) {
                    lines.add(longest);
                }
            }
        }
        if (lines.isEmpty() && geometry.getDimension() >= 1) {
            LineString longest = longestLine(geometry);
            if (longest != null) {
                lines.add(longest);
            }
        }
        return lines;
    }

    private LineString fishingPath(Point entry, Point exit, Geometry corridor, LakePlanningGeometry lake) {
        if (entry == null || exit == null) {
            return null;
        }
        List<Coordinate> coords = new ArrayList<>();
        coords.add(entry.getCoordinate());
        if (corridor != null && !corridor.isEmpty()) {
            Point interior = corridor.getInteriorPoint();
            if (interior != null && lake.validFishingPoint(interior)
                    && GeoMetrics.distanceM(entry, interior) > 1
                    && GeoMetrics.distanceM(exit, interior) > 1) {
                coords.add(interior.getCoordinate());
            }
        }
        coords.add(exit.getCoordinate());
        if (coords.size() < 2) {
            return null;
        }
        LineString path = factory.createLineString(coords.toArray(Coordinate[]::new));
        path.setSRID(GeoMapper.SRID);
        return path;
    }

    private LineString longestLine(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        LineString longest = null;
        double best = -1;
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof LineString lineString && GeoMetrics.lengthM(lineString) > best) {
                best = GeoMetrics.lengthM(lineString);
                longest = lineString;
            }
        }
        if (longest == null && geometry instanceof LineString lineString) {
            return lineString;
        }
        return longest;
    }

    private LineString asLine(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof LineString lineString) {
            return lineString;
        }
        return longestLine(geometry);
    }

    private Geometry bufferWater(Point point, double meters, LakePlanningGeometry lake) {
        if (point == null || point.isEmpty()) {
            return null;
        }
        Geometry buffer = point.buffer(GeoMetrics.bufferDegrees(meters, point.getY()));
        return GeometrySanitizer.validateFixAndNormalize(waterIntersect(buffer, lake), 5);
    }

    private Geometry larger(Geometry a, Geometry b) {
        double aa = a == null || a.isEmpty() ? 0 : a.getArea();
        double ba = b == null || b.isEmpty() ? 0 : b.getArea();
        if (aa <= 0 && ba <= 0) {
            return null;
        }
        return aa >= ba ? a : b;
    }

    private Point firstValid(Point origin, LakePlanningGeometry lake) {
        if (origin == null || origin.isEmpty()) {
            return null;
        }
        if (lake.validFishingPoint(origin)) {
            return origin;
        }
        return locationService.nearestValidFishingPoint(origin, lake).orElse(null);
    }

    private CandidateSpot copySpot(CandidateSpot source) {
        CandidateSpot copy = new CandidateSpot();
        copy.setFeatureId(source.getFeatureId());
        copy.setType(source.getType());
        copy.setSourceGeometry(source.getSourceGeometry());
        copy.setLocation(source.getLocation());
        copy.setRepresentativeDepthM(source.getRepresentativeDepthM());
        copy.setMinDepthM(source.getMinDepthM());
        copy.setMaxDepthM(source.getMaxDepthM());
        copy.setFeatureConfidence(source.getFeatureConfidence());
        copy.setStrategyWeight(source.getStrategyWeight());
        copy.setStrategyRationale(source.getStrategyRationale());
        copy.setWindowFrom(source.getWindowFrom());
        copy.setWindowTo(source.getWindowTo());
        copy.setTechniques(source.getTechniques());
        copy.setAnalysisVersion(source.getAnalysisVersion());
        copy.setPipeline(source.getPipeline());
        copy.setWindowSpecific(source.isWindowSpecific());
        copy.setRawOrientationDeg(source.getRawOrientationDeg());
        copy.setLightPreference(source.getLightPreference());
        copy.setSourceFeatureIds(source.getSourceFeatureIds());
        copy.setEvidenceTypes(source.getEvidenceTypes());
        copy.setMinDepthM(source.getMinDepthM());
        copy.setMaxDepthM(source.getMaxDepthM());
        return copy;
    }

    private Point point(Coordinate coordinate) {
        Point point = factory.createPoint(coordinate);
        point.setSRID(GeoMapper.SRID);
        return point;
    }

    private static Coordinate midCoordinate(LineString line) {
        Coordinate[] coordinates = line.getCoordinates();
        return new Coordinate(
                (coordinates[0].x + coordinates[coordinates.length - 1].x) / 2.0,
                (coordinates[0].y + coordinates[coordinates.length - 1].y) / 2.0
        );
    }

    private static double corridorWidth(FeatureType type, PlanningProperties.Spatial spatial) {
        if (type == FeatureType.ISLAND_EDGE) {
            return spatial.getCorridorWidthShoreM();
        }
        if (type == FeatureType.DROP_OFF) {
            return spatial.getCorridorWidthDropOffM();
        }
        return spatial.getCorridorWidthDefaultM();
    }
}
