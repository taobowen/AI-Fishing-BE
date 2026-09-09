package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.RawPage;

import java.util.List;

public interface OntarioFeatureClient {

    List<RawPage> query(FeatureQuery query);
}
