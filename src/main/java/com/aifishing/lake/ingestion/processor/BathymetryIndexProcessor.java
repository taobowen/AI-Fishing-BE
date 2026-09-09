package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BathymetryIndexProcessor implements DatasetProcessor {

    @Override
    public DatasetType type() {
        return DatasetType.BATHYMETRY_INDEX;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        if (features.isEmpty()) {
            return NormalizeResult.notAvailable("Bathymetry index has no coverage for this lake");
        }
        return new NormalizeResult(
                com.aifishing.lake.ingestion.dto.DatasetStatusCode.AVAILABLE,
                features.size(),
                List.of(),
                Map.of("surveyCount", features.size()),
                null
        );
    }

    @Override
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        // Index is coverage metadata only; no canonical rows.
    }
}
