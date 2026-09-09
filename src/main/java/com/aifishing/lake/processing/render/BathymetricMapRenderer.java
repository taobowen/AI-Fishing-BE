package com.aifishing.lake.processing.render;

import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.VisionProperties;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class BathymetricMapRenderer {

    private final VisionProperties visionProperties;
    private final ProcessingProperties processingProperties;

    public BathymetricMapRenderer(VisionProperties visionProperties, ProcessingProperties processingProperties) {
        this.visionProperties = visionProperties;
        this.processingProperties = processingProperties;
    }

    public List<RenderedTile> render(AnalysisContext context) {
        Envelope envelope = envelope(context);
        if (envelope == null || envelope.isNull()) {
            throw new IllegalStateException("No geometry available to render a bathymetric map");
        }
        double lat = (envelope.getMinY() + envelope.getMaxY()) / 2.0;
        double widthM = envelope.getWidth() * GeoMetrics.metersPerDegreeLng(lat);
        double heightM = envelope.getHeight() * GeoMetrics.metersPerDegreeLat();
        int maxPx = Math.max(256, visionProperties.getMaxImagePx());
        double metersPerPixel = Math.max(widthM, heightM) / maxPx;
        List<Envelope> tiles = new ArrayList<>();
        if (metersPerPixel <= visionProperties.getMaxMetersPerPixel()) {
            tiles.add(envelope);
        } else {
            double tileM = Math.min(processingProperties.getTileSizeM() * 8, maxPx * visionProperties.getMaxMetersPerPixel());
            double overlapM = visionProperties.getTileOverlapM();
            double stepLng = (tileM - overlapM) / GeoMetrics.metersPerDegreeLng(lat);
            double stepLat = (tileM - overlapM) / GeoMetrics.metersPerDegreeLat();
            double tileLng = tileM / GeoMetrics.metersPerDegreeLng(lat);
            double tileLat = tileM / GeoMetrics.metersPerDegreeLat();
            int row = 0;
            for (double minLat = envelope.getMinY(); minLat < envelope.getMaxY() - 1e-9; minLat += stepLat) {
                int col = 0;
                for (double minLng = envelope.getMinX(); minLng < envelope.getMaxX() - 1e-9; minLng += stepLng) {
                    tiles.add(new Envelope(
                            minLng,
                            Math.min(envelope.getMaxX(), minLng + tileLng),
                            minLat,
                            Math.min(envelope.getMaxY(), minLat + tileLat)
                    ));
                    col++;
                    if (col > 40) {
                        break;
                    }
                }
                row++;
                if (row > 40) {
                    break;
                }
            }
        }
        List<RenderedTile> rendered = new ArrayList<>();
        int index = 0;
        for (Envelope tile : tiles) {
            int col = index % Math.max(1, (int) Math.ceil(envelope.getWidth() / Math.max(tile.getWidth(), 1e-9)));
            int row = index / Math.max(1, (int) Math.ceil(envelope.getWidth() / Math.max(tile.getWidth(), 1e-9)));
            rendered.add(renderTile(context, tile, row, col));
            index++;
        }
        return rendered;
    }

    public RenderedTile renderSingle(AnalysisContext context, Envelope envelope) {
        return renderTile(context, envelope, 0, 0);
    }

    private RenderedTile renderTile(AnalysisContext context, Envelope envelope, int row, int col) {
        double lat = (envelope.getMinY() + envelope.getMaxY()) / 2.0;
        double widthM = envelope.getWidth() * GeoMetrics.metersPerDegreeLng(lat);
        double heightM = envelope.getHeight() * GeoMetrics.metersPerDegreeLat();
        int maxPx = Math.max(256, visionProperties.getMaxImagePx());
        int widthPx;
        int heightPx;
        if (widthM >= heightM) {
            widthPx = maxPx;
            heightPx = Math.max(64, (int) Math.round(maxPx * (heightM / Math.max(widthM, 1))));
        } else {
            heightPx = maxPx;
            widthPx = Math.max(64, (int) Math.round(maxPx * (widthM / Math.max(heightM, 1))));
        }
        GeorefTransform georef = new GeorefTransform(
                envelope.getMinX(),
                envelope.getMinY(),
                envelope.getMaxX(),
                envelope.getMaxY(),
                widthPx,
                heightPx
        );
        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(12, 28, 48));
        g.fillRect(0, 0, widthPx, heightPx);
        if (context.lakeBoundary() != null) {
            g.setColor(new Color(18, 72, 118));
            fillGeometry(g, context.lakeBoundary(), georef);
        }
        for (LakeWaterway island : context.islands()) {
            if (island.getGeometry() == null) {
                continue;
            }
            g.setColor(new Color(70, 90, 55));
            fillGeometry(g, island.getGeometry(), georef);
        }
        double minDepth = 0;
        double maxDepth = 1;
        for (BathymetryContour contour : context.contours()) {
            if (contour.getDepthM() != null) {
                maxDepth = Math.max(maxDepth, contour.getDepthM().doubleValue());
            }
        }
        for (BathymetryContour contour : context.contours()) {
            if (contour.getGeometry() == null || contour.getDepthM() == null) {
                continue;
            }
            double t = (contour.getDepthM().doubleValue() - minDepth) / Math.max(0.001, maxDepth - minDepth);
            g.setColor(depthColor(t));
            g.setStroke(new BasicStroke(1.4f));
            drawGeometry(g, contour.getGeometry(), georef);
        }
        g.setColor(new Color(230, 230, 210));
        g.setStroke(new BasicStroke(2.0f));
        for (LakeWaterway shoreline : context.shorelines()) {
            if (shoreline.getGeometry() != null) {
                drawGeometry(g, shoreline.getGeometry(), georef);
            }
        }
        drawLegend(g, widthPx, heightPx, minDepth, maxDepth);
        g.dispose();
        return new RenderedTile(row, col, image, georef, toPng(image));
    }

    private Envelope envelope(AnalysisContext context) {
        Envelope envelope = new Envelope();
        expand(envelope, context.lakeBoundary());
        for (BathymetryContour contour : context.contours()) {
            expand(envelope, contour.getGeometry());
        }
        for (LakeWaterway waterway : context.shorelines()) {
            expand(envelope, waterway.getGeometry());
        }
        for (LakeWaterway waterway : context.islands()) {
            expand(envelope, waterway.getGeometry());
        }
        if (envelope.isNull() && context.lake().getCentroid() != null) {
            Point c = context.lake().getCentroid();
            envelope.expandToInclude(c.getX() - 0.01, c.getY() - 0.01);
            envelope.expandToInclude(c.getX() + 0.01, c.getY() + 0.01);
        }
        if (!envelope.isNull()) {
            envelope.expandBy(envelope.getWidth() * 0.04, envelope.getHeight() * 0.04);
        }
        return envelope;
    }

    private void expand(Envelope envelope, Geometry geometry) {
        if (geometry != null && !geometry.isEmpty()) {
            envelope.expandToInclude(geometry.getEnvelopeInternal());
        }
    }

    private void fillGeometry(Graphics2D g, Geometry geometry, GeorefTransform georef) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            Path2D path = path(part, georef);
            if (path != null) {
                g.fill(path);
            }
        }
    }

    private void drawGeometry(Graphics2D g, Geometry geometry, GeorefTransform georef) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            Path2D path = path(part, georef);
            if (path != null) {
                g.draw(path);
            }
        }
    }

    private Path2D path(Geometry geometry, GeorefTransform georef) {
        Coordinate[] coordinates;
        if (geometry instanceof LineString lineString) {
            coordinates = lineString.getCoordinates();
        } else if ("Polygon".equals(geometry.getGeometryType())) {
            coordinates = ((org.locationtech.jts.geom.Polygon) geometry).getExteriorRing().getCoordinates();
        } else {
            coordinates = geometry.getCoordinates();
        }
        if (coordinates.length < 2) {
            return null;
        }
        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (Coordinate coordinate : coordinates) {
            double[] px = georef.toPixel(coordinate.x, coordinate.y);
            if (first) {
                path.moveTo(px[0], px[1]);
                first = false;
            } else {
                path.lineTo(px[0], px[1]);
            }
        }
        return path;
    }

    private void drawLegend(Graphics2D g, int widthPx, int heightPx, double minDepth, double maxDepth) {
        int legendWidth = Math.min(180, Math.max(90, widthPx / 6));
        int legendHeight = Math.min(90, Math.max(48, heightPx / 8));
        int x = 12;
        int y = heightPx - legendHeight - 12;
        g.setColor(new Color(8, 16, 28, 210));
        g.fillRect(x, y, legendWidth, legendHeight);
        g.setColor(new Color(230, 230, 210));
        g.drawString("N", x + legendWidth - 18, y + 16);
        g.drawLine(x + legendWidth - 14, y + 20, x + legendWidth - 14, y + 34);
        g.drawString("shallow", x + 8, y + 18);
        g.drawString("deep", x + 8, y + legendHeight - 10);
        int barX = x + 8;
        int barY = y + 24;
        int barW = legendWidth - 40;
        int barH = 10;
        for (int i = 0; i < barW; i++) {
            g.setColor(depthColor(i / (double) Math.max(1, barW - 1)));
            g.drawLine(barX + i, barY, barX + i, barY + barH);
        }
        g.setColor(new Color(230, 230, 210));
        g.drawString(String.format("%.0f–%.0fm", minDepth, maxDepth), x + 8, y + 48);
        g.drawString("EPSG:4326 N-up", x + 8, y + 62);
    }

    private Color depthColor(double t) {
        double clamped = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(20 + 200 * clamped);
        int g = (int) Math.round(180 - 80 * clamped);
        int b = (int) Math.round(220 - 160 * clamped);
        return new Color(r, g, b);
    }

    private byte[] toPng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode bathymetric map", ex);
        }
    }
}
