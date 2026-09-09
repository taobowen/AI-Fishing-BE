package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetFetchResult;
import com.aifishing.lake.ingestion.dto.DatasetType;

public interface OntarioDatasetSource {

    DatasetType type();

    DatasetFetchResult fetch(Lake lake);
}
