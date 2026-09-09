package com.aifishing.lake.processing.vision;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.render.GeorefTransform;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VisionFeatureParserTest {

    private final VisionFeatureParser parser = new VisionFeatureParser(new ObjectMapper());
    private final GeorefTransform georef = new GeorefTransform(-78.93, 44.74, -78.91, 44.76, 101, 101);

    @Test
    void pixelJsonBecomesWgs84InsideBbox() {
        Point expected = georef.toWgs84(25, 40);
        String json = """
                {"features":[
                  {"type":"HUMP","geometryType":"Polygon","coordinates":[[[20,20],[40,20],[40,40],[20,40],[20,20]]],"confidence":0.8,"evidence":"nested shallow"},
                  {"type":"POINT","geometryType":"Point","coordinates":[25,40],"confidence":0.6,"evidence":"cape"}
                ]}
                """;
        List<VisionCandidate> candidates = parser.parse(json, georef);
        assertThat(candidates).hasSize(2);
        VisionCandidate hump = candidates.stream().filter(c -> c.type() == FeatureType.HUMP).findFirst().orElseThrow();
        assertThat(hump.geometry().getGeometryType()).isEqualTo("Polygon");
        assertThat(hump.geometry().getSRID()).isEqualTo(4326);
        assertThat(hump.geometry().getCentroid().getX()).isBetween(-78.93, -78.91);
        assertThat(hump.geometry().getCentroid().getY()).isBetween(44.74, 44.76);
        VisionCandidate point = candidates.stream().filter(c -> c.type() == FeatureType.POINT).findFirst().orElseThrow();
        Point parsed = (Point) point.geometry();
        assertThat(parsed.getX()).isCloseTo(expected.getX(), within(1e-9));
        assertThat(parsed.getY()).isCloseTo(expected.getY(), within(1e-9));
    }

    @Test
    void extractsJsonObjectWhenModelAddsPreamble() {
        String raw = """
                Here is the structure I see:
                ```json
                {"features":[{"type":"POINT","geometryType":"Point","coordinates":[25,40],"confidence":0.6,"evidence":"cape"}]}
                ```
                """;
        List<VisionCandidate> candidates = parser.parse(raw, georef);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).type()).isEqualTo(FeatureType.POINT);
    }
}
