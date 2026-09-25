package com.aifishing.fishingtemplate.service;

import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.geo.PolygonalGeometries;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

/**
 * Write-time geometry checks for fishing template targets.
 * POINT must lie in usable lake water; PATH is clipped to water and rejected if
 * it would remain a single land-crossing path; ZONE is stored as the intersection
 * with lake water. Limits are hard 400s.
 */
@Component
public class TemplateGeometryValidator {

    public static final String GEOMETRY_INVALID = "TEMPLATE_GEOMETRY_INVALID";

    public Geometry validateAndNormalize(
            TemplateTargetKind kind,
            Geometry raw,
            LakePlanningGeometry lake
    ) {
        if (kind == null) {
            throw invalid("Template target kind is required");
        }
        if (raw == null || raw.isEmpty()) {
            throw invalid("Template target geometry is required");
        }
        if (lake == null || !lake.hasWater()) {
            throw invalid("Lake water geometry is unavailable for template validation");
        }
        return switch (kind) {
            case POINT -> validatePoint(raw, lake);
            case PATH -> validatePath(raw, lake);
            case ZONE -> validateZone(raw, lake);
        };
    }

    public void assertTargetCount(int count) {
        if (count < 0) {
            throw invalid("Template target count is invalid");
        }
        if (count > TemplateGeometryLimits.MAX_TARGETS) {
            throw invalid("Template may have at most " + TemplateGeometryLimits.MAX_TARGETS + " targets");
        }
    }

    private Geometry validatePoint(Geometry raw, LakePlanningGeometry lake) {
        if (!(raw instanceof Point point) || point.isEmpty()) {
            throw invalid("POINT targets must be a GeoJSON Point");
        }
        point.setSRID(4326);
        if (!lake.validFishingPoint(point)) {
            throw invalid("POINT must lie inside usable lake water");
        }
        return point;
    }

    private Geometry validatePath(Geometry raw, LakePlanningGeometry lake) {
        LineString line = asLineString(raw);
        assertVertexLimit(line.getNumPoints(), TemplateGeometryLimits.MAX_PATH_VERTICES, "PATH");
        Geometry clipped;
        try {
            clipped = lake.water().intersection(line);
        } catch (RuntimeException ex) {
            throw invalid("PATH could not be clipped to lake water");
        }
        if (clipped == null || clipped.isEmpty()) {
            throw invalid("PATH does not intersect usable lake water");
        }
        LineString waterPath = asSingleWaterLine(clipped);
        assertVertexLimit(waterPath.getNumPoints(), TemplateGeometryLimits.MAX_PATH_VERTICES, "PATH");
        if (crossesLandAsSinglePath(waterPath, lake)) {
            throw invalid("PATH crosses land and cannot be stored as one fishing path");
        }
        waterPath.setSRID(4326);
        return waterPath;
    }

    private Geometry validateZone(Geometry raw, LakePlanningGeometry lake) {
        Geometry polygonal = PolygonalGeometries.of(raw);
        if (polygonal == null || polygonal.isEmpty()) {
            throw invalid("ZONE targets must be a Polygon or MultiPolygon");
        }
        assertVertexLimit(vertexCount(polygonal), TemplateGeometryLimits.MAX_ZONE_VERTICES, "ZONE");
        Geometry clipped;
        try {
            clipped = PolygonalGeometries.of(lake.water().intersection(polygonal));
        } catch (RuntimeException ex) {
            throw invalid("ZONE could not be intersected with lake water");
        }
        if (clipped == null || clipped.isEmpty()) {
            throw invalid("ZONE does not intersect usable lake water");
        }
        assertVertexLimit(vertexCount(clipped), TemplateGeometryLimits.MAX_ZONE_VERTICES, "ZONE");
        clipped.setSRID(4326);
        return clipped;
    }

    private static LineString asLineString(Geometry raw) {
        if (raw instanceof LineString line && !(raw instanceof MultiLineString) && line.getNumPoints() >= 2) {
            line.setSRID(4326);
            return line;
        }
        if (raw instanceof MultiLineString multi && multi.getNumGeometries() == 1) {
            LineString line = (LineString) multi.getGeometryN(0);
            if (line.getNumPoints() >= 2) {
                line.setSRID(4326);
                return line;
            }
        }
        throw invalid("PATH targets must be a GeoJSON LineString");
    }

    private static LineString asSingleWaterLine(Geometry clipped) {
        if (clipped instanceof LineString line && !(clipped instanceof MultiLineString) && line.getNumPoints() >= 2) {
            line.setSRID(4326);
            return line;
        }
        if (clipped instanceof MultiLineString multi) {
            if (multi.getNumGeometries() != 1) {
                throw invalid("PATH crosses land; store separate water segments instead of one land-crossing path");
            }
            LineString line = (LineString) multi.getGeometryN(0);
            if (line.getNumPoints() < 2) {
                throw invalid("PATH clipped to lake water is too short");
            }
            line.setSRID(4326);
            return line;
        }
        if (clipped.getNumGeometries() == 1 && clipped.getGeometryN(0) instanceof LineString line && line.getNumPoints() >= 2) {
            line.setSRID(4326);
            return line;
        }
        throw invalid("PATH clipped to lake water is not a single fishing path");
    }

    private static boolean crossesLandAsSinglePath(LineString line, LakePlanningGeometry lake) {
        for (int i = 0; i < line.getNumPoints() - 1; i++) {
            Point from = line.getPointN(i);
            Point to = line.getPointN(i + 1);
            from.setSRID(4326);
            to.setSRID(4326);
            if (lake.landCrossing(from, to)) {
                return true;
            }
        }
        return false;
    }

    private static int vertexCount(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return 0;
        }
        return geometry.getNumPoints();
    }

    private static void assertVertexLimit(int count, int max, String kind) {
        if (count > max) {
            throw invalid(kind + " may have at most " + max + " vertices");
        }
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException(GEOMETRY_INVALID, message);
    }
}
