package com.aifishing.planning.spatial;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Shared boat-neutral lake navigation raster. Adjacency is implicit (8-neighbor).
 * Diagonal steps require both orthogonal neighbors to be traversable (no corner cut).
 */
public final class LakeNavRaster {

    private static final int[][] ORTH = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final LakeNavGrid grid;
    private final boolean[][] nav;
    private final byte[][] clearanceM;
    private final LocalMetricCrs.ProjectedGeometry projected;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
    private final Map<SearchKey, List<int[]>> cellPaths = new HashMap<>();
    private int[] components;
    private double[] dist;
    private int[] parent;
    private int[] touchGen;
    private int[] seenGen;
    private int generation;

    public LakeNavRaster(
            LakeNavGrid grid,
            boolean[][] nav,
            byte[][] clearanceM,
            LocalMetricCrs.ProjectedGeometry projected
    ) {
        this.grid = grid;
        this.nav = nav;
        this.clearanceM = clearanceM;
        this.projected = projected;
    }

    public LakeNavGrid grid() {
        return grid;
    }

    public LocalMetricCrs.ProjectedGeometry projected() {
        return projected;
    }

    public List<LakeNavTiles.Packed> packTiles() {
        List<LakeNavTiles.Packed> tiles = new ArrayList<>();
        int tilesX = grid.tilesX();
        int tilesY = grid.tilesY();
        for (int tx = 0; tx < tilesX; tx++) {
            for (int ty = 0; ty < tilesY; ty++) {
                byte[] mask = LakeNavTiles.packMask(nav, grid, tx, ty);
                if (!LakeNavTiles.tileHasNavigable(mask)) {
                    continue;
                }
                tiles.add(new LakeNavTiles.Packed(
                        tx, ty, mask, LakeNavTiles.packClearance(clearanceM, grid, tx, ty)));
            }
        }
        return tiles;
    }

    public int navigableCellCount() {
        int n = 0;
        for (boolean[] col : nav) {
            for (boolean cell : col) {
                if (cell) {
                    n++;
                }
            }
        }
        return n;
    }

    public boolean traversable(int x, int y) {
        return inBounds(x, y) && nav[x][y];
    }

    public int clearanceM(int x, int y) {
        if (!inBounds(x, y) || clearanceM == null) {
            return 0;
        }
        return clearanceM[x][y] & 0xFF;
    }

    public boolean sharesCell(LakeNavRaster other, int x, int y) {
        return other != null && other.grid.equals(grid) && traversable(x, y) && other.traversable(x, y);
    }

    public Optional<LocalWaterPathEstimator.PathEstimate> shortest(Point fromWgs, Point toWgs, double cruiseKmh) {
        return shortest(fromWgs, toWgs, cruiseKmh, Double.POSITIVE_INFINITY);
    }

    public Optional<LocalWaterPathEstimator.PathEstimate> shortest(
            Point fromWgs,
            Point toWgs,
            double cruiseKmh,
            double maxMeters
    ) {
        if (fromWgs == null || toWgs == null || fromWgs.isEmpty() || toWgs.isEmpty() || projected == null) {
            return Optional.empty();
        }
        synchronized (this) {
            Point fromM = projected.toMetricPoint(fromWgs);
            Point toM = projected.toMetricPoint(toWgs);
            int[] start = snap(fromM);
            int[] goal = snap(toM);
            if (start == null || goal == null || !sameComponent(start, goal)) {
                return Optional.empty();
            }
            SearchKey key = new SearchKey(idx(start[0], start[1]), idx(goal[0], goal[1]), Double.doubleToRawLongBits(maxMeters));
            List<int[]> cells = cellPaths.get(key);
            if (cells == null && !cellPaths.containsKey(key)) {
                GenerateProfiler.current().count("astarCalls");
                long startedNs = System.nanoTime();
                cells = astar(start, goal, maxMeters);
                GenerateProfiler.current().count("astarTimeNs", Math.max(0, System.nanoTime() - startedNs));
                cellPaths.put(key, cells);
            }
            return toEstimate(cells, fromWgs, toWgs, cruiseKmh);
        }
    }

