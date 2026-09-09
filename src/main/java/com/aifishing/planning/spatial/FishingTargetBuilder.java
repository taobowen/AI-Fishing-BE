package com.aifishing.planning.spatial;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
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
        if (type == FeatureType.DROP_OFF || type == FeatureType.ISLAND_EDGE || source.getDimension() == 1) {
            GenerateProfiler.current().start(GenerateProfiler.TARGET_SPLIT);
            List<LineString> parts = splitMeaningfully(asLines(source), spatial);
            GenerateProfiler.current().end(GenerateProfiler.TARGET_SPLIT);
            if (parts.isEmpty()) {
                applyPoint(spot, spot.getLocation(), lake, corridorWidth(type, spatial), snapshotId, 0, "point");
                return List.of(spot);
            }
            List<CandidateSpot> paths = new ArrayList<>();
            double chainage = 0;
            for (int i = 0; i < parts.size(); i++) {
                CandidateSpot copy = i == 0 ? spot : copySpot(spot);
                double length = GeoMetrics.lengthM(parts.get(i));
                String reason = parts.size() == 1 ? "whole" : (length >= spatial.getMaxSegmentLengthM() - 1 ? "max_length" : "orientation_change");
                applyPath(copy, parts.get(i), lake, corridorWidth(type, spatial), snapshotId, i, chainage, chainage + length, reason);
                chainage += length;
                paths.add(copy);
            }
            return paths;
        }
        if ((type == FeatureType.HUMP || type == FeatureType.FLAT || type == FeatureType.BASIN || type == FeatureType.POINT)
                && source.getDimension() >= 2
                && GeoMetrics.areaM2(source) >= 400) {
            return polygonPrimitives(spot, source, lake, spatial, snapshotId);
        }
        applyPoint(spot, spot.getLocation(), lake, spatial.getCorridorWidthDefaultM(), snapshotId, 0, "point");
        return List.of(spot);
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

    private List<CandidateSpot> polygonPrimitives(
            CandidateSpot spot,
            Geometry source,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            UUID snapshotId
    ) {
        Geometry waterGeom = waterIntersect(source, lake);
        List<CandidateSpot> out = new ArrayList<>();
        if (waterGeom == null || waterGeom.isEmpty()) {
            applyPoint(spot, spot.getLocation(), lake, spatial.getCorridorWidthDefaultM(), snapshotId, 0, "polygon_fallback_point");
            return List.of(spot);
        }
        LineString boundary = longestLine(waterGeom.getBoundary());
        if (boundary != null && GeoMetrics.lengthM(boundary) >= 30) {
            List<LineString> parts = splitMeaningfully(List.of(boundary), spatial);
            double chainage = 0;
            for (int i = 0; i < parts.size(); i++) {
                CandidateSpot pathSpot = i == 0 && out.isEmpty() ? spot : copySpot(spot);
                double length = GeoMetrics.lengthM(parts.get(i));
                applyPath(pathSpot, parts.get(i), lake, spatial.getCorridorWidthDefaultM(), snapshotId, i, chainage, chainage + length, "polygon_boundary");
                chainage += length;
                out.add(pathSpot);
            }
        }
        Point interior = firstValid(point(new InteriorPointArea(waterGeom).getInteriorPoint()), lake);
        if (interior != null) {
            CandidateSpot anchor = out.isEmpty() ? spot : copySpot(spot);
            applyPoint(anchor, interior, lake, spatial.getCorridorWidthDefaultM(), snapshotId, out.size(), "polygon_anchor");
            List<SpatialUtility.Sample> samples = new ArrayList<>();
            samples.add(new SpatialUtility.Sample(interior, 0));
            List<VisitPortal> portals = boundaryPortals(waterGeom, lake, 4);
            for (int i = 0; i < portals.size(); i++) {
                samples.add(new SpatialUtility.Sample(portals.get(i).point(), (i + 1) / (double) (portals.size() + 1)));
            }
            anchor.setStaticSamples(samples);
            if (out.isEmpty()) {
                return List.of(anchor);
            }
            out.add(anchor);
        }
        if (out.isEmpty()) {
            applyPoint(spot, spot.getLocation(), lake, spatial.getCorridorWidthDefaultM(), snapshotId, 0, "polygon_data_limitation");
            return List.of(spot);
        }
        return out;
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
        spot.setCoverageIds(List.of(id));
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
        Geometry water = lake.water().intersection(geometry);
        for (Geometry island : lake.islands()) {
            if (island != null && !island.isEmpty() && water != null && !water.isEmpty()) {
                try {
                    water = water.difference(island);
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

    private List<VisitPortal> boundaryPortals(Geometry geometry, LakePlanningGeometry lake, int count) {
        Coordinate[] coords = geometry.getBoundary().getCoordinates();
        if (coords.length < 2) {
            Point interior = firstValid(geometry.getInteriorPoint(), lake);
            return interior == null ? List.of() : List.of(new VisitPortal("p0", interior));
        }
        List<VisitPortal> portals = new ArrayList<>();
        int step = Math.max(1, coords.length / count);
        for (int i = 0; i < coords.length && portals.size() < count; i += step) {
            Point candidate = firstValid(point(coords[i]), lake);
            if (candidate != null) {
                portals.add(new VisitPortal("p" + portals.size(), candidate));
            }
        }
        return portals;
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
