package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.CanonicalOntarioRecord;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProvenanceBinder {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ProvenanceBinder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void bind(CanonicalOntarioRecord record, Lake lake, String provider, String importVersion, ParsedFeature feature) {
        record.setLakeId(lake.getId());
        record.setProvider(provider);
        record.setImportVersion(importVersion);
        record.setSource(provider);
        if (feature != null) {
            record.setSourceRecordId(feature.sourceRecordId());
            if (feature.properties() != null && !feature.properties().isMissingNode()) {
                record.setSourceMetadata(objectMapper.convertValue(feature.properties(), MAP));
            }
        }
    }
}
