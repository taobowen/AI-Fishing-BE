package com.aifishing.lake.processing.benchmark;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkScorerTest {

    private final FeatureMatcher matcher = new FeatureMatcher();
    private final BenchmarkScorer scorer = new BenchmarkScorer(matcher);
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Test
    void scoresSyntheticGoldWithoutLiveHeadFile() throws Exception {
        List<GoldFeature> gold = loadSyntheticGold();
        LakeFeature hump = feature(FeatureType.HUMP, gold.get(0).geometry());
        LakeFeature extra = feature(FeatureType.FLAT, factory.createPolygon(factory.createLinearRing(new Coordinate[]{
                new Coordinate(-78.90, 44.74),
                new Coordinate(-78.89, 44.74),
                new Coordinate(-78.89, 44.75),
                new Coordinate(-78.90, 44.75),
                new Coordinate(-78.90, 44.74)
        })));
        Map<String, Object> metrics = scorer.scoreAgainstGold(List.of(hump, extra), gold);
        assertThat(metrics.get("matched")).isEqualTo(1);
        assertThat((Double) metrics.get("recall")).isEqualTo(0.5);
        assertThat((Double) metrics.get("precision")).isEqualTo(0.5);
        assertThat((Double) metrics.get("f1")).isGreaterThan(0.4);
        assertThat(metrics.get("falsePositives")).isEqualTo(1);
        assertThat(metrics.get("missed")).isEqualTo(1);

        Map<String, Object> agreement = scorer.agreement(List.of(hump), List.of(hump, extra));
        assertThat(agreement.get("matched")).isEqualTo(1);
        assertThat(agreement.get("gisOnly")).isEqualTo(0);
        assertThat(agreement.get("visionOnly")).isEqualTo(1);
    }

    private List<GoldFeature> loadSyntheticGold() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new ClassPathResource("benchmarks/synthetic-gold.geojson").getInputStream());
        List<GoldFeature> gold = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            gold.add(new GoldFeature(
                    GeoJsonGeometryReader.featureType(feature.path("properties")),
                    GeoJsonGeometryReader.read(feature.get("geometry")),
                    feature.path("properties").path("name").asText(),
                    null
            ));
        }
        return gold;
    }

    private LakeFeature feature(FeatureType type, org.locationtech.jts.geom.Geometry geometry) {
        geometry.setSRID(GeoMapper.SRID);
        LakeFeature feature = new LakeFeature();
        feature.setType(type);
        feature.setPipeline(Pipeline.GIS);
        feature.setGeometry(geometry);
        feature.setConfidence(BigDecimal.valueOf(0.7));
        return feature;
    }
}
