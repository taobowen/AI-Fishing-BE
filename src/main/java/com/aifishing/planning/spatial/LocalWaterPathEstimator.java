package com.aifishing.planning.spatial;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;

@Component
public class LocalWaterPathEstimator {

    private static final int[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final LocalMetricCrs localMetricCrs;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public LocalWaterPathEstimator(LocalMetricCrs localMetricCrs) {
        this.localMetricCrs = localMetricCrs;
    }

    public Optional<PathEstimate> path(
            Point from,
            Point to,
            LakePlanningGeometry lake,
            Geometry clip,
            PlanningProperties.Spatial spatial
    ) {
        if (from == null || to == null || lake == null || !lake.hasWater()) {
            return Optional.empty();
        }
        GenerateProfiler.current().count("astarCalls");
        if (!lake.validFishingPoint(from) || !lake.validFishingPoint(to)) {
            return Optional.empty();
        }
        double cell = spatial.getLocalPathCellSizeM();
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(lake.water(), from.getX());
        Geometry water = projected.toMetric(lake.water());
        Geometry islands = union(lake.islands(), projected);
        Geometry metricFrom = projected.toMetric(from);
        Geometry metricTo = projected.toMetric(to);
        Envelope envelope = envelope(clip, projected, metricFrom, metricTo, cell);
        int cols = Math.max(1, (int) Math.ceil(envelope.getWidth() / cell) + 1);
        int rows = Math.max(1, (int) Math.ceil(envelope.getHeight() / cell) + 1);
        while ((long) cols * rows > spatial.getLocalPathMaxCells() && cell < 200) {
            cell *= 1.5;
            cols = Math.max(1, (int) Math.ceil(envelope.getWidth() / cell) + 1);
            rows = Math.max(1, (int) Math.ceil(envelope.getHeight() / cell) + 1);
        }
        Geometry blocked = islands == null || islands.isEmpty() ? null : islands.buffer(cell * 0.55);
        Geometry localWater = clipToEnvelope(water, envelope);
        Geometry localBlocked = clipToEnvelope(blocked, envelope);
        if (localWater == null || localWater.isEmpty()) {
            return Optional.empty();
        }
        PreparedGeometry preparedWater = PreparedGeometryFactory.prepare(localWater);
        PreparedGeometry preparedBlocked =
                localBlocked == null || localBlocked.isEmpty()
                        ? null
                        : PreparedGeometryFactory.prepare(localBlocked);
        boolean[][] nav = new boolean[cols][rows];
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                double mx = envelope.getMinX() + (x + 0.5) * cell;
                double my = envelope.getMinY() + (y + 0.5) * cell;
                Point cellPoint = metricPoint(water.getFactory(), mx, my, projected.srid());
                nav[x][y] = preparedWater.covers(cellPoint)
                        && (preparedBlocked == null || !preparedBlocked.covers(cellPoint));
            }
        }
        int[] start = snap(metricFrom.getCoordinate(), envelope, cell, cols, rows, nav);
        int[] goal = snap(metricTo.getCoordinate(), envelope, cell, cols, rows, nav);
        if (start == null || goal == null) {
            return Optional.empty();
        }
        List<int[]> cells = astar(nav, start, goal, cell);
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        List<Coordinate> coords = new ArrayList<>();
        for (int[] cellIdx : cells) {
            double mx = envelope.getMinX() + (cellIdx[0] + 0.5) * cell;
            double my = envelope.getMinY() + (cellIdx[1] + 0.5) * cell;
            Point wgs = projected.toWgs84Point(metricPoint(water.getFactory(), mx, my, projected.srid()));
            coords.add(wgs.getCoordinate());
        }
        prependIfWater(coords, from, lake);
        appendIfWater(coords, to, lake);
        if (coords.size() < 2) {
            return Optional.empty();
        }
        LineString line = factory.createLineString(coords.toArray(Coordinate[]::new));
        line.setSRID(GeoMapper.SRID);
        if (crossesLand(line, lake)) {
            return Optional.empty();
        }
        double meters = 0;
        for (int i = 1; i < coords.size(); i++) {
            meters += hypotM(coords.get(i - 1), coords.get(i));
        }
        double kmh = spatial.getInternalCruiseKmh();
        double minutes = meters / 1000.0 / kmh * 60.0;
        return Optional.of(new PathEstimate(line, meters, minutes));
    }

    public boolean connected(
            Point from,
            Point to,
            LakePlanningGeometry lake,
            Geometry clip,
            PlanningProperties.Spatial spatial
    ) {
        return path(from, to, lake, clip, spatial).isPresent();
    }

    private static List<int[]> astar(boolean[][] nav, int[] start, int[] goal, double cell) {
        int cols = nav.length;
        int rows = nav[0].length;
        record Node(int x, int y, double g, double f) {
        }
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        long startKey = key(start[0], start[1]);
        open.add(new Node(start[0], start[1], 0, heuristic(start, goal, cell)));
        best.put(startKey, 0.0);
        while (!open.isEmpty()) {
            Node current = open.poll();
            if (current.x == goal[0] && current.y == goal[1]) {
                return reconstruct(parent, goal);
            }
            long currentKey = key(current.x, current.y);
            if (current.g > best.getOrDefault(currentKey, Double.POSITIVE_INFINITY) + 1e-9) {
                continue;
            }
            for (int[] dir : DIRS) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || !nav[nx][ny]) {
                    continue;
                }
                if (dir[0] != 0 && dir[1] != 0
                        && (!nav[current.x + dir[0]][current.y] || !nav[current.x][current.y + dir[1]])) {
                    continue;
                }
                double step = (dir[0] != 0 && dir[1] != 0) ? cell * Math.sqrt(2) : cell;
                double g = current.g + step;
                long nextKey = key(nx, ny);
                if (g + 1e-9 < best.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) {
                    best.put(nextKey, g);
                    parent.put(nextKey, currentKey);
                    open.add(new Node(nx, ny, g, g + heuristic(new int[]{nx, ny}, goal, cell)));
                }
            }
        }
        return List.of();
    }

    private static List<int[]> reconstruct(Map<Long, Long> parent, int[] goal) {
        List<int[]> path = new ArrayList<>();
        long cursor = key(goal[0], goal[1]);
        while (true) {
            path.addFirst(new int[]{(int) (cursor >> 32), (int) cursor});
            Long prev = parent.get(cursor);
            if (prev == null) {
                break;
            }
            cursor = prev;
        }
        return path;
    }

    private static double heuristic(int[] a, int[] b, double cell) {
        double dx = (a[0] - b[0]) * cell;
        double dy = (a[1] - b[1]) * cell;
        return Math.hypot(dx, dy);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static int[] snap(Coordinate metric, Envelope envelope, double cell, int cols, int rows, boolean[][] nav) {
        int x = (int) Math.floor((metric.x - envelope.getMinX()) / cell);
        int y = (int) Math.floor((metric.y - envelope.getMinY()) / cell);
        x = Math.max(0, Math.min(cols - 1, x));
        y = Math.max(0, Math.min(rows - 1, y));
        if (nav[x][y]) {
            return new int[]{x, y};
        }
        int bestX = -1;
        int bestY = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                if (!nav[i][j]) {
                    continue;
                }
                double d = Math.hypot(i - x, j - y);
                if (d < best) {
                    best = d;
                    bestX = i;
                    bestY = j;
                }
            }
        }
        return bestX < 0 ? null : new int[]{bestX, bestY};
    }

    private Envelope envelope(
            Geometry clip,
            LocalMetricCrs.ProjectedGeometry projected,
            Geometry metricFrom,
            Geometry metricTo,
            double cell
    ) {
        Envelope envelope = new Envelope();
        envelope.expandToInclude(metricFrom.getCoordinate());
        envelope.expandToInclude(metricTo.getCoordinate());
        if (clip != null && !clip.isEmpty()) {
            envelope.expandToInclude(projected.toMetric(clip).getEnvelopeInternal());
        }
        envelope.expandBy(Math.max(cell * 2, 40));
        return envelope;
    }

    private static Geometry clipToEnvelope(Geometry geometry, Envelope envelope) {
        if (geometry == null || geometry.isEmpty()) {
            return geometry;
        }
        try {
            Geometry clip = geometry.getFactory().toGeometry(envelope);
            clip.setSRID(geometry.getSRID());
            return geometry.intersection(clip);
        } catch (Exception ignored) {
            return geometry;
        }
    }

    private Geometry union(List<Geometry> islands, LocalMetricCrs.ProjectedGeometry projected) {
        Geometry acc = null;
        for (Geometry island : islands) {
            if (island == null || island.isEmpty()) {
                continue;
            }
            Geometry metric = projected.toMetric(island);
            acc = acc == null ? metric : acc.union(metric);
        }
        return acc;
    }

    private static Point metricPoint(GeometryFactory factory, double x, double y, int srid) {
        Point point = factory.createPoint(new Coordinate(x, y));
        point.setSRID(srid);
        return point;
    }

    private void prependIfWater(List<Coordinate> coords, Point from, LakePlanningGeometry lake) {
        if (coords.isEmpty()) {
            coords.add(from.getCoordinate());
            return;
        }
        Point first = point(coords.get(0));
        if (!from.equals(first) && !lake.landCrossing(from, first)) {
            coords.add(0, from.getCoordinate());
        }
    }

    private void appendIfWater(List<Coordinate> coords, Point to, LakePlanningGeometry lake) {
        Point last = point(coords.get(coords.size() - 1));
        if (!to.equals(last) && !lake.landCrossing(last, to)) {
            coords.add(to.getCoordinate());
        }
    }

    private static boolean crossesLand(LineString line, LakePlanningGeometry lake) {
        for (int i = 1; i < line.getNumPoints(); i++) {
            Point a = line.getPointN(i - 1);
            Point b = line.getPointN(i);
            a.setSRID(GeoMapper.SRID);
            b.setSRID(GeoMapper.SRID);
            if (lake.landCrossing(a, b)) {
                return true;
            }
        }
        return false;
    }

    private Point point(Coordinate coordinate) {
        Point point = factory.createPoint(coordinate);
        point.setSRID(GeoMapper.SRID);
        return point;
    }

    private static double hypotM(Coordinate a, Coordinate b) {
        double lat = (a.y + b.y) / 2.0;
        double dLat = (a.y - b.y) * 111_320.0;
        double dLng = (a.x - b.x) * 111_320.0 * Math.cos(Math.toRadians(lat));
        return Math.hypot(dLat, dLng);
    }

    public ZoneNavGraph buildGraph(
            UUID zoneId,
            Geometry clip,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial
    ) {
        List<ZoneNavGraph.Node> nodes = new ArrayList<>();
        List<ZoneNavGraph.Edge> edges = new ArrayList<>();
        if (clip == null || lake == null || !lake.hasWater()) {
            return new ZoneNavGraph(zoneId, nodes, edges);
        }
        double cell = spatial.getLocalPathCellSizeM();
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(lake.water(), clip.getCoordinate().x);
        Geometry water = projected.toMetric(lake.water());
        Geometry islands = union(lake.islands(), projected);
        Envelope envelope = projected.toMetric(clip).getEnvelopeInternal();
        envelope.expandBy(Math.max(cell * 2, 40));
        int cols = Math.max(1, (int) Math.ceil(envelope.getWidth() / cell) + 1);
        int rows = Math.max(1, (int) Math.ceil(envelope.getHeight() / cell) + 1);
        while ((long) cols * rows > Math.min(spatial.getLocalPathMaxCells(), spatial.getMaxNavNodesPerZone() * 4L) && cell < 200) {
            cell *= 1.5;
            cols = Math.max(1, (int) Math.ceil(envelope.getWidth() / cell) + 1);
            rows = Math.max(1, (int) Math.ceil(envelope.getHeight() / cell) + 1);
        }
        Geometry blocked = islands == null || islands.isEmpty() ? null : islands.buffer(cell * 0.55);
        Geometry localWater = clipToEnvelope(water, envelope);
        Geometry localBlocked = clipToEnvelope(blocked, envelope);
        if (localWater == null || localWater.isEmpty()) {
            return new ZoneNavGraph(zoneId, nodes, edges);
        }
        PreparedGeometry preparedWater = PreparedGeometryFactory.prepare(localWater);
        PreparedGeometry preparedBlocked =
                localBlocked == null || localBlocked.isEmpty() ? null : PreparedGeometryFactory.prepare(localBlocked);
        UUID[][] ids = new UUID[cols][rows];
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                double mx = envelope.getMinX() + (x + 0.5) * cell;
                double my = envelope.getMinY() + (y + 0.5) * cell;
                Point cellPoint = metricPoint(water.getFactory(), mx, my, projected.srid());
                boolean nav = preparedWater.covers(cellPoint)
                        && (preparedBlocked == null || !preparedBlocked.covers(cellPoint));
                if (!nav) {
                    continue;
                }
                Point wgs = projected.toWgs84Point(cellPoint);
                UUID id = SpatialIds.named("nav", zoneId, null, x * 10_000 + y, "NODE", x + ":" + y);
                ids[x][y] = id;
                nodes.add(new ZoneNavGraph.Node(id, x, y, wgs));
            }
        }
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (ids[x][y] == null) {
                    continue;
                }
                for (int[] dir : DIRS) {
                    int nx = x + dir[0];
                    int ny = y + dir[1];
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || ids[nx][ny] == null) {
                        continue;
                    }
                    if (dir[0] != 0 && dir[1] != 0
                            && (ids[x + dir[0]][y] == null || ids[x][y + dir[1]] == null)) {
                        continue;
                    }
                    double meters = (dir[0] != 0 && dir[1] != 0) ? cell * Math.sqrt(2) : cell;
                    edges.add(new ZoneNavGraph.Edge(ids[x][y], ids[nx][ny], meters));
                }
            }
        }
        return new ZoneNavGraph(zoneId, nodes, edges);
    }

    public record PathEstimate(LineString path, double meters, double minutes) {
    }
}
