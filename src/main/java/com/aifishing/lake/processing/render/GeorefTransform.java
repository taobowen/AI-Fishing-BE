package com.aifishing.lake.processing.render;

import com.aifishing.common.geo.GeoMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.LinkedHashMap;
import java.util.Map;

public record GeorefTransform(
        double minLng,
        double minLat,
        double maxLng,
        double maxLat,
        int widthPx,
        int heightPx
) {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    public Point toWgs84(double pixelX, double pixelY) {
        double lng = minLng + (pixelX / Math.max(1, widthPx - 1)) * (maxLng - minLng);
        double lat = maxLat - (pixelY / Math.max(1, heightPx - 1)) * (maxLat - minLat);
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        return point;
    }

    public double[] toPixel(double lng, double lat) {
        double x = (lng - minLng) / (maxLng - minLng) * Math.max(1, widthPx - 1);
        double y = (maxLat - lat) / (maxLat - minLat) * Math.max(1, heightPx - 1);
        return new double[]{x, y};
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("minLng", minLng);
        map.put("minLat", minLat);
        map.put("maxLng", maxLng);
        map.put("maxLat", maxLat);
        map.put("widthPx", widthPx);
        map.put("heightPx", heightPx);
        map.put("northUp", true);
        map.put("srid", GeoMapper.SRID);
        return map;
    }
}
