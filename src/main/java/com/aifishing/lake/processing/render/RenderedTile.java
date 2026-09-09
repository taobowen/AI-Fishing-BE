package com.aifishing.lake.processing.render;

import java.awt.image.BufferedImage;

public record RenderedTile(
        int row,
        int col,
        BufferedImage image,
        GeorefTransform georef,
        byte[] png
) {
}
