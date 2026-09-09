package com.aifishing.lake.processing.render;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeorefTransformTest {

    @Test
    void pixelRoundTripPreservesKnownContourVertex() {
        GeorefTransform georef = new GeorefTransform(-78.93, 44.74, -78.91, 44.76, 1001, 801);
        double lng = -78.9215;
        double lat = 44.7512;
        double[] pixel = georef.toPixel(lng, lat);
        Point back = georef.toWgs84(pixel[0], pixel[1]);
        assertThat(back.getX()).isCloseTo(lng, within(1e-9));
        assertThat(back.getY()).isCloseTo(lat, within(1e-9));

        Point nw = georef.toWgs84(0, 0);
        assertThat(nw.getX()).isEqualTo(-78.93);
        assertThat(nw.getY()).isEqualTo(44.76);
        Point se = georef.toWgs84(1000, 800);
        assertThat(se.getX()).isEqualTo(-78.91);
        assertThat(se.getY()).isEqualTo(44.74);
        assertThat(georef.toMap().get("northUp")).isEqualTo(true);
        assertThat(georef.toMap().get("srid")).isEqualTo(4326);
    }
}
