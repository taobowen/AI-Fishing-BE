package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.ingestion.OntarioProperties;
import com.aifishing.lake.ingestion.admin.PaginationReport;
import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.processor.GeoJsonFeatureParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("!test")
public class ArcGisOntarioFeatureClient implements OntarioFeatureClient {

    private final RestClient restClient;
    private final OntarioProperties properties;
    private final GeoJsonFeatureParser parser;

    @Autowired
    public ArcGisOntarioFeatureClient(OntarioProperties properties, GeoJsonFeatureParser parser) {
        this(properties, parser, defaultRestClient());
    }

    ArcGisOntarioFeatureClient(OntarioProperties properties, GeoJsonFeatureParser parser, RestClient restClient) {
        this.properties = properties;
        this.parser = parser;
        this.restClient = restClient;
    }

    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public List<RawPage> query(FeatureQuery query) {
        int pageSize = query.pageSize() != null ? query.pageSize() : properties.getPageSize();
        List<RawPage> pages = new ArrayList<>();
        int offset = 0;
        int pageIndex = 1;
        while (true) {
            String url = buildUrl(query, pageSize, offset);
            // RestClient.uri(String) treats the value as a URI template and re-encodes
            // query commas, which ArcGIS rejects with 400 / -2147220985.
            byte[] body = restClient.get()
                    .uri(URI.create(url))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(byte[].class);
            if (body == null) {
                body = "{}".getBytes(StandardCharsets.UTF_8);
            }
            int featureCount = parser.parse(body).size();
            boolean exceeded = parser.exceededTransferLimit(body);
            if (pageIndex > 1 && featureCount == 0 && !exceeded) {
                break;
            }
            Map<String, Object> requestMetadata = new LinkedHashMap<>();
            requestMetadata.put("resultOffset", offset);
            requestMetadata.put("resultRecordCount", pageSize);
            requestMetadata.put("pageSize", pageSize);
            requestMetadata.put("featureCount", featureCount);
            requestMetadata.put("exceededTransferLimit", exceeded);
            requestMetadata.put("paginationSafetyCapHit", false);
            requestMetadata.put("outSR", 4326);
            requestMetadata.put("f", "geojson");
            requestMetadata.put("where", query.where());
            pages.add(new RawPage(pageIndex, body, url, requestMetadata, 200, MediaType.APPLICATION_JSON_VALUE));
            if (!exceeded && featureCount < pageSize) {
                break;
            }
            if (pageIndex >= PaginationReport.SAFETY_CAP_PAGES) {
                Map<String, Object> capped = new LinkedHashMap<>(requestMetadata);
                capped.put("paginationSafetyCapHit", true);
                pages.set(pages.size() - 1, new RawPage(
                        pageIndex, body, url, capped, 200, MediaType.APPLICATION_JSON_VALUE));
                break;
            }
            offset += pageSize;
            pageIndex++;
        }
        return pages;
    }

    private String buildUrl(FeatureQuery query, int pageSize, int offset) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(query.layerUrl() + "/query")
                .queryParam("f", "geojson")
                .queryParam("outFields", "*")
                .queryParam("returnGeometry", true)
                .queryParam("outSR", 4326)
                .queryParam("resultOffset", offset)
                .queryParam("resultRecordCount", pageSize)
                .queryParam("where", query.where() == null || query.where().isBlank() ? "1=1" : query.where());
        if (query.minLng() != null) {
            builder.queryParam("geometry", query.minLng() + "," + query.minLat() + "," + query.maxLng() + "," + query.maxLat())
                    .queryParam("geometryType", "esriGeometryEnvelope")
                    .queryParam("inSR", 4326)
                    .queryParam("spatialRel", "esriSpatialRelIntersects");
        }
        return builder.encode().toUriString();
    }
}
