package com.aifishing.lake.ingestion.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoJsonFeatureParserTest {

    private final GeoJsonFeatureParser parser = new GeoJsonFeatureParser(new ObjectMapper());

    @Test
    void parsesPaginatedCollectionAndFlagsTransferLimit() {
        byte[] page1 = """
                {"type":"FeatureCollection","exceededTransferLimit":true,"features":[
                  {"type":"Feature","id":1,"properties":{"OGF_ID":1},"geometry":{"type":"Point","coordinates":[-78.92,44.75]}}
                ]}
                """.getBytes(StandardCharsets.UTF_8);
        byte[] page2 = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":2,"properties":{"OBJECTID":2},"geometry":{"type":"Point","coordinates":[-78.91,44.76]}}
                ]}
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(parser.exceededTransferLimit(page1)).isTrue();
        assertThat(parser.parse(page1)).hasSize(1);
        assertThat(parser.exceededTransferLimit(page2)).isFalse();
        assertThat(parser.parse(page2)).hasSize(1);
        assertThat(parser.parse(page2).getFirst().sourceRecordId()).isEqualTo("2");
    }

    @Test
    void rejectsInvalidGeometryInsteadOfSkipping() {
        byte[] invalid = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"OGF_ID":9},"geometry":{"type":"Polygon","coordinates":[[[0,0],[1,1]]]}}
                ]}
                """.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
