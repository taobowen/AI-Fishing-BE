package com.aifishing.lake.ingestion.processor;

import com.aifishing.common.geo.GeoMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Component;

@Component
public class CrsTransformer {

    private final CoordinateTransform nad83ToWgs84;

    public CrsTransformer() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateTransformFactory transformFactory = new CoordinateTransformFactory();
        this.nad83ToWgs84 = transformFactory.createTransform(
                crsFactory.createFromParameters("NAD83", "+proj=longlat +ellps=GRS80 +datum=NAD83 +no_defs"),
                crsFactory.createFromParameters("WGS84", "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs")
        );
    }

    public Geometry toWgs84(Geometry geometry, Integer sourceSrid) {
        if (geometry == null) {
            return null;
        }
        int srid = sourceSrid != null ? sourceSrid : geometry.getSRID();
        if (srid == 0 || srid == GeoMapper.SRID) {
            geometry.setSRID(GeoMapper.SRID);
            return geometry;
        }
        if (srid != 4269) {
            throw new IllegalArgumentException("Unsupported source SRID " + srid);
        }
        Geometry copy = geometry.copy();
        copy.apply((CoordinateFilter) coordinate -> {
            ProjCoordinate from = new ProjCoordinate(coordinate.x, coordinate.y);
            ProjCoordinate to = new ProjCoordinate();
            nad83ToWgs84.transform(from, to);
            coordinate.setCoordinate(new Coordinate(to.x, to.y));
        });
        copy.setSRID(GeoMapper.SRID);
        copy.geometryChanged();
        return copy;
    }
}
