package com.aifishing.lake.processing.hybrid;

import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.ContourTopology;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.lake.processing.geo.FeatureGeometryMatch;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

@Component
public class VisionCandidateValidator {

    private final ProcessingProperties properties;
    private final ContourTopology topology;

    public VisionCandidateValidator(ProcessingProperties properties, ContourTopology topology) {
        this.properties = properties;
        this.topology = topology;
    }

    public boolean accept(LakeFeature feature, AnalysisContext context) {
        Geometry geometry = feature.getGeometry();
        if (geometry == null || geometry.isEmpty()) {
            return false;
        }
        if (context.lakeBoundary() != null && !geometry.intersects(context.lakeBoundary())) {
            return false;
        }
        return switch (feature.getType()) {
            case HUMP -> acceptHumpOrBasin(geometry, context, true);
            case BASIN -> acceptHumpOrBasin(geometry, context, false);
            case DROP_OFF -> acceptDropOff(geometry, context);
            case FLAT -> acceptFlat(geometry, context);
            case POINT -> acceptPoint(geometry, context);
            case ISLAND_EDGE -> acceptIslandEdge(geometry, context);
        };
    }

    public Geometry snap(LakeFeature feature, AnalysisContext context) {
        Geometry geometry = feature.getGeometry();
        if (geometry == null) {
            return null;
        }
        return switch (feature.getType()) {
            case HUMP, BASIN, FLAT, ISLAND_EDGE -> snapPolygon(geometry, context);
            default -> geometry;
        };
    }

    private boolean acceptHumpOrBasin(Geometry geometry, AnalysisContext context, boolean hump) {
        if (tooTiny(geometry)) {
            return false;
        }
        if (context.closedContours().isEmpty()) {
            return false;
        }
        for (ContourTopology.ClosedContour child : context.closedContours()) {
            if (FeatureGeometryMatch.iou(geometry, child.polygon()) < 0.2
                    && !geometry.intersects(child.polygon())) {
                continue;
            }
            ContourTopology.ClosedContour parent = topology.parentOf(child, context.closedContours());
            if (parent == null) {
                continue;
            }
            double relief = hump ? parent.depthM() - child.depthM() : child.depthM() - parent.depthM();
            double minRelief = hump ? properties.getHumpMinReliefM() : properties.getBasinMinReliefM();
            if (relief >= minRelief) {
                return true;
            }
        }
        return localRelief(geometry, context, hump);
    }

    private boolean localRelief(Geometry geometry, AnalysisContext context, boolean hump) {
        Double min = null;
        Double max = null;
        for (BathymetryContour contour : context.contours()) {
            if (contour.getGeometry() == null || contour.getDepthM() == null || !geometry.intersects(contour.getGeometry())) {
                continue;
            }
            double depth = contour.getDepthM().doubleValue();
            min = min == null ? depth : Math.min(min, depth);
            max = max == null ? depth : Math.max(max, depth);
        }
        if (min == null || max == null) {
            return false;
        }
        double minRelief = hump ? properties.getHumpMinReliefM() : properties.getBasinMinReliefM();
        return (max - min) >= minRelief;
    }

    private boolean acceptDropOff(Geometry geometry, AnalysisContext context) {
        if (GeoMetrics.lengthM(geometry) < 20) {
            return false;
        }
        BathymetryContour nearest = null;
        double nearestM = Double.POSITIVE_INFINITY;
        for (BathymetryContour contour : context.contours()) {
            if (contour.getGeometry() == null || contour.getDepthM() == null) {
                continue;
            }
            double distance = GeoMetrics.distanceM(geometry, contour.getGeometry());
            if (distance < nearestM) {
                nearestM = distance;
                nearest = contour;
            }
        }
        if (nearest == null || nearestM > properties.getDropoffMaxSpacingM()) {
            return false;
        }
        for (BathymetryContour other : context.contours()) {
            if (other == nearest || other.getGeometry() == null || other.getDepthM() == null) {
                continue;
            }
            if (other.getDepthM().compareTo(nearest.getDepthM()) == 0) {
                continue;
            }
            double spacing = GeoMetrics.distanceM(nearest.getGeometry(), other.getGeometry());
            if (spacing <= 0 || spacing > properties.getDropoffMaxSpacingM()) {
                continue;
            }
            double relief = Math.abs(other.getDepthM().doubleValue() - nearest.getDepthM().doubleValue());
            if (relief / spacing >= properties.getDropoffMinGradient()) {
                return true;
            }
        }
        return false;
    }

    private boolean acceptFlat(Geometry geometry, AnalysisContext context) {
        if (GeoMetrics.areaM2(geometry) < properties.getFlatMinAreaM2()) {
            return false;
        }
        ContourTopology.ClosedContour best = null;
        double bestIou = 0;
        for (ContourTopology.ClosedContour closed : context.closedContours()) {
            double iou = FeatureGeometryMatch.iou(geometry, closed.polygon());
            if (iou > bestIou) {
                bestIou = iou;
                best = closed;
            }
        }
        if (best == null || bestIou < 0.2) {
            return false;
        }
        ContourTopology.ClosedContour parent = topology.parentOf(best, context.closedContours());
        double spacing;
        double relief;
        if (parent != null) {
            spacing = Math.max(1.0, GeoMetrics.distanceM(best.polygon().getExteriorRing(), parent.polygon().getExteriorRing()));
            relief = Math.abs(parent.depthM() - best.depthM());
        } else if (context.sourceQuality().meanContourSpacingM() != null) {
            spacing = Math.max(1.0, context.sourceQuality().meanContourSpacingM());
            relief = 0;
        } else {
            return false;
        }
        return relief / spacing <= properties.getFlatMaxGradient();
    }

    private boolean acceptPoint(Geometry geometry, AnalysisContext context) {
        double nearest = Double.POSITIVE_INFINITY;
        for (LakeWaterway shoreline : context.shorelines()) {
            if (shoreline.getGeometry() == null) {
                continue;
            }
            nearest = Math.min(nearest, GeoMetrics.distanceM(geometry, shoreline.getGeometry()));
        }
        return nearest <= properties.getPointWindowM();
    }

    private boolean acceptIslandEdge(Geometry geometry, AnalysisContext context) {
        if (tooTiny(geometry) && GeoMetrics.lengthM(geometry) < Math.sqrt(properties.getIslandMinAreaM2()) * 4) {
            return false;
        }
        for (LakeWaterway island : context.islands()) {
            if (island.getGeometry() != null && geometry.intersects(island.getGeometry())) {
                return true;
            }
        }
        return false;
    }

    private Geometry snapPolygon(Geometry geometry, AnalysisContext context) {
        ContourTopology.ClosedContour best = null;
        double bestIou = 0;
        for (ContourTopology.ClosedContour closed : context.closedContours()) {
            double iou = FeatureGeometryMatch.iou(geometry, closed.polygon());
            if (iou > bestIou) {
                bestIou = iou;
                best = closed;
            }
        }
        if (best != null && bestIou >= FeatureGeometryMatch.POLYGON_IOU) {
            return best.polygon();
        }
        return geometry;
    }

    private boolean tooTiny(Geometry geometry) {
        String type = geometry.getGeometryType();
        if ("Polygon".equals(type) || "MultiPolygon".equals(type)) {
            return GeoMetrics.areaM2(geometry) < properties.getMinClosedAreaM2();
        }
        return false;
    }
}