    private Optional<LocalWaterPathEstimator.PathEstimate> toEstimate(
            List<int[]> cells,
            Point fromWgs,
            Point toWgs,
            double cruiseKmh
    ) {
        if (cells == null || cells.isEmpty()) {
            return Optional.empty();
        }
        List<Coordinate> coords = new ArrayList<>();
        for (int[] cell : cells) {
            coords.add(wgsOf(cell[0], cell[1]).getCoordinate());
        }
        coords.add(0, fromWgs.getCoordinate());
        coords.add(toWgs.getCoordinate());
        if (coords.size() < 2) {
            return Optional.empty();
        }
        LineString line = factory.createLineString(coords.toArray(Coordinate[]::new));
        line.setSRID(GeoMapper.SRID);
        double meters = 0;
        for (int i = 1; i < coords.size(); i++) {
            meters += hypotM(coords.get(i - 1), coords.get(i));
        }
        double kmh = cruiseKmh <= 0 ? 6 : cruiseKmh;
        return Optional.of(new LocalWaterPathEstimator.PathEstimate(line, meters, meters / 1000.0 / kmh * 60.0));
    }

    public Optional<LocalWaterPathEstimator.PathEstimate> shortest(
            Point fromWgs,
            Point toWgs,
            LakePlanningGeometry lake,
            double cruiseKmh
    ) {
        return shortest(fromWgs, toWgs, cruiseKmh);
    }

    public static String cellKey(int[] cell) {
        if (cell == null) {
            return "";
        }
        return "c:" + cell[0] + ":" + cell[1];
    }

    public int[] cellOf(Point wgs) {
        if (wgs == null || wgs.isEmpty() || projected == null) {
            return null;
        }
        return snap(projected.toMetricPoint(wgs));
    }

    public Point wgsOf(int x, int y) {
        double mx = grid.originX() + (x + 0.5) * grid.cellSizeM();
        double my = grid.originY() + (y + 0.5) * grid.cellSizeM();
        Point metric = factory.createPoint(new Coordinate(mx, my));
        metric.setSRID(grid.utmZone() + 32600);
        return projected.toWgs84Point(metric);
    }

    private boolean sameComponent(int[] start, int[] goal) {
        ensureComponents();
        int left = components[idx(start[0], start[1])];
        int right = components[idx(goal[0], goal[1])];
        return left != 0 && left == right;
    }

