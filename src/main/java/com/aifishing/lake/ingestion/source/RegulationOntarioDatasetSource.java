package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.OntarioProperties;
import com.aifishing.lake.ingestion.dto.DatasetFetchResult;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.RawPage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RegulationOntarioDatasetSource implements OntarioDatasetSource {

    private final OntarioProperties properties;
    private final SimpleHttpGetter httpGetter;

    public RegulationOntarioDatasetSource(OntarioProperties properties, SimpleHttpGetter httpGetter) {
        this.properties = properties;
        this.httpGetter = httpGetter;
    }

    @Override
    public DatasetType type() {
        return DatasetType.REGULATION;
    }

    @Override
    public DatasetFetchResult fetch(Lake lake) {
        String url = properties.getLio().getRegulationsUrl();
        SimpleHttpGetter.HttpGetResponse response = httpGetter.get(url);
        if (response.status() == 404) {
            return DatasetFetchResult.notAvailable(url, "Regulations catalogue returned 404");
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("Regulations catalogue HTTP " + response.status());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("retrievedAt", Instant.now().toString());
        metadata.put("note", "Tabular/catalogue download only; no spatial sanctuary geometry inferred");
        String contentType = response.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : response.contentType();
        RawPage page = new RawPage(1, response.body(), url, metadata, response.status(), contentType);
        return DatasetFetchResult.fetched(url, List.of(page));
    }
}
