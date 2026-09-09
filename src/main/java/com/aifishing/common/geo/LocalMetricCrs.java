package com.aifishing.common.geo;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Component;

/**
 * Transforms WGS84 (EPSG:4326) geometries into a local UTM metric CRS for meter-accurate
 * distance, buffer, and closest-point operations, then back to 4326 for persistence.
 */
@Component
public class LocalMetricCrs {

    private final CRSFactory crsFactory = new CRSFactory();
    private final CoordinateTransformFactory transformFactory = new CoordinateTransformFactory();

    public ProjectedGeometry project(Geometry wgs84, double longitudeDeg) {
        if (wgs84 == null) {
            return null;
        }
        int zone = utmZone(longitudeDeg);
        int srid = 32600 + zone;
        CoordinateTransform toMetric = transformFactory.createTransform(
                crsFactory.createFromParameters("WGS84", "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs"),
                crsFactory.createFromParameters("UTM" + zone, utmParams(zone))
        );
        CoordinateTransform toWgs = transformFactory.createTransform(
                crsFactory.createFromParameters("UTM" + zone, utmParams(zone)),
                crsFactory.createFromParameters("WGS84", "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs")
        );
        Geometry metric = transform(wgs84, toMetric, srid);
        return new ProjectedGeometry(metric, srid, zone, toMetric, toWgs);
    }

    public static int utmZone(double longitudeDeg) {
        int zone = (int) Math.floor((longitudeDeg + 180.0) / 6.0) + 1;
        return Math.max(1, Math.min(60, zone));
    }

    private static String utmParams(int zone) {
        return "+proj=utm +zone=" + zone + " +datum=WGS84 +units=m +no_defs";
    }

    static Geometry transform(Geometry geometry, CoordinateTransform transform, int srid) {
        Geometry copy = geometry.copy();
        copy.apply(new CoordinateSequenceFilter() {
            @Override
            public void filter(CoordinateSequence seq, int i) {
                ProjCoordinate from = new ProjCoordinate(seq.getX(i), seq.getY(i));
                ProjCoordinate to = new ProjCoordinate();
                transform.transform(from, to);
                seq.setOrdinate(i, 0, to.x);
                seq.setOrdinate(i, 1, to.y);
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public boolean isGeometryChanged() {
                return true;
            }
        });
        copy.setSRID(srid);
        copy.geometryChanged();
        return copy;
    }

    public record ProjectedGeometry(
            Geometry geometry,
            int srid,
            int utmZone,
            CoordinateTransform toMetric,
            CoordinateTransform toWgs84
    ) {
        public Geometry toMetric(Geometry wgs84) {
            return LocalMetricCrs.transform(wgs84, toMetric, srid);
        }

        public Point toMetricPoint(Point wgs84) {
            return (Point) toMetric(wgs84);
        }

        public Geometry toWgs84(Geometry metric) {
            if (metric == null) {
                return null;
            }
            return LocalMetricCrs.transform(metric, toWgs84, GeoMapper.SRID);
        }

        public Point toWgs84Point(Point metric) {
            return (Point) toWgs84(metric);
        }
    }
}
