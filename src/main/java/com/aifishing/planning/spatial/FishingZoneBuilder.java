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
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FishingZoneBuilder {

    private final LocalMetricCrs localMetricCrs;
    private final CandidateLocationService locationService;
    private final LakeNavRasterBuilder rasterBuilder;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
    private ClusterStats lastStats = ClusterStats.empty();

    public FishingZoneBuilder(
            LocalMetricCrs localMetricCrs,
            LocalWaterPathEstimator waterPath,
            CandidateLocationService locationService
    ) {
        this(localMetricCrs, waterPath, locationService, new LakeNavRasterBuilder(localMetricCrs));
    }

    @Autowired
    public FishingZoneBuilder(
            LocalMetricCrs localMetricCrs,
            LocalWaterPathEstimator waterPath,
            CandidateLocationService locationService,
            LakeNavRasterBuilder rasterBuilder
    ) {
        this.localMetricCrs = localMetricCrs;
        this.locationService = locationService;
        this.rasterBuilder = rasterBuilder;
    }

    public ClusterStats lastStats() {
        return lastStats;
    }

    public List<CandidateSpot> cluster(
            List<CandidateSpot> targets,
            LakePlanningGeometry lake,
            PlanningProperties properties
    ) {
        return cluster(targets, lake, properties, new SpatialSkipLedger());
    }

    public List<CandidateSpot> cluster(
            List<CandidateSpot> targets,
            LakePlanningGeometry lake,
            PlanningProperties properties,
            SpatialSkipLedger ledger
    ) {
        return cluster(targets, lake, properties, ledger, null);
    }

    public List<CandidateSpot> cluster(
            List<CandidateSpot> targets,
            LakePlanningGeometry lake,
            PlanningProperties properties,
            SpatialSkipLedger ledger,
            LakeNavRaster raster
    ) {
        GenerateProfiler.current().start(GenerateProfiler.PHYSICAL_ZONE_BUILD);
        PlanningProperties.Spatial spatial = properties.getSpatial();
        SpatialSkipLedger skips = ledger == null ? new SpatialSkipLedger() : ledger;
        List<CandidateSpot> atomic = new ArrayList<>();
        Map<UUID, Geometry> polygons = new LinkedHashMap<>();
        for (CandidateSpot spot : targets) {
            if (spot.getTargetKind() == TargetKind.ZONE) {
                continue;
            }
            Geometry polygon = polygonalComponent(spot, spatial);
            if (polygon == null) {
                skips.skipTarget(spot.getFishingTargetId() == null ? spot.getFeatureId() : spot.getFishingTargetId(),
                        "NOT_POLYGONAL");
                continue;
            }
            atomic.add(spot);
            if (spot.getFishingTargetId() != null) {
                polygons.put(spot.getFishingTargetId(), polygon);
            }
        }
        int n = atomic.size();
        long theoretical = n < 2 ? 0 : (long) n * (n - 1) / 2;
        if (n < spatial.getClusterMinMembers()) {
            lastStats = new ClusterStats(theoretical, 0, 0, 0);
            GenerateProfiler.current().end(GenerateProfiler.PHYSICAL_ZONE_BUILD);
            GenerateProfiler.current().count("physicalZoneCount", 0);
            return List.of();
        }
        LakeNavRaster nav = raster != null ? raster : rasterBuilder.build(lake, spatial);
        GrowResult grown = growClusters(atomic, polygons, lake, spatial, nav);
        lastStats = grown.stats();
        List<CandidateSpot> zones = new ArrayList<>();
        for (List<Integer> component : grown.components()) {
            if (component.size() < spatial.getClusterMinMembers()) {
                continue;
            }
            skips.addAttemptedPartition();
            String partitionId = partitionId(component, atomic);
            try {
                CandidateSpot zone = toZone(component, atomic, lake, spatial, skips, partitionId);
                if (zone != null) {
                    zones.add(zone);
                } else {
                    skips.skipPartition(partitionId, "ENVELOPE_FAILED");
                }
            } catch (RuntimeException ex) {
                skips.skipPartition(partitionId, "ZONE_BUILD_FAILED");
            }
        }
        GenerateProfiler.current().end(GenerateProfiler.PHYSICAL_ZONE_BUILD);
        GenerateProfiler.current().count("physicalZoneCount", zones.size());
        return zones;
    }

    private GrowResult growClusters(
            List<CandidateSpot> atomic,
            Map<UUID, Geometry> polygons,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            LakeNavRaster raster
    ) {
        int n = atomic.size();
        long theoretical = (long) n * (n - 1) / 2;
        List<Geometry> discovery = new ArrayList<>(n);
        STRtree tree = new STRtree();
        double searchM = spatial.getZoneNeighborSearchRadiusM();
        for (int i = 0; i < n; i++) {
            Geometry geom = discoveryGeometry(atomic.get(i), polygons);
            discovery.add(geom);
            if (geom == null || geom.isEmpty()) {
                continue;
            }
            Envelope env = new Envelope(geom.getEnvelopeInternal());
            env.expandBy(GeoMetrics.bufferDegrees(searchM, GeoMetrics.referenceLat(geom)));
            tree.insert(env, i);
        }
        tree.build();
        long neighborPairs = 0;
        long expensive = 0;
        long astar = 0;
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Geometry geom = discovery.get(i);
            if (geom == null || geom.isEmpty()) {
                continue;
            }
            Envelope query = new Envelope(geom.getEnvelopeInternal());
            query.expandBy(GeoMetrics.bufferDegrees(searchM, GeoMetrics.referenceLat(geom)));
            @SuppressWarnings("unchecked")
            List<Integer> hits = tree.query(query);
            for (Integer j : hits) {
                if (j == null || j <= i) {
                    continue;
                }
                neighborPairs++;
                PairEval eval = pairLinkMeters(
                        atomic.get(i), atomic.get(j), discovery.get(i), discovery.get(j), lake, spatial, raster);
                if (eval.expensive()) {
                    expensive++;
                }
                if (eval.astar()) {
                    astar++;
                }
                if (eval.meters() != null) {
                    links.add(new Link(i, j, eval.meters()));
                }
            }
        }
        links.sort(Comparator.comparingDouble(Link::meters));
        UnionFind uf = new UnionFind(n);
        List<Link> accepted = new ArrayList<>();
        Map<Integer, Double> weightByRoot = new HashMap<>();
        Map<Integer, Double> areaByRoot = new HashMap<>();
        Map<Integer, Envelope> bboxByRoot = new HashMap<>();
        for (int i = 0; i < n; i++) {
            weightByRoot.put(i, coverageWeight(atomic.get(i), spatial));
            areaByRoot.put(i, GeoMetrics.areaM2(discovery.get(i)));
            bboxByRoot.put(i, spanEnvelope(atomic.get(i), discovery.get(i)));
        }
        for (Link link : links) {
            int ra = uf.find(link.i());
            int rb = uf.find(link.j());
            if (ra == rb) {
                continue;
            }
            List<Integer> merged = uf.members(link.i());
            merged.addAll(uf.members(link.j()));
            List<Link> mergedEdges = new ArrayList<>();
            for (Link edge : accepted) {
                if (uf.find(edge.i()) == ra || uf.find(edge.i()) == rb
                        || uf.find(edge.j()) == ra || uf.find(edge.j()) == rb) {
                    mergedEdges.add(edge);
                }
            }
            mergedEdges.add(link);
            double weight = weightByRoot.getOrDefault(ra, 0.0) + weightByRoot.getOrDefault(rb, 0.0);
            double areaM2 = areaByRoot.getOrDefault(ra, 0.0) + areaByRoot.getOrDefault(rb, 0.0);
            Envelope bbox = new Envelope(bboxByRoot.get(ra));
            bbox.expandToInclude(bboxByRoot.get(rb));
            if (!coherent(merged, mergedEdges, areaM2, weight, bbox, spatial)) {
                continue;
            }
            uf.union(link.i(), link.j());
            int root = uf.find(link.i());
            accepted.add(link);
            weightByRoot.put(root, weight);
            areaByRoot.put(root, areaM2);
            bboxByRoot.put(root, bbox);
        }
        List<List<Integer>> components = uf.components();
        ClusterStats stats = new ClusterStats(theoretical, neighborPairs, expensive, astar);
        return new GrowResult(components, stats);
    }

    private PairEval pairLinkMeters(
            CandidateSpot a,
            CandidateSpot b,
            Geometry geomA,
            Geometry geomB,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            LakeNavRaster raster
    ) {
        Point[] nearest = nearestPoints(geomA, geomB, a, b);
        if (nearest[0] == null || nearest[1] == null) {
            return PairEval.none();
        }
        double joinMax = spatial.getZoneWaterPathJoinMaxM();
        double geodesic = GeoMetrics.distanceM(nearest[0], nearest[1]);
        if (geodesic > joinMax) {
            return new PairEval(null, false, true);
        }
        if (lake.crossesIsland(nearest[0], nearest[1])) {
            return new PairEval(null, false, true);
        }
        if (!lake.landCrossing(nearest[0], nearest[1])) {
            return new PairEval(geodesic, false, true);
        }
        if (raster == null) {
            return new PairEval(null, false, true);
        }
        try {
            var path = raster.shortest(nearest[0], nearest[1], spatial.getInternalCruiseKmh(), joinMax);
            if (path.isPresent() && path.get().meters() <= joinMax) {
                return new PairEval(path.get().meters(), true, true);
            }
            return new PairEval(null, true, true);
        } catch (RuntimeException ex) {
            return new PairEval(null, true, true);
        }
    }

    private static Point[] nearestPoints(Geometry geomA, Geometry geomB, CandidateSpot a, CandidateSpot b) {
        if (geomA != null && !geomA.isEmpty() && geomB != null && !geomB.isEmpty()) {
            Coordinate[] coords = DistanceOp.nearestPoints(geomA, geomB);
            return new Point[]{pointOf(coords[0], geomA), pointOf(coords[1], geomB)};
        }
        return new Point[]{a.getEntryPoint(), b.getEntryPoint()};
    }

    private static Point pointOf(Coordinate coordinate, Geometry owner) {
        Point point = owner.getFactory().createPoint(coordinate);
        point.setSRID(GeoMapper.SRID);
        return point;
    }

    private static Geometry discoveryGeometry(CandidateSpot spot, Map<UUID, Geometry> polygons) {
        if (spot.getFishingTargetId() != null && polygons.get(spot.getFishingTargetId()) != null) {
            return polygons.get(spot.getFishingTargetId());
        }
        if (spot.getTargetGeometry() != null && !spot.getTargetGeometry().isEmpty()) {
            return spot.getTargetGeometry();
        }
        if (spot.getFishingCorridor() != null && !spot.getFishingCorridor().isEmpty()) {
            return spot.getFishingCorridor();
        }
        return spot.getLocation();
    }

    private static double coverageWeight(CandidateSpot spot, PlanningProperties.Spatial spatial) {
        if (spot.getTargetKind() != null && spot.getTargetKind().isPathLike()) {
            double length = 0;
            if (spot.getChainageStartM() != null && spot.getChainageEndM() != null) {
                length = Math.abs(spot.getChainageEndM() - spot.getChainageStartM());
            } else {
                Geometry geom = spot.getTargetGeometry() != null ? spot.getTargetGeometry() : spot.getSourceGeometry();
                length = GeoMetrics.lengthM(geom);
            }
            return Math.max(1.0, length / spatial.getPathCoverageUnitM());
        }
        return 1.0;
    }

    private static Envelope spanEnvelope(CandidateSpot spot, Geometry discovery) {
        Envelope env = new Envelope();
        if (discovery != null && !discovery.isEmpty()) {
            env.expandToInclude(discovery.getEnvelopeInternal());
        }
        if (spot.getLocation() != null && !spot.getLocation().isEmpty()) {
            env.expandToInclude(spot.getLocation().getCoordinate());
        }
        return env;
    }

    private static double bboxSpanM(Envelope bbox) {
        if (bbox == null || bbox.isNull()) {
            return 0;
        }
        double lat = (bbox.getMinY() + bbox.getMaxY()) / 2.0;
        double dLat = (bbox.getMaxY() - bbox.getMinY()) * GeoMetrics.metersPerDegreeLat();
        double dLng = (bbox.getMaxX() - bbox.getMinX()) * GeoMetrics.metersPerDegreeLng(lat);
        return Math.hypot(dLat, dLng);
    }

    private static boolean coherent(
            List<Integer> members,
            List<Link> edges,
            double areaM2,
            double weight,
            Envelope bbox,
            PlanningProperties.Spatial spatial
    ) {
        double diameter = Math.max(treeDiameter(members, edges), bboxSpanM(bbox));
        if (diameter > spatial.getZoneMaxWaterPathDiameterM()) {
            return false;
        }
        double maxGap = 0;
        for (Link edge : edges) {
            maxGap = Math.max(maxGap, edge.meters());
        }
        if (maxGap > spatial.getZoneMaxInternalGapM()) {
            return false;
        }
        double areaKm2 = Math.max(areaM2 / 1_000_000.0, 1e-6);
        if (weight / areaKm2 < spatial.getZoneMinCoverageDensity()) {
            return false;
        }
        if (areaM2 > 1 && diameter / Math.sqrt(areaM2) > 8.0) {
            return false;
        }
        return true;
    }

    private static double treeDiameter(List<Integer> members, List<Link> edges) {
        if (members.isEmpty()) {
            return 0;
        }
        Map<Integer, List<int[]>> adj = new HashMap<>();
        for (int member : members) {
            adj.put(member, new ArrayList<>());
        }
        for (Link edge : edges) {
            adj.computeIfAbsent(edge.i(), ignored -> new ArrayList<>()).add(new int[]{edge.j(), (int) Math.round(edge.meters())});
            adj.computeIfAbsent(edge.j(), ignored -> new ArrayList<>()).add(new int[]{edge.i(), (int) Math.round(edge.meters())});
        }
        int start = members.get(0);
        int[] far = farthest(start, adj);
        return farthest(far[0], adj)[1];
    }

    private static int[] farthest(int start, Map<Integer, List<int[]>> adj) {
        Map<Integer, Integer> dist = new HashMap<>();
        ArrayList<Integer> queue = new ArrayList<>();
        dist.put(start, 0);
        queue.add(start);
        int bestNode = start;
        int best = 0;
        for (int q = 0; q < queue.size(); q++) {
            int node = queue.get(q);
            int d = dist.get(node);
            if (d > best) {
                best = d;
                bestNode = node;
            }
            for (int[] next : adj.getOrDefault(node, List.of())) {
                if (dist.containsKey(next[0])) {
                    continue;
                }
                dist.put(next[0], d + next[1]);
                queue.add(next[0]);
            }
        }
        return new int[]{bestNode, best};
    }

    private record PairEval(Double meters, boolean astar, boolean expensive) {
        private static PairEval none() {
            return new PairEval(null, false, false);
        }
    }

    private record Link(int i, int j, double meters) {
    }

    private record GrowResult(List<List<Integer>> components, ClusterStats stats) {
    }

    private static final class UnionFind {
        private final int[] parent;
        private final int[] rank;
        private final List<List<Integer>> members;

        private UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            members = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                members.add(new ArrayList<>(List.of(i)));
            }
        }

        private int find(int i) {
            if (parent[i] != i) {
                parent[i] = find(parent[i]);
            }
            return parent[i];
        }

        private void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return;
            }
            if (rank[ra] < rank[rb]) {
                int tmp = ra;
                ra = rb;
                rb = tmp;
            }
            parent[rb] = ra;
            members.get(ra).addAll(members.get(rb));
            members.set(rb, List.of());
            if (rank[ra] == rank[rb]) {
                rank[ra]++;
            }
        }

        private List<Integer> members(int i) {
            return new ArrayList<>(members.get(find(i)));
        }

        private List<List<Integer>> components() {
            List<List<Integer>> out = new ArrayList<>();
            for (int i = 0; i < parent.length; i++) {
                if (find(i) == i && !members.get(i).isEmpty()) {
                    out.add(List.copyOf(members.get(i)));
                }
            }
            return out;
        }
    }

    private CandidateSpot toZone(
            List<Integer> indexes,
            List<CandidateSpot> atomic,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            SpatialSkipLedger ledger,
            String partitionId
    ) {
        List<CandidateSpot> members = indexes.stream().map(atomic::get).toList();
        Geometry envelope = envelopeGeometry(members, lake, spatial, ledger, partitionId);
        if (envelope == null) {
            return null;
        }
        Point representative = envelope.getInteriorPoint();
        if (!lake.validFishingPoint(representative)) {
            representative = locationService.nearestValidFishingPoint(representative, lake).orElse(null);
        }
        if (representative == null) {
            return null;
        }
        GenerateProfiler.current().start(GenerateProfiler.PORTAL_BUILD);
        List<VisitPortal> portals = zonePortals(envelope, lake, spatial.getMaxZonePortals());
        GenerateProfiler.current().end(GenerateProfiler.PORTAL_BUILD);
        if (portals.size() < 2) {
            return null;
        }
        CandidateSpot zone = new CandidateSpot();
        String memberFp = members.stream()
                .map(m -> String.valueOf(m.getFishingTargetId() == null ? m.getFeatureId() : m.getFishingTargetId()))
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        UUID zoneId = SpatialIds.zoneId(members.get(0).getFishingTargetId(), memberFp);
        zone.setFeatureId(zoneId);
        zone.setZoneId(zoneId);
        zone.setType(dominantType(members));
        zone.setTargetKind(TargetKind.ZONE);
        zone.setTargetGeometry(envelope);
        zone.setFishingCorridor(envelope);
        zone.setVisitEnvelope(envelope);
        zone.setLocation(representative);
        zone.setEntryPoint(portals.get(0).point());
        zone.setExitPoint(portals.get(portals.size() - 1).point());
        zone.setPortals(portals);
        zone.setZoneMembers(members);
        List<UUID> coverage = new ArrayList<>();
        coverage.add(zoneId);
        members.forEach(member -> coverage.addAll(member.coverageIds()));
        zone.setCoverageIds(coverage);
        zone.setPipeline(members.get(0).getPipeline());
        zone.setAnalysisVersion(members.get(0).getAnalysisVersion());
        zone.setFeatureConfidence(members.stream()
                .map(CandidateSpot::getFeatureConfidence)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5));
        zone.setStrategyWeight(members.stream().mapToDouble(CandidateSpot::getStrategyWeight).average().orElse(0.5));
        zone.setWindowFrom(members.get(0).getWindowFrom());
        zone.setWindowTo(members.get(0).getWindowTo());
        zone.setTechniques(members.get(0).getTechniques());
        zone.setLightPreference(members.get(0).getLightPreference());
        zone.setMinDepthM(members.stream().map(CandidateSpot::getMinDepthM).filter(java.util.Objects::nonNull).min(Double::compareTo).orElse(null));
        zone.setMaxDepthM(members.stream().map(CandidateSpot::getMaxDepthM).filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(null));
        if (zone.getMinDepthM() != null && zone.getMaxDepthM() != null) {
            zone.setRepresentativeDepthM((zone.getMinDepthM() + zone.getMaxDepthM()) / 2.0);
        }
        return zone;
    }

    Geometry envelopeGeometry(
            List<CandidateSpot> members,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial
    ) {
        return envelopeGeometry(members, lake, spatial, new SpatialSkipLedger(), "ad-hoc");
    }

    Geometry envelopeGeometry(
            List<CandidateSpot> members,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            SpatialSkipLedger ledger,
            String partitionId
    ) {
        List<Geometry> parts = new ArrayList<>();
        for (CandidateSpot member : members) {
            Geometry polygon = polygonalComponent(member, spatial);
            if (polygon == null) {
                ledger.skipTarget(member.getFishingTargetId() == null ? member.getFeatureId() : member.getFishingTargetId(),
                        "NOT_POLYGONAL");
                continue;
            }
            parts.add(polygon);
        }
        if (parts.isEmpty()) {
            return null;
        }
        Geometry union;
        try {
            union = UnaryUnionOp.union(parts);
        } catch (RuntimeException ex) {
            return null;
        }
        union = GeometrySanitizer.validateFixAndNormalize(union, spatial.getSliverMinAreaM2());
        if (union == null || union.isEmpty()) {
            return null;
        }
        double lng = members.get(0).getLocation().getX();
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(members.get(0).getLocation(), lng);
        Geometry metric = projected.toMetric(union);
        ConcaveHull hull = new ConcaveHull(metric);
        hull.setMaximumEdgeLengthRatio(spatial.getConcaveHullEdgeLengthRatio());
        hull.setHolesAllowed(true);
        Geometry concave = projected.toWgs84(hull.getHull());
        if (concave == null || concave.isEmpty()) {
            return null;
        }
        concave = TopologyPreservingSimplifier.simplify(concave, GeoMetrics.bufferDegrees(3, members.get(0).getLocation().getY()));
        concave = GeometrySanitizer.validateFixAndNormalize(concave, spatial.getSliverMinAreaM2());
        if (concave == null || concave.isEmpty()) {
            return null;
        }
        Geometry water = concave;
        if (lake.hasWater()) {
            try {
                water = PolygonalGeometries.of(lake.water().intersection(concave));
            } catch (RuntimeException ex) {
                return null;
            }
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
        return GeometrySanitizer.validateFixAndNormalize(water, spatial.getSliverMinAreaM2());
    }

    private List<VisitPortal> zonePortals(Geometry envelope, LakePlanningGeometry lake, int count) {
        CoordinateLoop loop = new CoordinateLoop(envelope.getBoundary().getCoordinates());
        List<VisitPortal> portals = new ArrayList<>();
        int n = Math.max(2, count);
        for (int i = 0; i < n; i++) {
            Point candidate = loop.at(i / (double) n);
            Point water = locationService.nearestValidFishingPoint(candidate, lake).orElse(null);
            if (water != null) {
                portals.add(new VisitPortal("z" + i, water));
            }
        }
        return portals;
    }

    private static FeatureType dominantType(List<CandidateSpot> members) {
        Map<FeatureType, Integer> counts = new LinkedHashMap<>();
        for (CandidateSpot member : members) {
            counts.merge(member.getType(), 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(FeatureType.ISLAND_EDGE);
    }

    Geometry polygonalComponent(CandidateSpot member, PlanningProperties.Spatial spatial) {
        if (member == null) {
            return null;
        }
        double width = corridorWidth(member, spatial);
        TargetKind kind = member.getTargetKind();
        if (kind != null && kind.isPathLike()) {
            Geometry corridor = polygonalOrNull(member.getFishingCorridor(), spatial);
            if (corridor != null) {
                return corridor;
            }
            return bufferToPolygon(member.getTargetGeometry(), width, spatial);
        }
        if (kind == TargetKind.POINT) {
            Geometry point = member.getLocation() != null ? member.getLocation() : member.getTargetGeometry();
            return bufferToPolygon(point, width / 2.0, spatial);
        }
        Geometry source = member.getFishingCorridor() != null ? member.getFishingCorridor() : member.getTargetGeometry();
        if (source != null && source.getDimension() >= 2) {
            return polygonalOrNull(source, spatial);
        }
        if (source != null && source.getDimension() == 1) {
            return bufferToPolygon(source, width, spatial);
        }
        if (source instanceof Point) {
            return bufferToPolygon(source, width / 2.0, spatial);
        }
        return bufferToPolygon(member.getLocation(), width / 2.0, spatial);
    }

    private static Geometry polygonalOrNull(Geometry geometry, PlanningProperties.Spatial spatial) {
        if (geometry == null || geometry.isEmpty() || geometry.getDimension() < 2) {
            return null;
        }
        return GeometrySanitizer.validateFixAndNormalize(geometry, spatial.getSliverMinAreaM2());
    }

    private static Geometry bufferToPolygon(Geometry geometry, double meters, PlanningProperties.Spatial spatial) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        try {
            Geometry buffered = geometry.buffer(GeoMetrics.bufferDegrees(meters, GeoMetrics.referenceLat(geometry)));
            return GeometrySanitizer.validateFixAndNormalize(buffered, spatial.getSliverMinAreaM2());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static double corridorWidth(CandidateSpot member, PlanningProperties.Spatial spatial) {
        if (member.getFishingCorridorWidthM() != null && member.getFishingCorridorWidthM() > 0) {
            return member.getFishingCorridorWidthM();
        }
        if (member.getType() == FeatureType.ISLAND_EDGE) {
            return spatial.getCorridorWidthShoreM();
        }
        if (member.getType() == FeatureType.DROP_OFF) {
            return spatial.getCorridorWidthDropOffM();
        }
        return spatial.getCorridorWidthDefaultM();
    }

    private static String partitionId(List<Integer> indexes, List<CandidateSpot> atomic) {
        return indexes.stream()
                .map(atomic::get)
                .map(spot -> String.valueOf(spot.getFishingTargetId() == null ? spot.getFeatureId() : spot.getFishingTargetId()))
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private final class CoordinateLoop {
        private final org.locationtech.jts.geom.Coordinate[] coords;

        private CoordinateLoop(org.locationtech.jts.geom.Coordinate[] coords) {
            this.coords = coords == null ? new org.locationtech.jts.geom.Coordinate[0] : coords;
        }

        private Point at(double fraction) {
            if (coords.length == 0) {
                return factory.createPoint();
            }
            int index = Math.min(coords.length - 1, Math.max(0, (int) Math.round(fraction * (coords.length - 1))));
            Point point = factory.createPoint(coords[index]);
            point.setSRID(GeoMapper.SRID);
            return point;
        }
    }
}
