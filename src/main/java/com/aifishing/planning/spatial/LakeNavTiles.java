package com.aifishing.planning.spatial;

import com.aifishing.common.geo.LocalMetricCrs;

import java.util.List;

/**
 * Bit-packed tile codec for {@link LakeNavRaster}. Bit i of the mask is
 * local cell (i % tileSize, i / tileSize). Clearance is one byte per cell (0–255 m).
 */
public final class LakeNavTiles {

    private LakeNavTiles() {
    }

    public record Packed(int tileX, int tileY, byte[] mask, byte[] clearance) {
    }

    public static byte[] packMask(boolean[][] nav, LakeNavGrid grid, int tileX, int tileY) {
        int size = grid.tileSizeCells();
        byte[] mask = new byte[(size * size + 7) / 8];
        int x0 = tileX * size;
        int y0 = tileY * size;
        for (int ly = 0; ly < size; ly++) {
            int gy = y0 + ly;
            if (gy < 0 || gy >= grid.heightCells()) {
                continue;
            }
            for (int lx = 0; lx < size; lx++) {
                int gx = x0 + lx;
                if (gx < 0 || gx >= grid.widthCells() || !nav[gx][gy]) {
                    continue;
                }
                int bit = ly * size + lx;
                mask[bit / 8] |= (byte) (1 << (bit % 8));
            }
        }
        return mask;
    }

    public static byte[] packClearance(byte[][] clearance, LakeNavGrid grid, int tileX, int tileY) {
        int size = grid.tileSizeCells();
        byte[] out = new byte[size * size];
        int x0 = tileX * size;
        int y0 = tileY * size;
        for (int ly = 0; ly < size; ly++) {
            int gy = y0 + ly;
            if (gy < 0 || gy >= grid.heightCells()) {
                continue;
            }
            for (int lx = 0; lx < size; lx++) {
                int gx = x0 + lx;
                if (gx < 0 || gx >= grid.widthCells()) {
                    continue;
                }
                out[ly * size + lx] = clearance[gx][gy];
            }
        }
        return out;
    }

    public static boolean tileHasNavigable(byte[] mask) {
        if (mask == null) {
            return false;
        }
        for (byte b : mask) {
            if (b != 0) {
                return true;
            }
        }
        return false;
    }

    public static LakeNavRaster assemble(
            LakeNavGrid grid,
            List<Packed> tiles,
            LocalMetricCrs.ProjectedGeometry projected
    ) {
        boolean[][] nav = new boolean[grid.widthCells()][grid.heightCells()];
        byte[][] clearance = new byte[grid.widthCells()][grid.heightCells()];
        int size = grid.tileSizeCells();
        if (tiles != null) {
            for (Packed tile : tiles) {
                unpack(nav, clearance, grid, size, tile);
            }
        }
        return new LakeNavRaster(grid, nav, clearance, projected);
    }

    private static void unpack(
            boolean[][] nav,
            byte[][] clearance,
            LakeNavGrid grid,
            int size,
            Packed tile
    ) {
        int x0 = tile.tileX() * size;
        int y0 = tile.tileY() * size;
        byte[] mask = tile.mask();
        byte[] clear = tile.clearance();
        for (int ly = 0; ly < size; ly++) {
            int gy = y0 + ly;
            if (gy < 0 || gy >= grid.heightCells()) {
                continue;
            }
            for (int lx = 0; lx < size; lx++) {
                int gx = x0 + lx;
                if (gx < 0 || gx >= grid.widthCells()) {
                    continue;
                }
                int bit = ly * size + lx;
                if (mask != null && bit / 8 < mask.length && ((mask[bit / 8] >> (bit % 8)) & 1) == 1) {
                    nav[gx][gy] = true;
                }
                if (clear != null && bit < clear.length) {
                    clearance[gx][gy] = clear[bit];
                }
            }
        }
    }
}
