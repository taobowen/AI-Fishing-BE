package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.ingestion.OntarioProperties;
import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.processor.GeoJsonFeatureParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArcGisOntarioFeatureClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void paginatesWithOutSrAndOffset() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/layer/query", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            assertThat(query).contains("outSR=4326");
            assertThat(query).contains("f=geojson");
            assertThat(query).contains("geometry=-79.0,44.5,-78.5,45.0");
            assertThat(query).doesNotContain("%252C");
            String body;
            if (query.contains("resultOffset=0")) {
                body = """
                        {"type":"FeatureCollection","exceededTransferLimit":true,"features":[
                          {"type":"Feature","id":1,"properties":{"OGF_ID":1},"geometry":{"type":"Point","coordinates":[-78.92,44.75]}}
                        ]}
                        """;
            } else if (query.contains("resultOffset=1")) {
                body = """
                        {"type":"FeatureCollection","features":[
                          {"type":"Feature","id":2,"properties":{"OGF_ID":2},"geometry":{"type":"Point","coordinates":[-78.91,44.76]}}
                        ]}
                        """;
            } else {
                body = """
                        {"type":"FeatureCollection","features":[]}
                        """;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        OntarioProperties properties = new OntarioProperties();
        properties.setPageSize(1);
        ArcGisOntarioFeatureClient client = new ArcGisOntarioFeatureClient(
                properties,
                new GeoJsonFeatureParser(new ObjectMapper()),
                RestClient.builder().build()
        );

        String layerUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/layer";
        List<RawPage> pages = client.query(new FeatureQuery(layerUrl, "1=1", -79.0, 44.5, -78.5, 45.0, 1));

        assertThat(pages).hasSize(2);
        assertThat(pages.getFirst().requestMetadata()).containsEntry("outSR", 4326);
        assertThat(pages.getFirst().requestMetadata()).containsEntry("featureCount", 1);
        assertThat(pages.getFirst().requestMetadata()).containsEntry("exceededTransferLimit", true);
        assertThat(pages.get(1).requestMetadata()).containsEntry("resultOffset", 1);
        assertThat(pages.get(1).requestMetadata()).containsEntry("exceededTransferLimit", false);
    }
}
