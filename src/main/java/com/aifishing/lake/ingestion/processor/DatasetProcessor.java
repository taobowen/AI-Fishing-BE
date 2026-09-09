package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DatasetProcessor {

    DatasetType type();

    NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    );

    void replaceCanonical(UUID lakeId, String provider, List<?> records);

    record NormalizeResult(
            DatasetStatusCode status,
            int recordCount,
            List<?> records,
            Map<String, Object> metadata,
            String message
    ) {
        public static NormalizeResult available(List<?> records) {
            return available(records, Map.of());
        }

        public static NormalizeResult available(List<?> records, Map<String, Object> metadata) {
            return new NormalizeResult(
                    DatasetStatusCode.AVAILABLE,
                    records.size(),
                    records,
                    metadata == null ? Map.of() : metadata,
                    null
            );
        }

        public static NormalizeResult availableEmpty() {
            return new NormalizeResult(DatasetStatusCode.AVAILABLE, 0, List.of(), Map.of(), null);
        }

        public static NormalizeResult notAvailable(String message) {
            return new NormalizeResult(DatasetStatusCode.NOT_AVAILABLE, 0, List.of(), Map.of(), message);
        }

        public static NormalizeResult partial(List<?> records, String message) {
            return new NormalizeResult(DatasetStatusCode.PARTIAL, records.size(), records, Map.of(), message);
        }
    }
}
