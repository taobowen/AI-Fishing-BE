package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FmzProcessor implements DatasetProcessor {

    @Override
    public DatasetType type() {
        return DatasetType.FMZ;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (!features.isEmpty()) {
            ParsedFeature first = features.getFirst();
            metadata.put(
                    "zone",
                    FeatureProperties.text(first.properties(), "FMZ", "ZONE", "ZONE_NAME", "OFFICIAL_NAME", "NAME")
            );
            metadata.put("sourceRecordId", first.sourceRecordId());
        }
        return new NormalizeResult(DatasetStatusCode.AVAILABLE, features.size(), List.of(), metadata, null);
    }

    @Override
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        // FMZ is stored as dataset status + raw pages; no dedicated canonical table in Phase 2.
    }
}
