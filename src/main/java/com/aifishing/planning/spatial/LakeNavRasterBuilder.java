package com.aifishing.planning.spatial;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a boat-neutral lake raster with a conservative cell rule.
 * <p>
 * A cell is navigable only if a 3×3 sample inside the cell has water coverage
 * ≥ {@code navMinWaterCoverage} and island coverage ≤ {@code navMaxIslandFraction}.
 * Center-in-water alone is not enough, so thin islands are not erased and
 * narrow land necks are not invented as channels.
 * <p>
 * At 25 m, features thinner than one cell may disappear or block. Clearance is
 * a chamfer distance-to-land in metres, capped at 255, and is not boat-specific.
 */
@Component
public class LakeNavRasterBuilder {

    private static final int MAX_CELLS = 2_000_000;
    private static final double DIAG = Math.sqrt(2);

    private final LocalMetricCrs localMetricCrs;
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public LakeNavRasterBuilder(LocalMetricCrs localMetricCrs) {
        this.localMetricCrs = localMetricCrs;
    }

    public LakeNavRaster build(LakePlanningGeometry lake, PlanningProperties.Spatial spatial) {
        if (lake == null || !lake.hasWater()) {
            throw new IllegalArgumentException("Lake water geometry is required for navigation raster");
        }
        double cell = spatial.getLocalPathCellSizeM();
        double referenceLng = lake.water().getCentroid().getX();
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(lake.water(), referenceLng);
        Geometry metricWater = projected.toMetric(lake.water());
        Envelope envelope = metricWater.getEnvelopeInternal();
        envelope.expandBy(cell);
        double originX = Math.floor(envelope.getMinX() / cell) * cell;
        double originY = Math.floor(envelope.getMinY() / cell) * cell;
        int width = Math.max(1, (int) Math.ceil((envelope.getMaxX() - originX) / cell));
        int height = Math.max(1, (int) Math.ceil((envelope.getMaxY() - originY) / cell));
        while ((long) width * height > MAX_CELLS && cell < 200) {
            cell *= 1.5;
            originX = Math.floor(envelope.getMinX() / cell) * cell;
            originY = Math.floor(envelope.getMinY() / cell) * cell;
            width = Math.max(1, (int) Math.ceil((envelope.getMaxX() - originX) / cell));
            height = Math.max(1, (int) Math.ceil((envelope.getMaxY() - originY) / cell));
        }
        LakeNavGrid grid = new LakeNavGrid(
                "EPSG:" + projected.srid(),
                projected.utmZone(),
                referenceLng,
                originX,
                originY,
                cell,
                spatial.getNavTileSizeCells(),
                width,
                height
        );
        PreparedGeometry water = PreparedGeometryFactory.prepare(metricWater);
        List<PreparedGeometry> islands = new ArrayList<>();
        for (Geometry island : lake.islands()) {
            if (island == null || island.isEmpty()) {
                continue;
            }
            islands.add(PreparedGeometryFactory.prepare(projected.toMetric(island)));
        }
        boolean[][] nav = new boolean[width][height];
        double minWater = spatial.getNavMinWaterCoverage();
        double maxIsland = spatial.getNavMaxIslandFraction();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                nav[x][y] = conservativeCell(water, islands, originX, originY, cell, x, y, minWater, maxIsland);
            }
        }
        byte[][] clearance = clearanceLayer(nav, cell);
        return new LakeNavRaster(grid, nav, clearance, projected);
    }

    private boolean conservativeCell(
            PreparedGeometry water,
            List<PreparedGeometry> islands,
            double originX,
            double originY,
            double cell,
            int x,
            int y,
            double minWater,
            double maxIsland
    ) {
        int waterHits = 0;
        int islandHits = 0;
        int samples = 0;
        for (int sx = 0; sx < 3; sx++) {
            for (int sy = 0; sy < 3; sy++) {
                double mx = originX + (x + (sx + 0.5) / 3.0) * cell;
                double my = originY + (y + (sy + 0.5) / 3.0) * cell;
                Point sample = factory.createPoint(new Coordinate(mx, my));
                samples++;
                boolean onIsland = false;
                for (PreparedGeometry island : islands) {
                    if (island.covers(sample)) {
                        onIsland = true;
                        break;
                    }
                }
                if (onIsland) {
                    islandHits++;
                } else if (water.covers(sample)) {
                    waterHits++;
                }
            }
        }
        return (waterHits / (double) samples) >= minWater && (islandHits / (double) samples) <= maxIsland;
    }

    private static byte[][] clearanceLayer(boolean[][] nav, double cell) {
        int w = nav.length;
        int h = nav[0].length;
        double[][] dist = new double[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                dist[x][y] = nav[x][y] ? Double.POSITIVE_INFINITY : 0;
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!nav[x][y]) {
                    continue;
                }
                dist[x][y] = relax(dist, x, y, -1, 0, cell);
                dist[x][y] = Math.min(dist[x][y], relax(dist, x, y, 0, -1, cell));
                dist[x][y] = Math.min(dist[x][y], relax(dist, x, y, -1, -1, cell * DIAG));
                dist[x][y] = Math.min(dist[x][y], relax(dist, x, y, -1, 1, cell * DIAG));
            }
        }
        for (int x = w - 1; x >= 0; x--) {
            for (int y = h - 1; y >= 0; y--) {
                if (!nav[x][y]) {
                    continue;
                }
                dist[x][y] = Math.min(dist[x][y], relax(dist, x, y, 1, 0, cell));
                dist[x][y] = Math.min(dist[x][y], relax(dist, x, y, 0, 1, cell));
                dist[x][y] = Math.min(dist[x][y], relax(dist, x, y, 1, 1, cell * DIAG));
                dist[x][y] = Math.min(dist[x][y], relax(dist, x, y, 1, -1, cell * DIAG));
            }
        }
        byte[][] clearance = new byte[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double metres = dist[x][y];
                if (!Double.isFinite(metres)) {
                    metres = 255;
                }
                clearance[x][y] = (byte) Math.max(0, Math.min(255, (int) Math.round(metres)));
            }
        }
        return clearance;
    }

    private static double relax(double[][] dist, int x, int y, int dx, int dy, double step) {
        int nx = x + dx;
        int ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= dist.length || ny >= dist[0].length) {
            return dist[x][y];
        }
        return Math.min(dist[x][y], dist[nx][ny] + step);
    }
}
