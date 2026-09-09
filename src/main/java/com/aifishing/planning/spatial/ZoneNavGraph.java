package com.aifishing.planning.spatial;

import com.aifishing.common.geo.GeoMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Sparse local navigable-water graph for one PhysicalZone. Shortest path is Dijkstra
 * on this already-built graph — never a new raster.
 */
public final class ZoneNavGraph {

    private final UUID zoneId;
    private final List<Node> nodes;
    private final Map<UUID, Integer> indexById = new HashMap<>();
    private final List<List<Edge>> adjacency;

    private final List<Edge> edges;

    public ZoneNavGraph(UUID zoneId, List<Node> nodes, List<Edge> edges) {
        this.zoneId = zoneId;
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.adjacency = new ArrayList<>();
        for (int i = 0; i < this.nodes.size(); i++) {
            indexById.put(this.nodes.get(i).id(), i);
            adjacency.add(new ArrayList<>());
        }
        for (Edge edge : edges) {
            Integer from = indexById.get(edge.fromId());
            Integer to = indexById.get(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            adjacency.get(from).add(new Edge(edge.fromId(), edge.toId(), edge.meters()));
        }
    }

    public UUID zoneId() {
        return zoneId;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public int theoreticalFullPairCount() {
        int n = nodes.size();
        return n <= 1 ? 0 : n * (n - 1);
    }

    public Optional<LocalWaterPathEstimator.PathEstimate> shortest(Point from, Point to, double cruiseKmh) {
        int start = nearest(from);
        int goal = nearest(to);
        if (start < 0 || goal < 0) {
            return Optional.empty();
        }
        return shortest(start, goal, from, to, cruiseKmh);
    }

    public Optional<LocalWaterPathEstimator.PathEstimate> shortest(
            int start,
            int goal,
            Point from,
            Point to,
            double cruiseKmh
    ) {
        if (start < 0 || goal < 0 || start >= nodes.size() || goal >= nodes.size()) {
            return Optional.empty();
        }
        double[] dist = new double[nodes.size()];
        int[] parent = new int[nodes.size()];
        java.util.Arrays.fill(dist, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(parent, -1);
        dist[start] = 0;
        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingDouble(a -> dist[a[0]]));
        open.add(new int[]{start});
        boolean[] seen = new boolean[nodes.size()];
        while (!open.isEmpty()) {
            int current = open.poll()[0];
            if (seen[current]) {
                continue;
            }
            seen[current] = true;
            if (current == goal) {
                break;
            }
            for (Edge edge : adjacency.get(current)) {
                Integer next = indexById.get(edge.toId());
                if (next == null) {
                    continue;
                }
                double g = dist[current] + edge.meters();
                if (g + 1e-9 < dist[next]) {
                    dist[next] = g;
                    parent[next] = current;
                    open.add(new int[]{next});
                }
            }
        }
        if (dist[goal] == Double.POSITIVE_INFINITY) {
            return Optional.empty();
        }
        List<Coordinate> coords = new ArrayList<>();
        int cursor = goal;
        while (cursor >= 0) {
            coords.add(0, nodes.get(cursor).point().getCoordinate());
            cursor = parent[cursor];
        }
        if (from != null) {
            coords.add(0, from.getCoordinate());
        }
        if (to != null) {
            coords.add(to.getCoordinate());
        }
        if (coords.size() < 2) {
            return Optional.empty();
        }
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
        LineString line = factory.createLineString(coords.toArray(Coordinate[]::new));
        line.setSRID(GeoMapper.SRID);
        double kmh = cruiseKmh <= 0 ? 6 : cruiseKmh;
        double minutes = dist[goal] / 1000.0 / kmh * 60.0;
        return Optional.of(new LocalWaterPathEstimator.PathEstimate(line, dist[goal], minutes));
    }

    public int nearest(Point point) {
        if (point == null || nodes.isEmpty()) {
            return -1;
        }
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < nodes.size(); i++) {
            double d = hypotM(point.getCoordinate(), nodes.get(i).point().getCoordinate());
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public UUID nearestNodeId(Point point) {
        int idx = nearest(point);
        return idx < 0 ? null : nodes.get(idx).id();
    }

    private static double hypotM(Coordinate a, Coordinate b) {
        double lat = (a.y + b.y) / 2.0;
        double dLat = (a.y - b.y) * 111_320.0;
        double dLng = (a.x - b.x) * 111_320.0 * Math.cos(Math.toRadians(lat));
        return Math.hypot(dLat, dLng);
    }

    public record Node(UUID id, int cellX, int cellY, Point point) {
    }

    public record Edge(UUID fromId, UUID toId, double meters) {
    }
}
