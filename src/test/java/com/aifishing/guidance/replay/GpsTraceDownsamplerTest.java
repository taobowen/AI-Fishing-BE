package com.aifishing.guidance.replay;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GpsTraceDownsamplerTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void neverDropsAnchorIdsAndThinsOrdinaryPoints() {
        UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID ordinary = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID anchor = UUID.fromString("00000000-0000-4000-8000-000000000003");
        UUID last = UUID.fromString("00000000-0000-4000-8000-000000000004");

        List<GpsTraceDownsampler.GpsPoint> points = List.of(
                point(first, 44.75, -79.00),
                point(ordinary, 44.75001, -79.00001),
                point(anchor, 44.75002, -79.00002),
                point(last, 44.76, -79.01)
        );

        List<GpsTraceDownsampler.GpsPoint> kept = GpsTraceDownsampler.downsample(points, Set.of(anchor));

        assertThat(kept).extracting(GpsTraceDownsampler.GpsPoint::id)
                .contains(first, anchor, last)
                .doesNotContain(ordinary);
    }

    @Test
    void keepsDenseClusterWhenEveryPointIsAnAnchor() {
        List<GpsTraceDownsampler.GpsPoint> points = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.fromString("00000000-0000-4000-8000-00000000000" + (i + 1));
            ids.add(id);
            points.add(point(id, 44.75 + (i * 0.00001), -79.00));
        }

        List<GpsTraceDownsampler.GpsPoint> kept = GpsTraceDownsampler.downsample(points, ids);

        assertThat(kept).hasSize(5);
    }

    private static GpsTraceDownsampler.GpsPoint point(UUID id, double lat, double lng) {
        Point location = FACTORY.createPoint(new Coordinate(lng, lat));
        location.setSRID(4326);
        return new GpsTraceDownsampler.GpsPoint(id, location);
    }
}
