package com.aifishing.lake.ingestion.geo;

import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.IngestionAccessProperties;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

@Component
public class AccessLakeAssociator {

    private final LakeWaterGeometry lakeWaterGeometry;
    private final LocalMetricCrs localMetricCrs;
    private final IngestionAccessProperties properties;

    public AccessLakeAssociator(
            LakeWaterGeometry lakeWaterGeometry,
            LocalMetricCrs localMetricCrs,
            IngestionAccessProperties properties
    ) {
        this.lakeWaterGeometry = lakeWaterGeometry;
        this.localMetricCrs = localMetricCrs;
        this.properties = properties;
    }

    public Association evaluate(Lake lake, Point point) {
        Geometry water = lakeWaterGeometry.unionedWater(lake);
        if (water == null || water.isEmpty() || point == null || point.isEmpty()) {
            return Association.rejected();
        }
        double longitude = lake.getCentroid() != null ? lake.getCentroid().getX() : point.getX();
        LocalMetricCrs.ProjectedGeometry projected = localMetricCrs.project(water, longitude);
        Geometry metricWater = projected.geometry();
        Point metricPoint = projected.toMetricPoint(point);
        double distanceM = metricWater.covers(metricPoint) ? 0.0 : metricWater.distance(metricPoint);
        boolean associated = distanceM <= properties.getMaxLakeAssociationMeters();
        return new Association(associated, distanceM);
    }

    public record Association(boolean associated, double distanceMeters) {
        static Association rejected() {
            return new Association(false, Double.POSITIVE_INFINITY);
        }
    }
}
