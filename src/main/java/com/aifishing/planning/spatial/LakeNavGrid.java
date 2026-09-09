package com.aifishing.planning.spatial;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit metric grid for one SpatialPlanningSnapshot. Tiles are addressed as
 * (tileX, tileY) against this origin — never lon/lat.
 * <p>
 * 25 m cells cannot resolve islands or channels thinner than about one cell;
 * those features may vanish or block depending on conservative coverage.
 */
public record LakeNavGrid(
        String crs,
        int utmZone,
        double referenceLng,
        double originX,
        double originY,
        double cellSizeM,
        int tileSizeCells,
        int widthCells,
        int heightCells
) {
    public static final int DEFAULT_TILE_SIZE = 128;

    public int tileX(int cellX) {
        return Math.floorDiv(cellX, tileSizeCells);
    }

    public int tileY(int cellY) {
        return Math.floorDiv(cellY, tileSizeCells);
    }

    public int localX(int cellX) {
        int rem = cellX % tileSizeCells;
        return rem < 0 ? rem + tileSizeCells : rem;
    }

    public int localY(int cellY) {
        int rem = cellY % tileSizeCells;
        return rem < 0 ? rem + tileSizeCells : rem;
    }

    public int tilesX() {
        return (int) Math.ceil(widthCells / (double) tileSizeCells);
    }

    public int tilesY() {
        return (int) Math.ceil(heightCells / (double) tileSizeCells);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("crs", crs);
        out.put("utmZone", utmZone);
        out.put("referenceLng", referenceLng);
        out.put("originX", originX);
        out.put("originY", originY);
        out.put("cellSizeM", cellSizeM);
        out.put("tileSizeCells", tileSizeCells);
        out.put("widthCells", widthCells);
        out.put("heightCells", heightCells);
        return out;
    }

    public static LakeNavGrid fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return new LakeNavGrid(
                String.valueOf(raw.getOrDefault("crs", "")),
                ((Number) raw.getOrDefault("utmZone", 0)).intValue(),
                ((Number) raw.getOrDefault("referenceLng", 0)).doubleValue(),
                ((Number) raw.getOrDefault("originX", 0)).doubleValue(),
                ((Number) raw.getOrDefault("originY", 0)).doubleValue(),
                ((Number) raw.getOrDefault("cellSizeM", 25)).doubleValue(),
                ((Number) raw.getOrDefault("tileSizeCells", DEFAULT_TILE_SIZE)).intValue(),
                ((Number) raw.getOrDefault("widthCells", 0)).intValue(),
                ((Number) raw.getOrDefault("heightCells", 0)).intValue()
        );
    }
}