    /**
     * Labels navigable cells with the same adjacency A* uses, including the
     * no-corner-cut diagonal rule. Different labels are unreachable.
     */
    private void ensureComponents() {
        if (components != null) {
            return;
        }
        synchronized (this) {
            if (components != null) {
                return;
            }
            int w = grid.widthCells();
            int h = grid.heightCells();
            int[] labels = new int[w * h];
            int next = 1;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (!nav[x][y]) {
                        continue;
                    }
                    int seed = idx(x, y);
                    if (labels[seed] != 0) {
                        continue;
                    }
                    labels[seed] = next;
                    queue.add(seed);
                    while (!queue.isEmpty()) {
                        int current = queue.removeFirst();
                        int cx = current / h;
                        int cy = current % h;
                        for (int[] dir : ORTH) {
                            markComponent(cx + dir[0], cy + dir[1], next, labels, queue);
                        }
                        for (int[] dir : DIAG) {
                            if (!traversable(cx + dir[0], cy) || !traversable(cx, cy + dir[1])) {
                                continue;
                            }
                            markComponent(cx + dir[0], cy + dir[1], next, labels, queue);
                        }
                    }
                    next++;
                }
            }
            components = labels;
        }
    }

    private void markComponent(int x, int y, int label, int[] labels, ArrayDeque<Integer> queue) {
        if (!traversable(x, y)) {
            return;
        }
        int index = idx(x, y);
        if (labels[index] != 0) {
            return;
        }
        labels[index] = label;
        queue.add(index);
    }

    private int[] snap(Point metric) {
        if (metric == null || metric.isEmpty()) {
            return null;
        }
        int x = (int) Math.floor((metric.getX() - grid.originX()) / grid.cellSizeM());
        int y = (int) Math.floor((metric.getY() - grid.originY()) / grid.cellSizeM());
        int[] best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!traversable(cx, cy)) {
                    continue;
                }
                double mx = grid.originX() + (cx + 0.5) * grid.cellSizeM();
                double my = grid.originY() + (cy + 0.5) * grid.cellSizeM();
                double d = Math.hypot(mx - metric.getX(), my - metric.getY());
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }

    private List<int[]> astar(int[] start, int[] goal, double maxMeters) {
        ensureWorkspace();
        if (generation == Integer.MAX_VALUE) {
            java.util.Arrays.fill(touchGen, 0);
            java.util.Arrays.fill(seenGen, 0);
            generation = 0;
        }
        generation++;
        int h = grid.heightCells();
        int s = idx(start[0], start[1]);
        int g = idx(goal[0], goal[1]);
        touchGen[s] = generation;
        dist[s] = 0;
        parent[s] = -1;
        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingDouble(a -> distAt(a[0]) + heuristic(a[0], g)));
        open.add(new int[]{s});
        while (!open.isEmpty()) {
            int current = open.poll()[0];
            if (seenGen[current] == generation) {
                continue;
            }
            seenGen[current] = generation;
            GenerateProfiler.current().count("astarExpandedCells");
            if (current == g) {
                break;
            }
            int cx = current / h;
            int cy = current % h;
            for (int[] dir : ORTH) {
                consider(cx, cy, dir[0], dir[1], current, open, grid.cellSizeM(), maxMeters);
            }
            for (int[] dir : DIAG) {
                int ox = cx + dir[0];
                int oy = cy;
                int px = cx;
                int py = cy + dir[1];
                if (!traversable(ox, oy) || !traversable(px, py)) {
                    continue;
                }
                consider(cx, cy, dir[0], dir[1], current, open, grid.cellSizeM() * Math.sqrt(2), maxMeters);
            }
        }
        if (touchGen[g] != generation || dist[g] == Double.POSITIVE_INFINITY) {
            return List.of();
        }
        List<int[]> cells = new ArrayList<>();
        int cursor = g;
        while (cursor >= 0) {
            cells.add(0, new int[]{cursor / h, cursor % h});
            cursor = parent[cursor];
        }
        return cells;
    }

    private void ensureWorkspace() {
        int cells = grid.widthCells() * grid.heightCells();
        if (dist != null && dist.length == cells) {
            return;
        }
        dist = new double[cells];
        parent = new int[cells];
        touchGen = new int[cells];
        seenGen = new int[cells];
        generation = 0;
    }

    private double distAt(int index) {
        return touchGen[index] == generation ? dist[index] : Double.POSITIVE_INFINITY;
    }

    private void consider(
            int cx,
            int cy,
            int dx,
            int dy,
            int current,
            PriorityQueue<int[]> open,
            double step,
            double maxMeters
    ) {
        int nx = cx + dx;
        int ny = cy + dy;
        if (!traversable(nx, ny)) {
            return;
        }
        int next = idx(nx, ny);
        double g = distAt(current) + step;
        if (g > maxMeters) {
            return;
        }
        if (g + 1e-9 < distAt(next)) {
            touchGen[next] = generation;
            dist[next] = g;
            parent[next] = current;
            open.add(new int[]{next});
        }
    }

    private double heuristic(int index, int goal) {
        int h = grid.heightCells();
        int dx = index / h - goal / h;
        int dy = index % h - goal % h;
        return Math.hypot(dx, dy) * grid.cellSizeM();
    }

    private int idx(int x, int y) {
        return x * grid.heightCells() + y;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < grid.widthCells() && y < grid.heightCells();
    }

    private record SearchKey(int start, int goal, long maxMetersBits) {
    }

    private static double hypotM(Coordinate a, Coordinate b) {
        double lat = (a.y + b.y) / 2.0;
        double dLat = (a.y - b.y) * 111_320.0;
        double dLng = (a.x - b.x) * 111_320.0 * Math.cos(Math.toRadians(lat));
        return Math.hypot(dLat, dLng);
    }
}
