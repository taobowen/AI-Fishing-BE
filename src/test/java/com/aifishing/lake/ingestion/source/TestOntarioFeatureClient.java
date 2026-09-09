package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.RawPage;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@Profile("test")
public class TestOntarioFeatureClient implements OntarioFeatureClient {

    private static final byte[] EMPTY_COLLECTION = """
            {"type":"FeatureCollection","features":[]}
            """.getBytes(StandardCharsets.UTF_8);

    @Override
    public List<RawPage> query(FeatureQuery query) {
        return List.of(new RawPage(
                1,
                EMPTY_COLLECTION,
                query.layerUrl(),
                Map.of("where", query.where() == null ? "1=1" : query.where()),
                200,
                MediaType.APPLICATION_JSON_VALUE
        ));
    }
}
