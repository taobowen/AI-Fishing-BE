package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.DatasetFetchResult;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.RawPage;

import java.util.List;
import java.util.function.Function;

public class IntersectingArcGisDatasetSource implements OntarioDatasetSource {

    private final DatasetType type;
    private final String layerUrl;
    private final OntarioFeatureClient client;
    private final Function<Lake, FeatureQuery> queryFactory;

    public IntersectingArcGisDatasetSource(
            DatasetType type,
            String layerUrl,
            OntarioFeatureClient client,
            Function<Lake, FeatureQuery> queryFactory
    ) {
        this.type = type;
        this.layerUrl = layerUrl;
        this.client = client;
        this.queryFactory = queryFactory;
    }

    @Override
    public DatasetType type() {
        return type;
    }

    @Override
    public DatasetFetchResult fetch(Lake lake) {
        FeatureQuery query = queryFactory.apply(lake);
        List<RawPage> pages = client.query(query);
        return DatasetFetchResult.fetched(layerUrl, pages);
    }
}
