package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetFetchResult;
import com.aifishing.lake.ingestion.dto.DatasetType;

public class AlwaysUnavailableDatasetSource implements OntarioDatasetSource {

    private final DatasetType type;
    private final String reason;

    public AlwaysUnavailableDatasetSource(DatasetType type, String reason) {
        this.type = type;
        this.reason = reason;
    }

    @Override
    public DatasetType type() {
        return type;
    }

    @Override
    public DatasetFetchResult fetch(Lake lake) {
        return DatasetFetchResult.notAvailable(null, reason);
    }
}
